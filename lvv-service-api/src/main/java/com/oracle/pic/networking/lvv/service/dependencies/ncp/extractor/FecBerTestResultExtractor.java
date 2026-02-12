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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/** Extractor for FEC_BER failures. */
@Slf4j
public class FecBerTestResultExtractor implements TestResultExtractor {

    private static final String TEST_FEC_BER = "test_fec_ber_threshold";
    private static final String FEC_BER = "FEC_BER Errors";

    private final ObjectMapper mapper;

    // FEC_BER Result Column Names
    private static final String DEVICE_RACK = "Device Rack";
    private static final String DEVICE_NAME = "Device Name";
    private static final String DEVICE_PORT = "Device Port";
    private static final String PRE_FEC_BER = "PRE_FEC_BER";
    private static final String LOCK_STATUS = "Lock Status";
    private static final String REMOTE_DEVICE = "Remote Device";
    private static final String REMOTE_INTERFACE = "Remote Interface";

    private static final String UNKNOWN = "Unknown";
    private static final Pattern FEC_BER_BLOCK =
            Pattern.compile("\\{\\s*'([^']+?)'\\s*:\\s*\\{(.*?)\\}\\s*\\}", Pattern.DOTALL);

    public FecBerTestResultExtractor() {
        this.mapper = new ObjectMapper();
    }

    public FecBerTestResultExtractor(ObjectMapper mapper) {
        this.mapper = mapper != null ? mapper : new ObjectMapper();
    }

    @Override
    public String testName() {
        return TEST_FEC_BER;
    }

    @Override
    public void extract(
            String deviceId,
            String message,
            MetricsScope scope,
            Map<String, Map<String, List<Map<String, String>>>> deviceResults) {
        Map<String, List<Map<String, String>>> perDeviceMap =
                deviceResults.computeIfAbsent(deviceId, k -> new HashMap<>());

        List<Map<String, String>> fecBerResults =
                perDeviceMap.computeIfAbsent(FEC_BER, k -> new ArrayList<>());

        if (message == null || message.isBlank()) {
            return;
        }

        log.info(
                "[FEC_BER] Processing FEC_BER Error message for device {}: \n {}",
                deviceId,
                message);

        boolean anyRowAdded = false;

        try {
            Matcher errorMessage = FEC_BER_BLOCK.matcher(message);
            while (errorMessage.find()) {
                String portName = errorMessage.group(1).trim();
                String innerMap = errorMessage.group(2).trim();

                String jsonLike = toJsonLike(innerMap);
                JsonNode node = mapper.readTree(jsonLike);
                Map<String, String> row = new HashMap<>();
                row.put(DEVICE_RACK, node.path("rack").asText(UNKNOWN));
                row.put(DEVICE_NAME, node.path("device_name").asText(deviceId));
                row.put(DEVICE_PORT, portName);
                row.put(PRE_FEC_BER, node.path("pre_fec_ber").asText(UNKNOWN));
                JsonNode lockNode = node.path("lock_status");
                String lockVal =
                        lockNode.isMissingNode() ? UNKNOWN : String.valueOf(lockNode.asBoolean());
                row.put(LOCK_STATUS, lockVal);
                row.put(REMOTE_DEVICE, node.path("remote_device").asText(UNKNOWN));
                row.put(REMOTE_INTERFACE, node.path("remote_interface").asText(UNKNOWN));
                fecBerResults.add(row);
                anyRowAdded = true;
            }
        } catch (IOException e) {
            log.warn("[FEC_BER] FEC_BER Error in unexpected format{}", message, e);
            scope.emit(MetricNames.ProcessNcpResult.FecBerErrorFormatUnexpected, 1.0);
            Map<String, String> result = new HashMap<>();
            result.put(DEVICE_NAME, deviceId);
            result.put(DEVICE_RACK, UNKNOWN);
            result.put(DEVICE_PORT, UNKNOWN);
            result.put(PRE_FEC_BER, UNKNOWN);
            result.put(LOCK_STATUS, UNKNOWN);
            result.put(REMOTE_DEVICE, UNKNOWN);
            result.put(REMOTE_INTERFACE, UNKNOWN);
            fecBerResults.add(result);
        }

        if (!anyRowAdded) {
            log.warn("[FEC_BER] FEC_BER Error in unexpected format: {}", message);

            // Reuse existing unexpected-format metric to avoid changing MetricNames
            scope.emit(MetricNames.ProcessNcpResult.FecBerErrorFormatUnexpected, 1.0);
            Map<String, String> result = new HashMap<>();
            result.put(DEVICE_NAME, deviceId);
            result.put(DEVICE_RACK, UNKNOWN);
            result.put(DEVICE_PORT, UNKNOWN);
            result.put(PRE_FEC_BER, UNKNOWN);
            result.put(LOCK_STATUS, UNKNOWN);
            result.put(REMOTE_DEVICE, UNKNOWN);
            result.put(REMOTE_INTERFACE, UNKNOWN);
            fecBerResults.add(result);
        }
    }

    // Convert inner map into valid JSON for ObjectMapper
    private String toJsonLike(String innerMap) {
        if (innerMap == null || innerMap.isBlank()) {
            return "{}";
        }
        String newJson = innerMap.trim();

        newJson = newJson.replaceAll("'([^'\\\\]*(?:\\\\.[^'\\\\]*)*)'\\s*:", "\"$1\":");

        newJson = newJson.replaceAll("\\bTrue\\b", "true");
        newJson = newJson.replaceAll("\\bFalse\\b", "false");
        newJson = newJson.replaceAll("\\bNone\\b", "null");

        newJson =
                newJson.replaceAll(":\\s*'([^'\\\\]*(?:\\\\.[^'\\\\]*)*)'(?=\\s*[},])", ": \"$1\"");

        newJson = newJson.replaceAll(",\\s*$", "");

        newJson = newJson.replaceAll(",\\s*(?=\\})", "");
        return "{" + newJson + "}";
    }
}
