package com.oracle.pic.networking.lvv.service.dependencies.ncp.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Extractor for LLDP failures. Mirrors the logic in NcpJobResultProcessor.extractLldpErrors,
 * without modifying that class.
 */
@Slf4j
public class LldpTestResultExtractor implements TestResultExtractor {

    private static final String TEST_LLDP = "test_lldp";
    private static final String CRYPTO = "crypto";
    private static final String UNKNOWN = "Unknown";
    private static final String LLDP = "LLDP Errors";

    private final ObjectMapper mapper;

    // LLDP Result Column Names
    private static final String DEVICE_A_RACK = "Device A Rack";
    private static final String DEVICE_A_NAME = "Device A Name";
    private static final String DEVICE_A_PORT = "Device A Port";
    private static final String DEVICE_B_RACK = "Device B Rack";
    private static final String DEVICE_B_NAME = "Device B Name";
    private static final String DEVICE_B_PORT = "Device B Port";
    private static final String EXPECTED_DEVICE_B_RACK = "Expected Device B Rack";
    private static final String EXPECTED_DEVICE_B_NAME = "Expected Device B Name";
    private static final String EXPECTED_DEVICE_B_PORT = "Expected Device B Port";
    private static final String LLDP_STATUS = "LLDP Status";

    public LldpTestResultExtractor() {
        this.mapper = new ObjectMapper();
    }

    public LldpTestResultExtractor(ObjectMapper mapper) {
        this.mapper = mapper != null ? mapper : new ObjectMapper();
    }

    @Override
    public String testName() {
        return TEST_LLDP;
    }

    @Override
    public void extract(
            String deviceId,
            String message,
            MetricsScope scope,
            Map<String, Map<String, List<Map<String, String>>>> deviceResults) {
        Map<String, List<Map<String, String>>> perDeviceMap =
                deviceResults.computeIfAbsent(deviceId, k -> new HashMap<>());

        List<Map<String, String>> lldpResults =
                perDeviceMap.computeIfAbsent(LLDP, k -> new ArrayList<>());

        if (message == null) {
            return;
        }

        log.info("[LLDP] Processing LLDP Error message for device {}: \n {}", deviceId, message);
        int idx = message.indexOf("Failed:");
        if (idx != 0) {
            return;
        }

        String jsonPart = message.substring("Failed:".length()).trim();
        if (jsonPart.isEmpty()) {
            log.warn("[LLDP] LLDP Error in unexpected format {}", message);
            scope.emit(MetricNames.ProcessNcpResult.LldpErrorFormatUnexpected, 1.0);

            Map<String, String> result = new HashMap<>();
            result.put(DEVICE_A_NAME, deviceId);
            result.put(LLDP_STATUS, UNKNOWN);
            lldpResults.add(result);
            return;
        }

        JsonNode failedObj;
        try {
            failedObj = mapper.readTree(jsonPart);
        } catch (IOException e) {
            log.warn("[LLDP] LLDP Error in unexpected format {}", message, e);
            scope.emit(MetricNames.ProcessNcpResult.LldpErrorFormatUnexpected, 1.0);

            Map<String, String> result = new HashMap<>();
            result.put(DEVICE_A_NAME, deviceId);
            result.put(LLDP_STATUS, UNKNOWN);
            lldpResults.add(result);
            return;
        }

        if (!failedObj.path("message").asText().startsWith("LLDP Failures")) {
            return;
        }

        JsonNode errorsArray = failedObj.path("errors_object");
        if (errorsArray.isArray()) {
            for (JsonNode errorObj : errorsArray) {
                log.info("[LLDP] Parsing errorObject: {}", errorObj.toString());
                Map<String, String> result = new HashMap<>();

                String currentOrigin = errorObj.path("current_origin").asText();
                String currentDestination = errorObj.path("current_destination").asText();
                String expectedDestination = errorObj.path("expected_destination").asText();

                parseDeviceString(
                        currentOrigin, result, DEVICE_A_RACK, DEVICE_A_NAME, DEVICE_A_PORT);
                parseDeviceString(
                        currentDestination, result, DEVICE_B_RACK, DEVICE_B_NAME, DEVICE_B_PORT);
                parseDeviceString(
                        expectedDestination,
                        result,
                        EXPECTED_DEVICE_B_RACK,
                        EXPECTED_DEVICE_B_NAME,
                        EXPECTED_DEVICE_B_PORT);

                if (containsCrypto(result.get(DEVICE_A_NAME))
                        || containsCrypto(result.get(DEVICE_B_NAME))) {
                    result.put(LLDP_STATUS, LldpStatus.UNSUPPORTED.name());
                } else if (UNKNOWN.equals(result.get(DEVICE_B_NAME))) {
                    result.put(LLDP_STATUS, LldpStatus.INTERFACE_DOWN.name());
                } else {
                    result.put(LLDP_STATUS, LldpStatus.MISMATCH.name());
                }

                lldpResults.add(result);
            }
        }
    }

    private boolean containsCrypto(String deviceName) {
        return deviceName != null && deviceName.contains(CRYPTO);
    }

    // Mirrors NcpJobResultProcessor.parseDeviceString but kept local to avoid modifying that class.
    private void parseDeviceString(
            String input,
            Map<String, String> lldpResults,
            String deviceRack,
            String deviceName,
            String devicePort) {
        if (input == null || input.isEmpty()) {
            lldpResults.put(deviceRack, UNKNOWN);
            lldpResults.put(deviceName, UNKNOWN);
            lldpResults.put(devicePort, UNKNOWN);
            return;
        }

        String[] parts = input.split(":");
        if (parts.length < 5) {
            lldpResults.put(deviceRack, UNKNOWN);
            lldpResults.put(deviceName, UNKNOWN);
            lldpResults.put(devicePort, UNKNOWN);
            return;
        }

        String name = parts[0];
        String port = parts[1];
        String rackUnit = parts[parts.length - 2] + ":" + parts[parts.length - 1];

        lldpResults.put(deviceRack, rackUnit);
        lldpResults.put(deviceName, name);
        lldpResults.put(devicePort, port);
    }
}
