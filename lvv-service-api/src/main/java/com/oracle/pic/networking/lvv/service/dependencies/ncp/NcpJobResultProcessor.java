package com.oracle.pic.networking.lvv.service.dependencies.ncp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.pic.commons.exceptions.server.ErrorCode;
import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import com.oracle.pic.networking.lvv.service.kiev.LinkStatus;
import com.oracle.pic.networking.lvv.service.kiev.LldpStatus;
import com.oracle.pic.networking.lvv.service.kiev.ValidationFailureResult;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Getter
@Setter
@ToString
@Slf4j
public class NcpJobResultProcessor {

    InputStream inputStream;
    Map<DevicePortInfo, ValidationFailureResult.Builder> resultBuilder;
    ObjectMapper mapper;

    private static final String TEST_LLDP = "test_lldp";
    private static final String TEST_POWER = "test_power";
    private static final String TEST_OPTICS = "test_optics";

    private static final String PASSED = "PASSED";
    private static final String FAILED = "FAILED";

    private static final String PSU_FAILURE_MESSAGE = "Power supply failure detected";

    @ToString
    @Getter
    @Setter
    @EqualsAndHashCode
    public static class DevicePortInfo {
        public final String deviceName;
        public final String port;
        public final String rackUnit;

        public DevicePortInfo(String deviceName, String port, String rackUnit) {
            this.deviceName = deviceName;
            this.port = port;
            this.rackUnit = rackUnit;
        }
    }

    public NcpJobResultProcessor(InputStream inputStream) {
        this.inputStream = inputStream;
        this.mapper = new ObjectMapper();
        this.resultBuilder = new HashMap<>();
    }

    public DevicePortInfo parseDeviceString(String input) {
        if (input == null || input.isEmpty()) {
            return new DevicePortInfo("Unknown", "Unknown", "Unknown");
        }

        String[] parts = input.split(":");
        if (parts.length < 5) {
            return new DevicePortInfo("Unknown", "Unknown", "Unknown");
        }

        String deviceName = parts[0];
        String port = parts[1];
        String rackUnit = parts[parts.length - 2] + ":" + parts[parts.length - 1];

        return new DevicePortInfo(deviceName, port, rackUnit);
    }

    public void extractLldpErrors(String message, MetricsScope scope) {

        // Expected format: "Failed: {json}"
        log.info("[LLDP] Processing LLDP Error message: \n {}", message);

        int idx = message.indexOf("Failed:");
        if (idx == 0) {
            String jsonPart = message.substring("Failed:".length()).trim();
            JsonNode failedObj;
            try {
                failedObj = mapper.readTree(jsonPart);
            } catch (IOException e) {
                log.error("[LLDP] LLDP Error in unexpected format {}", message, e);
                scope.emit(MetricNames.ProcessNcpResult.LldpErrorFormatUnexpected, 1.0);
                throw new RenderableException(
                        ErrorCode.IncorrectState,
                        String.format("LLDP Error in unexpected format: %s", message));
            }

            // Message must be "LLDP Failures"
            if (failedObj
                    .path("message")
                    .asText()
                    .startsWith("LLDP Failures")) { // handle error count >1
                JsonNode errorsArray = failedObj.path("errors_object");
                if (errorsArray.isArray()) {
                    for (JsonNode errorObj : errorsArray) {
                        log.info("[LLDP] Parsing errorObject: {}", errorObj.toString());

                        ValidationFailureResult.Builder builder = ValidationFailureResult.builder();
                        String currentOrigin = errorObj.path("current_origin").asText();
                        String currentDestination = errorObj.path("current_destination").asText();
                        String expectedDestination = errorObj.path("expected_destination").asText();
                        DevicePortInfo originDevicePortInfo = parseDeviceString(currentOrigin);
                        DevicePortInfo destinationDevicePortInfo =
                                parseDeviceString(currentDestination);
                        DevicePortInfo expectedDevicePortInfo =
                                parseDeviceString(expectedDestination);
                        ValidationFailureResult.LinkSource linkSource =
                                ValidationFailureResult.LinkSource.builder()
                                        .deviceAName(originDevicePortInfo.deviceName)
                                        .deviceAPort(originDevicePortInfo.port)
                                        .build();
                        builder.linkSource(linkSource);
                        builder.deviceARack(originDevicePortInfo.rackUnit);
                        builder.deviceBName(destinationDevicePortInfo.deviceName);
                        builder.deviceBPort(destinationDevicePortInfo.port);
                        builder.deviceBRack(destinationDevicePortInfo.rackUnit);
                        builder.deviceBNameExpected(expectedDevicePortInfo.deviceName);
                        builder.deviceBPortExpected(expectedDevicePortInfo.port);
                        builder.deviceBRackExpected(expectedDevicePortInfo.rackUnit);
                        builder.lldpStatus(LldpStatus.MISMATCH);
                        builder.linkStatus(LinkStatus.DOWN);
                        resultBuilder.put(originDevicePortInfo, builder);

                        log.info(
                                "[LLDP] Parsed output for LLDP Error: originDevicePortInfo: {}, destinationDevicePortInfo: {}, expectedDevicePortInfo: {}",
                                originDevicePortInfo,
                                destinationDevicePortInfo,
                                expectedDevicePortInfo);

                        log.debug("[LLDP] Created builder for {}", originDevicePortInfo);
                    }
                }
            }
        }
    }

