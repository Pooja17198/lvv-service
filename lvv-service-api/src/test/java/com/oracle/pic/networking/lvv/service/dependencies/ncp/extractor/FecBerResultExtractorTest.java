package com.oracle.pic.networking.lvv.service.dependencies.ncp.extractor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FecBerResultExtractorTest {

    private FecBerTestResultExtractor extractor;
    private MetricsScope metricsScope;

    @BeforeEach
    void setup() {
        extractor = new FecBerTestResultExtractor();
        metricsScope = mock(MetricsScope.class);
        when(metricsScope.emit(any(Enum.class), anyDouble())).thenReturn(metricsScope);
    }

    @Test
    void testName_returnsTestFecBerThreshold() {
        assertEquals("test_fec_ber_threshold", extractor.testName());
    }

    @Test
    void extract_nullMessage_createsEmptyFecBerList() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        extractor.extract("dev1", null, metricsScope, deviceResults);

        assertTrue(deviceResults.containsKey("dev1"));
        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("dev1");
        assertTrue(perDevice.containsKey("FEC_BER Errors"));
        assertTrue(perDevice.get("FEC_BER Errors").isEmpty());
        verifyNoInteractions(metricsScope);
    }

    @Test
    void extract_blankMessage_createsEmptyFecBerList() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        extractor.extract("dev1", "   \n\t", metricsScope, deviceResults);

        assertTrue(deviceResults.containsKey("dev1"));
        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("dev1");
        assertTrue(perDevice.containsKey("FEC_BER Errors"));
        assertTrue(perDevice.get("FEC_BER Errors").isEmpty());
        verifyNoInteractions(metricsScope);
    }

    @Test
    void extract_singleValidBlock_parsesAllFields_andNoMetric() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        String message =
                "Failed: { 'Ethernet1/1': { "
                        + "'rack': 'R01', 'device_name': 'sw-01', 'pre_fec_ber': '1.2e-5', "
                        + "'lock_status': True, 'remote_device': 'sw-02', 'remote_interface': 'Ethernet1/2', "
                        + "} }";

        extractor.extract("devX", message, metricsScope, deviceResults);

        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("devX");
        List<Map<String, String>> rows = perDevice.get("FEC_BER Errors");
        assertEquals(1, rows.size());
        Map<String, String> r = rows.get(0);

        assertEquals("R01", r.get("Device Rack"));
        assertEquals("sw-01", r.get("Device Name"));
        assertEquals("Ethernet1/1", r.get("Device Port"));
        assertEquals("1.2e-5", r.get("PRE_FEC_BER"));
        assertEquals("true", r.get("Lock Status"));
        assertEquals("sw-02", r.get("Remote Device"));
        assertEquals("Ethernet1/2", r.get("Remote Interface"));

        verifyNoInteractions(metricsScope);
    }

    @Test
    void extract_multipleBlocks_processesAll() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        String message =
                "{ 'Ethernet1/1': { 'rack': 'R01', 'device_name': 'A', 'pre_fec_ber': '1e-6', 'lock_status': False } } "
                        + "noise "
                        + "{ 'Ethernet1/2': { 'rack': 'R02', 'device_name': 'B', 'pre_fec_ber': '2e-6', 'lock_status': True } }";

        extractor.extract("dev1", message, metricsScope, deviceResults);

        List<Map<String, String>> rows = deviceResults.get("dev1").get("FEC_BER Errors");
        assertEquals(2, rows.size());

        assertEquals("R01", rows.get(0).get("Device Rack"));
        assertEquals("A", rows.get(0).get("Device Name"));
        assertEquals("Ethernet1/1", rows.get(0).get("Device Port"));
        assertEquals("1e-6", rows.get(0).get("PRE_FEC_BER"));
        assertEquals("false", rows.get(0).get("Lock Status"));

        assertEquals("R02", rows.get(1).get("Device Rack"));
        assertEquals("B", rows.get(1).get("Device Name"));
        assertEquals("Ethernet1/2", rows.get(1).get("Device Port"));
        assertEquals("2e-6", rows.get(1).get("PRE_FEC_BER"));
        assertEquals("true", rows.get(1).get("Lock Status"));

        verifyNoInteractions(metricsScope);
    }

    @Test
    void extract_unexpectedFormat_addsUnknownRow_andEmitsMetric() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        extractor.extract(
                "devZ", "not matching the expected block structure", metricsScope, deviceResults);

        List<Map<String, String>> rows = deviceResults.get("devZ").get("FEC_BER Errors");
        assertEquals(1, rows.size());
        Map<String, String> r = rows.get(0);

        assertEquals("devZ", r.get("Device Name"));
        assertEquals("Unknown", r.get("Device Rack"));
        assertEquals("Unknown", r.get("Device Port"));
        assertEquals("Unknown", r.get("PRE_FEC_BER"));
        assertEquals("Unknown", r.get("Lock Status"));
        assertEquals("Unknown", r.get("Remote Device"));
        assertEquals("Unknown", r.get("Remote Interface"));

        verify(metricsScope).emit(MetricNames.ProcessNcpResult.FecBerErrorFormatUnexpected, 1.0);
    }

    @Test
    void extract_missingFields_defaultsAndUnknowns() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        String message = "{ 'Eth9/9': { 'rack': 'R99', 'lock_status': False } }";

        extractor.extract("fallbackDev", message, metricsScope, deviceResults);

        List<Map<String, String>> rows = deviceResults.get("fallbackDev").get("FEC_BER Errors");
        assertEquals(1, rows.size());
        Map<String, String> r = rows.get(0);

        assertEquals("R99", r.get("Device Rack"));
        assertEquals("fallbackDev", r.get("Device Name"));
        assertEquals("Eth9/9", r.get("Device Port"));
        assertEquals("Unknown", r.get("PRE_FEC_BER"));
        assertEquals("false", r.get("Lock Status"));
        assertEquals("Unknown", r.get("Remote Device"));
        assertEquals("Unknown", r.get("Remote Interface"));

        verifyNoInteractions(metricsScope);
    }

    @Test
    void constructor_withNullMapper_usesDefault() {
        FecBerTestResultExtractor custom = new FecBerTestResultExtractor((ObjectMapper) null);
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        custom.extract("devN", null, metricsScope, deviceResults);

        assertTrue(deviceResults.containsKey("devN"));
        assertTrue(deviceResults.get("devN").containsKey("FEC_BER Errors"));
        assertTrue(deviceResults.get("devN").get("FEC_BER Errors").isEmpty());
    }

    @Test
    void constructor_withCustomMapper_usesProvided() {
        ObjectMapper mapper = new ObjectMapper();
        FecBerTestResultExtractor custom = new FecBerTestResultExtractor(mapper);
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        custom.extract("devM", null, metricsScope, deviceResults);

        assertTrue(deviceResults.containsKey("devM"));
        assertTrue(deviceResults.get("devM").containsKey("FEC_BER Errors"));
        assertTrue(deviceResults.get("devM").get("FEC_BER Errors").isEmpty());
    }
}
