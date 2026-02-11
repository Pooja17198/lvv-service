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

class LldpTestResultExtractorTest {

    private LldpTestResultExtractor extractor;
    private MetricsScope metricsScope;

    @BeforeEach
    void setup() {
        extractor = new LldpTestResultExtractor();
        metricsScope = mock(MetricsScope.class);
        when(metricsScope.emit(any(Enum.class), anyDouble())).thenReturn(metricsScope);
    }

    @Test
    void testName_returnsTestLldp() {
        assertEquals("test_lldp", extractor.testName());
    }

    @Test
    void extract_nullMessage_createsEmptyLldpList() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        extractor.extract("device1", null, metricsScope, deviceResults);
        assertTrue(deviceResults.containsKey("device1"));
        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("device1");
        assertTrue(perDevice.containsKey("LLDP Errors"));
        assertTrue(perDevice.get("LLDP Errors").isEmpty());
    }

    @Test
    void extract_messageNotStartingWithFailed_createsEmptyLldpList() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        extractor.extract("device1", "Some other message", metricsScope, deviceResults);
        assertTrue(deviceResults.containsKey("device1"));
        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("device1");
        assertTrue(perDevice.containsKey("LLDP Errors"));
        assertTrue(perDevice.get("LLDP Errors").isEmpty());
    }

    @Test
    void extract_malformedJson_createsUnknownEntry_and_emitsMetric() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        extractor.extract("device1", "Failed: not_json", metricsScope, deviceResults);

        assertTrue(deviceResults.containsKey("device1"));
        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("device1");
        assertTrue(perDevice.containsKey("LLDP Errors"));
        List<Map<String, String>> lldpErrors = perDevice.get("LLDP Errors");
        assertEquals(1, lldpErrors.size());
        Map<String, String> error = lldpErrors.get(0);
        assertEquals("device1", error.get("Device A Name"));
        assertEquals("Unknown", error.get("LLDP Status"));

        verify(metricsScope).emit(MetricNames.ProcessNcpResult.LldpErrorFormatUnexpected, 1.0);
    }

    @Test
    void extract_messageNotStartingWithLldpFailures_createsEmptyLldpList() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        String message = "Failed: {\"message\":\"Some other failure\"}";
        extractor.extract("device1", message, metricsScope, deviceResults);
        assertTrue(deviceResults.containsKey("device1"));
        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("device1");
        assertTrue(perDevice.containsKey("LLDP Errors"));
        assertTrue(perDevice.get("LLDP Errors").isEmpty());
    }

    @Test
    void extract_emptyErrorsArray_createsEmptyLldpList() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        String message = "Failed: {\"message\":\"LLDP Failures: 0\",\"errors_object\":[]}";
        extractor.extract("device1", message, metricsScope, deviceResults);

        assertTrue(deviceResults.containsKey("device1"));
        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("device1");
        assertTrue(perDevice.containsKey("LLDP Errors"));
        List<Map<String, String>> lldpErrors = perDevice.get("LLDP Errors");
        assertTrue(lldpErrors.isEmpty());
    }

    @Test
    void extract_singleError_mismatchStatus() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        String message =
                "Failed: {\"message\":\"LLDP Failures: 1\",\"errors_object\":[{\"current_origin\":\"devA:Eth1/1:x:y:u1\",\"current_destination\":\"devB:Eth2/2:a:b:u2\",\"expected_destination\":\"devC:Eth3/3:c:d:u3\"}]}";
        extractor.extract("device1", message, metricsScope, deviceResults);

        assertTrue(deviceResults.containsKey("device1"));
        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("device1");
        assertTrue(perDevice.containsKey("LLDP Errors"));
        List<Map<String, String>> lldpErrors = perDevice.get("LLDP Errors");
        assertEquals(1, lldpErrors.size());
        Map<String, String> error = lldpErrors.get(0);
        assertEquals("devA", error.get("Device A Name"));
        assertEquals("Eth1/1", error.get("Device A Port"));
        assertEquals("y", error.get("Device A Rack"));
        assertEquals("devB", error.get("Device B Name"));
        assertEquals("Eth2/2", error.get("Device B Port"));
        assertEquals("b", error.get("Device B Rack"));
        assertEquals("devC", error.get("Expected Device B Name"));
        assertEquals("Eth3/3", error.get("Expected Device B Port"));
        assertEquals("d", error.get("Expected Device B Rack"));
        assertEquals("MISMATCH", error.get("LLDP Status"));
    }

    @Test
    void extract_cryptoInDeviceName_setsUnsupportedStatus() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        String message =
                "Failed: {\"message\":\"LLDP Failures: 1\",\"errors_object\":[{\"current_origin\":\"cryptoDev:Eth1/1:x:y:U10\",\"current_destination\":\"devB:Eth2/2:a:b:U20\",\"expected_destination\":\"devZ:Eth3/3:c:d:U30\"}]}";
        extractor.extract("device1", message, metricsScope, deviceResults);

        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("device1");
        List<Map<String, String>> lldpErrors = perDevice.get("LLDP Errors");
        assertEquals(1, lldpErrors.size());
        Map<String, String> error = lldpErrors.get(0);
        assertEquals("UNSUPPORTED", error.get("LLDP Status"));
    }

    @Test
    void extract_destinationUnknown_setsInterfaceDownStatus() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        String message =
                "Failed: {\"message\":\"LLDP Failures: 1\",\"errors_object\":[{\"current_origin\":\"devA:Eth1/1:x:y:U10\",\"current_destination\":\"Unknown:Unknown:Unknown\",\"expected_destination\":\"devZ:Eth3/3:c:d:U30\"}]}";
        extractor.extract("device1", message, metricsScope, deviceResults);

        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("device1");
        List<Map<String, String>> lldpErrors = perDevice.get("LLDP Errors");
        assertEquals(1, lldpErrors.size());
        Map<String, String> error = lldpErrors.get(0);
        assertEquals("INTERFACE_DOWN", error.get("LLDP Status"));
    }

    @Test
    void extract_multipleErrors_processesAll() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        String message =
                "Failed: {\"message\":\"LLDP Failures: 2\",\"errors_object\":[{\"current_origin\":\"devA:Eth1/1:x:y:u1\",\"current_destination\":\"devB:Eth2/2:a:b:u2\",\"expected_destination\":\"devC:Eth3/3:c:d:u3\"},{\"current_origin\":\"devD:Eth4/4:x:y:u4\",\"current_destination\":\"devE:Eth5/5:a:b:u5\",\"expected_destination\":\"devF:Eth6/6:c:d:u6\"}]}";
        extractor.extract("device1", message, metricsScope, deviceResults);

        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("device1");
        List<Map<String, String>> lldpErrors = perDevice.get("LLDP Errors");
        assertEquals(2, lldpErrors.size());
        assertEquals("MISMATCH", lldpErrors.get(0).get("LLDP Status"));
        assertEquals("MISMATCH", lldpErrors.get(1).get("LLDP Status"));
    }

    @Test
    void constructor_withNullMapper_usesDefault() {
        LldpTestResultExtractor extractorWithNull = new LldpTestResultExtractor(null);
        // Should not throw, and should work normally
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        extractorWithNull.extract("device1", null, metricsScope, deviceResults);
        assertTrue(deviceResults.containsKey("device1"));
        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("device1");
        assertTrue(perDevice.containsKey("LLDP Errors"));
        assertTrue(perDevice.get("LLDP Errors").isEmpty());
    }

    @Test
    void constructor_withCustomMapper_usesProvided() {
        ObjectMapper customMapper = new ObjectMapper();
        LldpTestResultExtractor extractorWithCustom = new LldpTestResultExtractor(customMapper);
        // Test that it works (we can't easily verify the mapper is used without spying, but at
        // least no NPE)
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        extractorWithCustom.extract("device1", null, metricsScope, deviceResults);
        assertTrue(deviceResults.containsKey("device1"));
        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("device1");
        assertTrue(perDevice.containsKey("LLDP Errors"));
        assertTrue(perDevice.get("LLDP Errors").isEmpty());
    }

    // Tests for parseDeviceString - since it's private, we test indirectly through extract
    // But to test edge cases, we can use malformed inputs that hit the parse logic

    @Test
    void extract_malformedCurrentOrigin_parsesAsUnknown() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        String message =
                "Failed: {\"message\":\"LLDP Failures: 1\",\"errors_object\":[{\"current_origin\":\"\",\"current_destination\":\"devB:Eth2/2:a:b:u2\",\"expected_destination\":\"devC:Eth3/3:c:d:u3\"}]}";
        extractor.extract("device1", message, metricsScope, deviceResults);

        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("device1");
        List<Map<String, String>> lldpErrors = perDevice.get("LLDP Errors");
        assertEquals(1, lldpErrors.size());
        Map<String, String> error = lldpErrors.get(0);
        assertEquals("Unknown", error.get("Device A Name"));
        assertEquals("Unknown", error.get("Device A Port"));
        assertEquals("Unknown", error.get("Device A Rack"));
    }

    @Test
    void extract_insufficientPartsInCurrentOrigin_parsesAsUnknown() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        String message =
                "Failed: {\"message\":\"LLDP Failures: 1\",\"errors_object\":[{\"current_origin\":\"devA:Eth1/1\",\"current_destination\":\"devB:Eth2/2:a:b:u2\",\"expected_destination\":\"devC:Eth3/3:c:d:u3\"}]}";
        extractor.extract("device1", message, metricsScope, deviceResults);

        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("device1");
        List<Map<String, String>> lldpErrors = perDevice.get("LLDP Errors");
        assertEquals(1, lldpErrors.size());
        Map<String, String> error = lldpErrors.get(0);
        assertEquals("Unknown", error.get("Device A Name"));
        assertEquals("Unknown", error.get("Device A Port"));
        assertEquals("Unknown", error.get("Device A Rack"));
    }

    @Test
    void extract_missingErrorsObject_createsEmptyLldpList() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        // errors_object missing
        String message = "Failed: {\"message\":\"LLDP Failures: 1\"}";
        extractor.extract("device1", message, metricsScope, deviceResults);
        assertTrue(deviceResults.containsKey("device1"));
        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("device1");
        assertTrue(perDevice.containsKey("LLDP Errors"));
        assertTrue(perDevice.get("LLDP Errors").isEmpty());
    }

    @Test
    void extract_errorsObjectNotArray_createsEmptyLldpList() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        // errors_object present but not an array
        String message = "Failed: {\"message\":\"LLDP Failures: 1\",\"errors_object\":{}}";
        extractor.extract("device1", message, metricsScope, deviceResults);
        assertTrue(deviceResults.containsKey("device1"));
        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("device1");
        assertTrue(perDevice.containsKey("LLDP Errors"));
        assertTrue(perDevice.get("LLDP Errors").isEmpty());
    }

    @Test
    void extract_cryptoInDestination_setsUnsupportedStatus() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        String message =
                "Failed: {\"message\":\"LLDP Failures: 1\",\"errors_object\":[{\"current_origin\":\"devA:Eth1/1:x:y:U10\",\"current_destination\":\"cryptoDest:Eth9/9:a:b:U99\",\"expected_destination\":\"devZ:Eth3/3:c:d:U30\"}]}";
        extractor.extract("device1", message, metricsScope, deviceResults);

        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("device1");
        List<Map<String, String>> lldpErrors = perDevice.get("LLDP Errors");
        assertEquals(1, lldpErrors.size());
        Map<String, String> error = lldpErrors.get(0);
        assertEquals("UNSUPPORTED", error.get("LLDP Status"));
    }

    @Test
    void extract_missingDestination_setsInterfaceDownStatus() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        // current_destination is missing entirely -> should parse as Unknown and set INTERFACE_DOWN
        String message =
                "Failed: {\"message\":\"LLDP Failures: 1\",\"errors_object\":[{\"current_origin\":\"devA:Eth1/1:x:y:U10\",\"expected_destination\":\"devZ:Eth3/3:c:d:U30\"}]}";
        extractor.extract("device1", message, metricsScope, deviceResults);

        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("device1");
        List<Map<String, String>> lldpErrors = perDevice.get("LLDP Errors");
        assertEquals(1, lldpErrors.size());
        Map<String, String> error = lldpErrors.get(0);
        assertEquals("Unknown", error.get("Device B Name"));
        assertEquals("Unknown", error.get("Device B Port"));
        assertEquals("Unknown", error.get("Device B Rack"));
        assertEquals("INTERFACE_DOWN", error.get("LLDP Status"));
    }

    @Test
    void extract_failedPrefixWithoutJson_createsUnknown_and_emitsMetric() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        extractor.extract("device1", "Failed:", metricsScope, deviceResults);

        assertTrue(deviceResults.containsKey("device1"));
        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("device1");
        assertTrue(perDevice.containsKey("LLDP Errors"));
        List<Map<String, String>> lldpErrors = perDevice.get("LLDP Errors");
        assertEquals(1, lldpErrors.size());
        Map<String, String> error = lldpErrors.get(0);
        assertEquals("device1", error.get("Device A Name"));
        assertEquals("Unknown", error.get("LLDP Status"));

        verify(metricsScope, atLeastOnce())
                .emit(MetricNames.ProcessNcpResult.LldpErrorFormatUnexpected, 1.0);
    }
}