    public void extractOpticErrors(String message, boolean lldpTestPassed, MetricsScope scope) {

        log.debug("[OPTICS] Processing Optics Error message: \n {}", message);

        JsonNode root;
        try {
            String prefix = "Failed: ";
            String jsonPart =
                    message.startsWith(prefix)
                            ? message.substring(prefix.length()).trim()
                            : message;
            root = mapper.readTree(jsonPart);
        } catch (IOException e) {
            log.error("[OPTICS] Optic Error in unexpected format {}", message, e);
            scope.emit(MetricNames.ProcessNcpResult.OpticErrorFormatUnexpected, 1.0);
            throw new RenderableException(
                    ErrorCode.IncorrectState,
                    String.format("Optic Error in unexpected format: %s", message));
        }

        JsonNode errorsArray = root.get("errors_object");
        if (errorsArray != null && errorsArray.isArray()) {
            for (JsonNode node : errorsArray) {
                log.debug("[OPTICS] Parsing errorObject: {}", node.toString());

                String deviceName = node.get("device").asText();
                String port = node.get("intf_name").asText();
                String txPower = node.get("output_power").asText();
                String rxPower = node.get("input_power").asText();

                // Extract rackUnit after the first colon
                String devicePhys = node.get("device_phys").asText();
                int colonIdx = devicePhys.indexOf(':');
                String rackUnit =
                        (colonIdx != -1 && colonIdx < devicePhys.length() - 1)
                                ? devicePhys.substring(colonIdx + 1)
                                : devicePhys;

                DevicePortInfo devicePortInfo = new DevicePortInfo(deviceName, port, rackUnit);

                log.debug(
                        "[OPTICS] Parsed output for Optic error: \n deviceName: {}, port: {}, txPower: {}, rxPower: {}, rackUnit: {}",
                        deviceName,
                        port,
                        txPower,
                        rxPower,
                        rackUnit);

                if (resultBuilder.containsKey(devicePortInfo)) {
                    log.debug("[OPTICS] Appended builder for {}", devicePortInfo);
                    ValidationFailureResult.Builder builder = resultBuilder.get(devicePortInfo);
                    builder.txPower(txPower);
                    builder.rxPower(rxPower);
                    resultBuilder.put(devicePortInfo, builder);
                } else {
                    log.debug("[OPTICS] Created builder for {}", devicePortInfo);
                    ValidationFailureResult.Builder builder = ValidationFailureResult.builder();
                    ValidationFailureResult.LinkSource linkSource =
                            ValidationFailureResult.LinkSource.builder()
                                    .deviceAName(deviceName)
                                    .deviceAPort(port)
                                    .build();

                    builder.deviceARack(rackUnit);
                    builder.linkSource(linkSource);
                    builder.rxPower(rxPower);
                    builder.txPower(txPower);
                    builder.linkStatus(LinkStatus.DOWN);

                    if (lldpTestPassed) {
                        builder.lldpStatus(LldpStatus.MATCH);
                    } else {
                        builder.lldpStatus(LldpStatus.UNTESTED);
                    }

                    resultBuilder.put(devicePortInfo, builder);
                }
            }
        }
    }

    public void updatePowerErrors(String deviceName, boolean lldpTestPassed) {

        log.debug("[POWER] Adding failure for device {}", deviceName);
        boolean onlyPsuFailure = true;

        for (Map.Entry<DevicePortInfo, ValidationFailureResult.Builder> entry :
                resultBuilder.entrySet()) {
            if (entry.getKey().deviceName.equals(deviceName)) {
                log.debug("[POWER] Found an entry for device {}, appending to it", entry.getKey());
                onlyPsuFailure = false;
                ValidationFailureResult.Builder builder = entry.getValue();
                builder.psuFailure(PSU_FAILURE_MESSAGE);
                resultBuilder.put(entry.getKey(), builder);
            }
        }

        if (onlyPsuFailure) {

            log.debug("[POWER] No entry found for device {}, creating a new entry", deviceName);

            DevicePortInfo info = new DevicePortInfo(deviceName, "Unknown", "Unknown");

            ValidationFailureResult.Builder builder = ValidationFailureResult.builder();
            builder.psuFailure(PSU_FAILURE_MESSAGE);
            ValidationFailureResult.LinkSource linkSource =
                    ValidationFailureResult.LinkSource.builder()
                            .deviceAName(deviceName)
                            .deviceAPort("Unknown")
                            .build();
            builder.linkSource(linkSource);
            builder.linkStatus(LinkStatus.DOWN);

            if (lldpTestPassed) {
                builder.lldpStatus(LldpStatus.MATCH);
            } else {
                builder.lldpStatus(LldpStatus.UNTESTED);
            }

            resultBuilder.put(info, builder);
        }
    }

