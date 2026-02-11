package com.oracle.pic.networking.lvv.service.dependencies.ncp.extractor;

import com.oracle.pic.commons.metrics.MetricsScope;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Extractor for POWER failures. Mirrors the logic in NcpJobResultProcessor.updatePowerErrors,
 * without modifying that class.
 */
@Slf4j
public class PowerTestResultExtractor implements TestResultExtractor {

    private static final String TEST_POWER = "test_power";
    private static final String POWER = "Power Errors";

    // Power Result Column Names
    private static final String DEVICE_NAME = "Device A Name";

    @Override
    public String testName() {
        return TEST_POWER;
    }

    @Override
    public void extract(
            String deviceId,
            String message,
            MetricsScope scope,
            Map<String, Map<String, List<Map<String, String>>>> deviceResults) {
        deviceResults
                .computeIfAbsent(deviceId, k -> new HashMap<>())
                .computeIfAbsent(POWER, k -> new ArrayList<>())
                .add(Map.of(DEVICE_NAME, deviceId));
    }
}
