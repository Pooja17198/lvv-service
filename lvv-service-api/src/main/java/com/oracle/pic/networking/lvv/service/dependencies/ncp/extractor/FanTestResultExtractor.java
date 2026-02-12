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

            root = mapper.readTree(jsonPart);
        } catch (IOException e) {
            log.error("[FANS] Fan Error in unexpected format {}", message, e);
            scope.emit(MetricNames.ProcessNcpResult.FanErrorFormatUnexpected, 1.0);
            throw new RenderableException(
                    ErrorCode.IncorrectState,
                    String.format("Fan Error in unexpected format: %s", message));
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
}
