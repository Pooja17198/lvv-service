package com.oracle.pic.networking.lvv.service.dependencies.ncp.extractor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InterfaceTestResultExtractorTest {

    private InterfaceTestResultExtractor extractor;
    private MetricsScope metricsScope;

    @BeforeEach
    void setup() {
        extractor = new InterfaceTestResultExtractor();
        metricsScope = mock(MetricsScope.class);
        when(metricsScope.emit(any(Enum.class), anyDouble())).thenReturn(metricsScope);
    }

    @Test
    void testName_returnsTestInterfaces() {
        assertEquals("test_interfaces", extractor.testName());
    }

    @Test
    void extract_nullMessage_createsEmptyInterfaceList() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        extractor.extract("device1", null, metricsScope, deviceResults);

        assertTrue(deviceResults.containsKey("device1"));
        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("device1");
        assertTrue(perDevice.containsKey("Interface Errors"));
        assertTrue(perDevice.get("Interface Errors").isEmpty());
        verifyNoInteractions(metricsScope);
    }

    @Test
    void extract_blankMessage_createsEmptyInterfaceList() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        extractor.extract("device1", "   \n\t  ", metricsScope, deviceResults);

        assertTrue(deviceResults.containsKey("device1"));
        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("device1");
        assertTrue(perDevice.containsKey("Interface Errors"));
        assertTrue(perDevice.get("Interface Errors").isEmpty());
        verifyNoInteractions(metricsScope);
    }

    @Test
    void extract_messageWithoutInterfaces_addsUnknownRow_andEmitsMetric() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();

        extractor.extract(
                "deviceA", "Failed: Ports appear to be down", metricsScope, deviceResults);

        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("deviceA");
        List<Map<String, String>> rows = perDevice.get("Interface Errors");
        assertEquals(1, rows.size());

        Map<String, String> row = rows.get(0);
        assertEquals("deviceA", row.get("Device Name"));
        assertEquals("Unknown", row.get("Device Port"));
        assertEquals("Unknown", row.get("Issue"));

        verify(metricsScope).emit(MetricNames.ProcessNcpResult.InterfaceErrorFormatUnexpected, 1.0);
    }

    @Test
    void extract_parsesBracketedPorts_multipleRowsAdded() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        String message =
                "Failed: Device aga5-q2-p4-t0-r28 interfaces are not enabled or up: ['swp39s0', 'swp55s1']";

        extractor.extract("dev1", message, metricsScope, deviceResults);

        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("dev1");
        List<Map<String, String>> rows = perDevice.get("Interface Errors");
        assertEquals(2, rows.size());

        assertEquals("dev1", rows.get(0).get("Device Name"));
        assertEquals("swp39s0", rows.get(0).get("Device Port"));
        assertEquals("Interfaces are not enabled or up", rows.get(0).get("Issue"));

        assertEquals("dev1", rows.get(1).get("Device Name"));
        assertEquals("swp55s1", rows.get(1).get("Device Port"));
        assertEquals("Interfaces are not enabled or up", rows.get(1).get("Issue"));

        verifyNoMoreInteractions(metricsScope);
    }

    @Test
    void extract_parsesBracketedEthernetPort_addsSingleRow() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        String message =
                "Failed: Device dxb3-c1-b3-t0-r10 interfaces are not enabled or up: ['Ethernet1/22']";

        extractor.extract("devX", message, metricsScope, deviceResults);

        List<Map<String, String>> rows = deviceResults.get("devX").get("Interface Errors");
        assertEquals(1, rows.size());
        assertEquals("Ethernet1/22", rows.get(0).get("Device Port"));
        assertEquals("Interfaces are not enabled or up", rows.get(0).get("Issue"));
        verifyNoInteractions(metricsScope);
    }

    @Test
    void extract_deduplicatesBracketedPorts_onlyUniqueRows() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        String message =
                "Failed: Device devDup interfaces are not enabled or up: ['swp39s0', 'swp39s0', 'swp55s1']";

        extractor.extract("devDup", message, metricsScope, deviceResults);

        List<Map<String, String>> rows = deviceResults.get("devDup").get("Interface Errors");
        assertEquals(2, rows.size());
        assertEquals("swp39s0", rows.get(0).get("Device Port"));
        assertEquals("swp55s1", rows.get(1).get("Device Port"));
    }

    @Test
    void extract_skipsPortsAlreadyCapturedByLLDP() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();

        Map<String, List<Map<String, String>>> perDevice = new HashMap<>();
        List<Map<String, String>> lldpErrors = new ArrayList<>();
        Map<String, String> lldpRow = new HashMap<>();
        lldpRow.put("Device A Port", "Ethernet1/1");
        lldpErrors.add(lldpRow);
        perDevice.put("LLDP Errors", lldpErrors);
        deviceResults.put("dev2", perDevice);

        extractor.extract(
                "dev2",
                "Failed: Device dev2 interfaces are not enabled or up: ['Ethernet1/1', 'Ethernet1/2']",
                metricsScope,
                deviceResults);

        List<Map<String, String>> rows = deviceResults.get("dev2").get("Interface Errors");
        assertEquals(1, rows.size());
        assertEquals("Ethernet1/2", rows.get(0).get("Device Port"));
    }

    @Test
    void extract_appendsToExistingList_whenCategoryPresent() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        Map<String, List<Map<String, String>>> perDevice = new HashMap<>();
        List<Map<String, String>> existing = new ArrayList<>();
        existing.add(Map.of("Device Name", "preA", "Device Port", "Ethernet9/9", "Issue", "x"));
        existing.add(Map.of("Device Name", "preB", "Device Port", "et1/9", "Issue", "y"));
        perDevice.put("Interface Errors", existing);
        deviceResults.put("dev3", perDevice);

        extractor.extract(
                "dev3",
                "Failed: Device dev3 interfaces are not enabled or up: ['et1/2']",
                metricsScope,
                deviceResults);

        List<Map<String, String>> rows = deviceResults.get("dev3").get("Interface Errors");
        assertEquals(3, rows.size());
        assertEquals("Ethernet9/9", rows.get(0).get("Device Port"));
        assertEquals("et1/9", rows.get(1).get("Device Port"));
        assertEquals("et1/2", rows.get(2).get("Device Port"));
    }

    @Test
    void extract_separateDeviceIds_isolatedResults() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();

        extractor.extract(
                "d1",
                "Failed: Device d1 interfaces are not enabled or up: ['et1/1']",
                metricsScope,
                deviceResults);
        extractor.extract(
                "d2",
                "Failed: Device d2 interfaces are not enabled or up: ['et1/2']",
                metricsScope,
                deviceResults);

        assertTrue(deviceResults.containsKey("d1"));
        assertTrue(deviceResults.containsKey("d2"));

        assertEquals(1, deviceResults.get("d1").get("Interface Errors").size());
        assertEquals(1, deviceResults.get("d2").get("Interface Errors").size());
        assertEquals(
                "et1/1", deviceResults.get("d1").get("Interface Errors").get(0).get("Device Port"));
        assertEquals(
                "et1/2", deviceResults.get("d2").get("Interface Errors").get(0).get("Device Port"));
    }

    @Test
    void extract_doesNotUseMetricsScope_onValidParse() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        extractor.extract(
                "dev",
                "Failed: Device dev interfaces are not enabled or up: ['et1/1', 'et1/2']",
                metricsScope,
                deviceResults);
        verifyNoMoreInteractions(metricsScope);
    }

    @Test
    void constructor_withNullMapper_usesDefault() {
        InterfaceTestResultExtractor custom = new InterfaceTestResultExtractor((ObjectMapper) null);
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        custom.extract("deviceZ", null, metricsScope, deviceResults);
        assertTrue(deviceResults.containsKey("deviceZ"));
        assertTrue(deviceResults.get("deviceZ").containsKey("Interface Errors"));
        assertTrue(deviceResults.get("deviceZ").get("Interface Errors").isEmpty());
    }

    @Test
    void constructor_withCustomMapper_usesProvided() {
        ObjectMapper mapper = new ObjectMapper();
        InterfaceTestResultExtractor custom = new InterfaceTestResultExtractor(mapper);
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        custom.extract("deviceY", null, metricsScope, deviceResults);
        assertTrue(deviceResults.containsKey("deviceY"));
        assertTrue(deviceResults.get("deviceY").containsKey("Interface Errors"));
        assertTrue(deviceResults.get("deviceY").get("Interface Errors").isEmpty());
    }
}