    public void processJobResult(MetricsScope scope) {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root;

        try {
            root = mapper.readTree(inputStream);
        } catch (IOException e) {
            log.error("Failed to parse job result as JSON: {}", e.getMessage());
            scope.emit(MetricNames.ProcessNcpResult.JsonParseFail, 1.0);
            throw new RenderableException(
                    ErrorCode.InternalError, "Failed to parse NCP job result as JSON");
        }

        JsonNode testResults = root.path("testResults");
        if (testResults.isMissingNode()) {
            log.info("No 'testResults' found in the payload.");
            scope.emit(MetricNames.ProcessNcpResult.NoTestResultFound, 1.0);
            throw new RenderableException(
                    ErrorCode.ExternalServerInvalidResponse,
                    "No 'testResults' found in the payload.");
        }

        Iterator<Map.Entry<String, JsonNode>> deviceIterator = testResults.fields();
        log.info("\nProcessing FAILED {} {} {} Test Cases:", TEST_LLDP, TEST_OPTICS, TEST_POWER);

        while (deviceIterator.hasNext()) {
            Map.Entry<String, JsonNode> deviceEntry = deviceIterator.next();
            String deviceId = deviceEntry.getKey();
            JsonNode healthCheckReport = deviceEntry.getValue().path("healthCheckReport");
            JsonNode testCases = healthCheckReport.path("testCases");

            log.info("Processing results for device {}", deviceId);

            if (testCases.isArray()) {
                String lldpError = null;
                String opticsError = null;
                String powerError = null;
                boolean deviceHasFailures = false;

                for (JsonNode testCaseNode : testCases) {
                    String testCaseName = testCaseNode.path("testCase").asText();
                    String status = testCaseNode.path("status").asText();

                    if (TEST_LLDP.equals(testCaseName) && FAILED.equals(status)) {
                        lldpError = testCaseNode.path("message").asText();
                    } else if (TEST_LLDP.equals(testCaseName) && PASSED.equals(status)) {
                        lldpError = PASSED;
                    }

                    if (TEST_OPTICS.equals(testCaseName) && FAILED.equals(status)) {
                        opticsError = testCaseNode.path("message").asText();
                    } else if (TEST_OPTICS.equals(testCaseName) && PASSED.equals(status)) {
                        opticsError = PASSED;
                    }

                    if (TEST_POWER.equals(testCaseName) && FAILED.equals(status)) {
                        powerError = FAILED;
                    } else if (TEST_POWER.equals(testCaseName) && PASSED.equals(status)) {
                        powerError = PASSED;
                    }
                }

                if (lldpError != null && !lldpError.equals(PASSED)) {
                    deviceHasFailures = true;
                    scope.emit(MetricNames.ProcessNcpResult.LldpError, 1.0);
                    extractLldpErrors(lldpError, scope);
                }

                if (opticsError != null && !opticsError.equals(PASSED)) {
                    deviceHasFailures = true;
                    scope.emit(MetricNames.ProcessNcpResult.OpticError, 1.0);
                    extractOpticErrors(opticsError, Objects.equals(lldpError, PASSED), scope);
                }

                if (Objects.equals(powerError, FAILED)) {
                    deviceHasFailures = true;
                    scope.emit(MetricNames.ProcessNcpResult.PsuError, 1.0);
                    updatePowerErrors(deviceId, Objects.equals(lldpError, PASSED));
                }

                if (!deviceHasFailures) {

                    log.info("Device has no failures");
                    scope.emit(MetricNames.ProcessNcpResult.Pass, 1.0);

                    // If a device has no failures, we create a minimal ValidationFailureResult
                    // Object with the device name and link status as UP, to remove these failures
                    // from the DB
                    DevicePortInfo info = new DevicePortInfo(deviceId, "Unknown", "Unknown");

                    ValidationFailureResult.LinkSource linkSource =
                            ValidationFailureResult.LinkSource.builder()
                                    .deviceAName(deviceId)
                                    .build();
                    ValidationFailureResult.Builder builder =
                            ValidationFailureResult.builder()
                                    .linkSource(linkSource)
                                    .linkStatus(LinkStatus.UP);

                    resultBuilder.put(info, builder);
                }
            }
        }
    }

    public List<ValidationFailureResult> buildValidationFailureResults(
            String rackSerialNumber, String rackUnit) {

        List<ValidationFailureResult> validationFailureResults = new ArrayList<>();

        for (ValidationFailureResult.Builder builder : resultBuilder.values()) {
            ValidationFailureResult validationFailureResult =
                    builder.rackSerial(rackSerialNumber).build();

            // For devices with only PSU failures, we need to set the rack unit explicitly, as it is
            // not provided in the validation result
            if (validationFailureResult.getDeviceARack() == null) {
                log.info("Setting rackUnit to '{}'", rackUnit);
                validationFailureResult.setDeviceARack(rackUnit);
            }

            log.info("Adding validation failure result: {}", validationFailureResult);
            validationFailureResults.add(validationFailureResult);
        }

        return validationFailureResults;
    }
}
