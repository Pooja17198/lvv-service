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
        // No interface names matching the regex pattern in this message
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
    void extract_parsesPorts_multiplePatterns_addsRows() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        String message = "Failed: Ethernet1/1 down; something et1/2 also down; and et-2/3 impacted";

        extractor.extract("dev1", message, metricsScope, deviceResults);

        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("dev1");
        List<Map<String, String>> rows = perDevice.get("Interface Errors");
        assertEquals(3, rows.size());

        assertEquals("dev1", rows.get(0).get("Device Name"));
        assertEquals("Ethernet1/1", rows.get(0).get("Device Port"));
        assertEquals("Interface not enables or up", rows.get(0).get("Issue"));

        assertEquals("dev1", rows.get(1).get("Device Name"));
        assertEquals("et1/2", rows.get(1).get("Device Port"));
        assertEquals("Interface not enables or up", rows.get(1).get("Issue"));

        assertEquals("dev1", rows.get(2).get("Device Name"));
        assertEquals("et-2/3", rows.get(2).get("Device Port"));
        assertEquals("Interface not enables or up", rows.get(2).get("Issue"));

        verifyNoMoreInteractions(metricsScope); // no unexpected format metric on valid parse
    }

    @Test
    void extract_withoutFailedPrefix_parsesPorts() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        String message = "Ethernet1/1 down and et1/2 down";

        extractor.extract("devX", message, metricsScope, deviceResults);

        List<Map<String, String>> rows = deviceResults.get("devX").get("Interface Errors");
        assertEquals(2, rows.size());
        assertEquals("Ethernet1/1", rows.get(0).get("Device Port"));
        assertEquals("et1/2", rows.get(1).get("Device Port"));
        verifyNoInteractions(metricsScope);
    }

    @Test
    void extract_deduplicatesPorts_onlyUniqueRows() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        String message = "Failed: Ethernet1/1 down; Ethernet1/1 still down; et1/2 down";

        extractor.extract("devDup", message, metricsScope, deviceResults);

        List<Map<String, String>> rows = deviceResults.get("devDup").get("Interface Errors");
        assertEquals(2, rows.size());
        assertEquals("Ethernet1/1", rows.get(0).get("Device Port"));
        assertEquals("et1/2", rows.get(1).get("Device Port"));
    }

    @Test
    void extract_skipsPortsAlreadyCapturedByLLDP() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();

        // Pre-populate with LLDP Errors containing Device A Port = Ethernet1/1
        Map<String, List<Map<String, String>>> perDevice = new HashMap<>();
        List<Map<String, String>> lldpErrors = new ArrayList<>();
        Map<String, String> lldpRow = new HashMap<>();
        lldpRow.put("Device A Port", "Ethernet1/1");
        lldpErrors.add(lldpRow);
        perDevice.put("LLDP Errors", lldpErrors);
        deviceResults.put("dev2", perDevice);

        // Message contains Ethernet1/1 (should be skipped) and et1/2 (should be added)
        extractor.extract(
                "dev2", "Failed: Ethernet1/1 down; et1/2 down", metricsScope, deviceResults);

        List<Map<String, String>> rows = deviceResults.get("dev2").get("Interface Errors");
        assertEquals(1, rows.size());
        assertEquals("et1/2", rows.get(0).get("Device Port"));
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

        extractor.extract("dev3", "Failed: et1/2 down", metricsScope, deviceResults);

        List<Map<String, String>> rows = deviceResults.get("dev3").get("Interface Errors");
        assertEquals(3, rows.size());
        assertEquals("Ethernet9/9", rows.get(0).get("Device Port"));
        assertEquals("et1/9", rows.get(1).get("Device Port"));
        assertEquals("et1/2", rows.get(2).get("Device Port"));
    }

    @Test
    void extract_separateDeviceIds_isolatedResults() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();

        extractor.extract("d1", "Failed: et1/1 down", metricsScope, deviceResults);
        extractor.extract("d2", "Failed: et1/2 down", metricsScope, deviceResults);

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
        extractor.extract("dev", "Failed: et1/1 down; et1/2 down", metricsScope, deviceResults);
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
