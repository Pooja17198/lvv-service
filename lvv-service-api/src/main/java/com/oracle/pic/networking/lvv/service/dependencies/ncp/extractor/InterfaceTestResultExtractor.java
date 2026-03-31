package com.oracle.pic.networking.lvv.service.dependencies.ncp.extractor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Extractor for INTERFACE failures. Mirrors the logic in
 * NcpJobResultProcessor.extractInterfaceDownErrors, without modifying that class.
 */
@Slf4j
public class InterfaceTestResultExtractor implements TestResultExtractor {

    private static final String TEST_INTERFACES = "test_interfaces";
    private static final String UNKNOWN = "Unknown";
    private static final String INTERFACE = "Interface Errors";

    private final ObjectMapper mapper;

    // Interface Result Column Names
    private static final String DEVICE_NAME = "Device Name";
    private static final String DEVICE_PORT = "Device Port";
    private static final String ISSUE = "Issue";

    public InterfaceTestResultExtractor() {
        this.mapper = new ObjectMapper();
    }

    public InterfaceTestResultExtractor(ObjectMapper mapper) {
        this.mapper = mapper != null ? mapper : new ObjectMapper();
    }

    @Override
    public String testName() {
        return TEST_INTERFACES;
    }

    @Override
    public void extract(
            String deviceId,
            String message,
            MetricsScope scope,
            Map<String, Map<String, List<Map<String, String>>>> deviceResults) {
        Map<String, List<Map<String, String>>> perDeviceMap =
                deviceResults.computeIfAbsent(deviceId, k -> new HashMap<>());

        List<Map<String, String>> interfaceResults =
                perDeviceMap.computeIfAbsent(INTERFACE, k -> new ArrayList<>());

        if (message == null || message.isBlank()) {
            return;
        }
        log.debug(
                "[INTERFACE] Processing Interface Error message for device {}: {}",
                deviceId,
                message);

        final String ISSUE_DESCRIPTION = "Interfaces are not enabled or up";

        try {
            LinkedHashSet<String> ports = extractPorts(message);

            // For empty Interface error ports, we set the status to UNKNOWN
            if (ports.isEmpty()) {
                log.warn("[INTERFACE] No interface names found in message");
                scope.emit(MetricNames.ProcessNcpResult.InterfaceErrorFormatUnexpected, 1.0);

                Map<String, String> result = new HashMap<>();
                result.put(DEVICE_NAME, deviceId);
                result.put(DEVICE_PORT, UNKNOWN);
                result.put(ISSUE, UNKNOWN);
                interfaceResults.add(result);
                return;
            }

            // check if this interface is already validated in LLDP errors
            HashSet<String> lldpPorts = new HashSet<>();
            for (List<Map<String, String>> tableRows : perDeviceMap.values()) {
                if (tableRows == null) {
                    continue;
                }
                for (Map<String, String> row : tableRows) {
                    if (row == null) {
                        continue;
                    }
                    String lldpPortName = row.get("Device A Port");

                    // column from LLDP rows
                    if (lldpPortName != null && !lldpPortName.isBlank()) {
                        lldpPorts.add(lldpPortName.trim());
                    }
                }
            }

            for (String port : ports) {
                if (lldpPorts.contains(port)) {
                    log.info("[INTERFACE] Skipping port {} already captured by LLDP", port);
                    continue;
                }
                Map<String, String> row = new HashMap<>();
                row.put(DEVICE_NAME, deviceId);
                row.put(DEVICE_PORT, port);
                row.put(ISSUE, ISSUE_DESCRIPTION);
                interfaceResults.add(row);
            }
        } catch (Exception e) {
            log.warn("[INTERFACE] Interface Error in unexpected format {}", message, e);
            scope.emit(MetricNames.ProcessNcpResult.InterfaceErrorFormatUnexpected, 1.0);

            Map<String, String> result = new HashMap<>();
            result.put(DEVICE_NAME, deviceId);
            result.put(DEVICE_PORT, UNKNOWN);
            result.put(ISSUE, UNKNOWN);
            interfaceResults.add(result);
        }
        log.info("[INTERFACE] Parsed output for Interface error: deviceName: {}", deviceId);
    }

    // returns all port names extracted from the message
    private LinkedHashSet<String> extractPorts(String message) {
        LinkedHashSet<String> ports = new LinkedHashSet<>();

        int start = message.lastIndexOf('[');
        int end = message.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return ports;
        }

        String portList = message.substring(start + 1, end).trim();
        if (portList.isBlank()) {
            return ports;
        }

        for (String rawPort : portList.split(",")) {
            String port = rawPort.trim();
            if ((port.startsWith("'") && port.endsWith("'"))
                    || (port.startsWith("\"") && port.endsWith("\""))) {
                port = port.substring(1, port.length() - 1).trim();
            }
            if (!port.isBlank()) {
                ports.add(port);
            }
        }

        return ports;
    }
}
