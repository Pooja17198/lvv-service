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

/** Extractor for FAN failures from test_fans output. */
@Slf4j
public class FanTestResultExtractor implements TestResultExtractor {

    private static final String TEST_FANS = "test_fans";
    private static final String FAN = "Fan Errors";
    private static final String UNKNOWN = "Unknown";

    // Fan Result Column Names
    private static final String DEVICE_NAME = "Device Name";
    private static final String FAN_NAME = "Fan Name";
    private static final String FAN_SLOT = "Fan Slot";
    private static final String STATUS = "Status";
    private static final String ERROR_MESSAGE = "Error Message";

    private final ObjectMapper mapper;

    public FanTestResultExtractor() {
        this.mapper = new ObjectMapper();
    }

    public FanTestResultExtractor(ObjectMapper mapper) {
        this.mapper = mapper != null ? mapper : new ObjectMapper();
    }

    @Override
    public String testName() {
        return TEST_FANS;
    }

    @Override
    public void extract(
            String deviceId,
            String message,
            MetricsScope scope,
            Map<String, Map<String, List<Map<String, String>>>> deviceResults) {
        Map<String, List<Map<String, String>>> perDeviceMap =
                deviceResults.computeIfAbsent(deviceId, k -> new HashMap<>());

        List<Map<String, String>> fanResults =
                perDeviceMap.computeIfAbsent(FAN, k -> new ArrayList<>());

        if (message == null || message.isBlank()) {
            return;
        }

        // Log the raw NCPCLI fan failure payload before attempting any parsing.
        log.info("[FANS] Raw fan error payload from NCPCLI for device {}: {}", deviceId, message);
        log.debug("[FANS] Processing Fan Error message for device {}: \n {}", deviceId, message);

        JsonNode root;
        try {
            String prefix = "Failed:";
            String jsonPart = message.trim();
            if (jsonPart.startsWith(prefix)) {
                jsonPart = jsonPart.substring(prefix.length()).trim();
            }

            if (jsonPart.isEmpty()) {
                return;
            }

            root = parseJsonPart(jsonPart);
        } catch (IOException e) {
            log.warn("[FANS] Fan Error in unexpected format {}", message, e);
            scope.emit(MetricNames.ProcessNcpResult.FanErrorFormatUnexpected, 1.0);
            fanResults.add(createUnknownFanResult(deviceId, message));
            return;
        }

        JsonNode errorsArray = root.get("errors_object");
        if (errorsArray != null && errorsArray.isArray()) {
            for (JsonNode node : errorsArray) {
                Map<String, String> result = new HashMap<>();
                result.put(DEVICE_NAME, deviceId);
                result.put(FAN_NAME, node.path("fan_name").asText(UNKNOWN));
                result.put(FAN_SLOT, node.path("fan_slot").asText(UNKNOWN));

                JsonNode statusNode = node.get("status");
                String statusValue =
                        statusNode == null || statusNode.isNull() ? UNKNOWN : statusNode.asText();
                result.put(STATUS, statusValue);

                fanResults.add(result);
            }
        }
    }

    /**
     * Fan failures are expected to be JSON, but some upstream failures return plain-text messages
     * with a trailing JSON blob. Attempt to parse from the first JSON opening token if needed.
     */
    private JsonNode parseJsonPart(String jsonPart) throws IOException {
        String candidate = jsonPart.trim();
        if (!candidate.startsWith("{") && !candidate.startsWith("[")) {
            int objectIdx = candidate.indexOf('{');
            int arrayIdx = candidate.indexOf('[');
            int startIdx;
            if (objectIdx < 0) {
                startIdx = arrayIdx;
            } else if (arrayIdx < 0) {
                startIdx = objectIdx;
            } else {
                startIdx = Math.min(objectIdx, arrayIdx);
            }

            if (startIdx < 0) {
                throw new IOException("Fan message does not contain JSON content");
            }
            candidate = candidate.substring(startIdx).trim();
        }
        return mapper.readTree(candidate);
    }

    private Map<String, String> createUnknownFanResult(String deviceId, String rawMessage) {
        Map<String, String> result = new HashMap<>();
        result.put(DEVICE_NAME, deviceId);
        result.put(FAN_NAME, UNKNOWN);
        result.put(FAN_SLOT, UNKNOWN);
        result.put(STATUS, UNKNOWN);
        result.put(ERROR_MESSAGE, rawMessage == null ? UNKNOWN : rawMessage);
        return result;
    }
}
