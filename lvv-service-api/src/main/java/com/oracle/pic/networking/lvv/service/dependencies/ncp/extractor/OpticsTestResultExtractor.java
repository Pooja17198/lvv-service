package com.oracle.pic.networking.lvv.service.dependencies.ncp.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.pic.commons.exceptions.server.ErrorCode;
import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Extractor for OPTICS failures. Mirrors the logic in NcpJobResultProcessor.extractOpticErrors,
 * without modifying that class.
 */
@Slf4j
public class OpticsTestResultExtractor implements TestResultExtractor {

    private static final String TEST_OPTICS = "test_optics";
    private static final String OPTICS = "Optic Errors";

    private final ObjectMapper mapper;

    // Optic Result Column Names
    private static final String DEVICE_NAME = "Device Name";
    private static final String DEVICE_PORT = "Device Port";
    private static final String TX_POWER = "Tx Power";
    private static final String RX_POWER = "Rx Power";

    public OpticsTestResultExtractor() {
        this.mapper = new ObjectMapper();
    }

    public OpticsTestResultExtractor(ObjectMapper mapper) {
        this.mapper = mapper != null ? mapper : new ObjectMapper();
    }

    @Override
    public String testName() {
        return TEST_OPTICS;
    }

    @Override
    public void extract(
            String deviceId,
            String message,
            MetricsScope scope,
            Map<String, Map<String, List<Map<String, String>>>> deviceResults) {
        Map<String, List<Map<String, String>>> perDeviceMap =
                deviceResults.computeIfAbsent(deviceId, k -> new HashMap<>());

        List<Map<String, String>> opticsResults =
                perDeviceMap.computeIfAbsent(OPTICS, k -> new ArrayList<>());

        if (message == null) {
            return;
        }

        log.debug(
                "[OPTICS] Processing Optics Error message for device {}: \n {}", deviceId, message);

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
            Map<String, Map<String, String>> dedupedOpticsByInterface = new LinkedHashMap<>();
            for (JsonNode node : errorsArray) {
                log.debug("[OPTICS] Parsing errorObject: {}", node.toString());
                Map<String, String> result = new HashMap<>();

                String opticDeviceName = node.get("device").asText();
                String opticInterface = node.get("intf_name").asText();
                result.put(DEVICE_NAME, opticDeviceName);
                result.put(DEVICE_PORT, opticInterface);
                result.put(TX_POWER, node.get("output_power").asText());
                result.put(RX_POWER, node.get("input_power").asText());

                String dedupeKey = opticDeviceName + "|" + opticInterface;
                dedupedOpticsByInterface.put(dedupeKey, result);
            }
            opticsResults.addAll(dedupedOpticsByInterface.values());
        }
    }
}
