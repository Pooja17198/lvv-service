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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    private static final Pattern INTERFACE_NAME =
            Pattern.compile("\\b(?:Ethernet\\d+(?:/\\d+)+|et-?\\d+(?:/\\d+)+)\\b");

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
                "[INTERFACE] Processing Interface Error message for device {}: \n {}",
                deviceId,
                message);

        final String PREFIX = "Failed:";
        final String ISSUE_DESCRIPTION = "Interface not enables or up";

        try {
            String body = message.trim();
            if (body.startsWith(PREFIX)) {
                body = body.substring(PREFIX.length()).trim();
            }

            // Parse the message to add ports to a list
            LinkedHashSet<String> ports = new LinkedHashSet<>();
            Matcher portName = INTERFACE_NAME.matcher(body);
            while (portName.find()) {
                ports.add(portName.group().trim());
            }

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
}
