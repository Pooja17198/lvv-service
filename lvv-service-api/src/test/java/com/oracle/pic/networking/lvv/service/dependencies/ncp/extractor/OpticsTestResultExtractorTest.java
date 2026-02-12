package com.oracle.pic.networking.lvv.service.dependencies.ncp.extractor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpticsTestResultExtractorTest {

    private OpticsTestResultExtractor extractor;
    private MetricsScope metricsScope;

    @BeforeEach
    void setup() {
        extractor = new OpticsTestResultExtractor();
        metricsScope = mock(MetricsScope.class);
        when(metricsScope.emit(any(Enum.class), anyDouble())).thenReturn(metricsScope);
    }

    @Test
    void testName_returnsTestOptics() {
        assertEquals("test_optics", extractor.testName());
    }

    @Test
    void extract_nullMessage_createsEmptyOpticsList() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        extractor.extract("device1", null, metricsScope, deviceResults);

        assertTrue(deviceResults.containsKey("device1"));
        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("device1");
        assertTrue(perDevice.containsKey("Optic Errors"));
        assertTrue(perDevice.get("Optic Errors").isEmpty());
    }

    @Test
    void extract_withFailedPrefix_validJson_parsesSingleEntry() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        String message =
                "Failed: {\"errors_object\":[{\"device\":\"devA\",\"intf_name\":\"Eth1/1\",\"output_power\":\"1.2\",\"input_power\":\"-3.4\"}]}";

        extractor.extract("device1", message, metricsScope, deviceResults);

        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("device1");
        assertNotNull(perDevice);
        List<Map<String, String>> optics = perDevice.get("Optic Errors");
        assertNotNull(optics);
        assertEquals(1, optics.size());
        Map<String, String> row = optics.get(0);
        assertEquals("devA", row.get("Device Name"));
        assertEquals("Eth1/1", row.get("Device Port"));
        assertEquals("1.2", row.get("Tx Power"));
        assertEquals("-3.4", row.get("Rx Power"));
    }

    @Test
    void extract_withoutPrefix_validJson_parsesSingleEntry() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        String message =
                "{\"errors_object\":[{\"device\":\"devB\",\"intf_name\":\"Eth2/2\",\"output_power\":\"2.5\",\"input_power\":\"-1.1\"}]}";

        extractor.extract("device1", message, metricsScope, deviceResults);

        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("device1");
        List<Map<String, String>> optics = perDevice.get("Optic Errors");
        assertEquals(1, optics.size());
        Map<String, String> row = optics.get(0);
        assertEquals("devB", row.get("Device Name"));
        assertEquals("Eth2/2", row.get("Device Port"));
        assertEquals("2.5", row.get("Tx Power"));
        assertEquals("-1.1", row.get("Rx Power"));
    }

    @Test
    void extract_multipleErrors_parsesAll() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        String message =
                "Failed: {\"errors_object\":["
                        + "{\"device\":\"devA\",\"intf_name\":\"Eth1/1\",\"output_power\":\"1.2\",\"input_power\":\"-3.4\"},"
                        + "{\"device\":\"devB\",\"intf_name\":\"Eth2/2\",\"output_power\":\"2.5\",\"input_power\":\"-1.1\"}"
                        + "]}";

        extractor.extract("device1", message, metricsScope, deviceResults);

        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("device1");
        List<Map<String, String>> optics = perDevice.get("Optic Errors");
        assertEquals(2, optics.size());
        assertEquals("devA", optics.get(0).get("Device Name"));
        assertEquals("devB", optics.get(1).get("Device Name"));
    }

    @Test
    void extract_duplicateInterfaceErrors_collapsesToSingleRowPerInterface() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        String message =
                "Failed: {\"errors_object\":["
                        + "{\"device\":\"devA\",\"intf_name\":\"Eth1/1\",\"output_power\":\"1.2\",\"input_power\":\"-3.4\"},"
                        + "{\"device\":\"devA\",\"intf_name\":\"Eth1/1\",\"output_power\":\"1.3\",\"input_power\":\"-3.5\"},"
                        + "{\"device\":\"devA\",\"intf_name\":\"Eth1/1\",\"output_power\":\"1.4\",\"input_power\":\"-3.6\"}"
                        + "]}";
        extractor.extract("device1", message, metricsScope, deviceResults);
        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("device1");
        List<Map<String, String>> optics = perDevice.get("Optic Errors");
        assertEquals(1, optics.size());
        Map<String, String> row = optics.get(0);
        assertEquals("devA", row.get("Device Name"));
        assertEquals("Eth1/1", row.get("Device Port"));
        assertEquals("1.4", row.get("Tx Power"));
        assertEquals("-3.6", row.get("Rx Power"));
    }

    @Test
    void extract_emptyErrorsArray_createsEmptyOpticsList() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        String message = "Failed: {\"errors_object\":[]}";

        extractor.extract("device1", message, metricsScope, deviceResults);

        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("device1");
        assertTrue(perDevice.containsKey("Optic Errors"));
        assertTrue(perDevice.get("Optic Errors").isEmpty());
    }

    @Test
    void extract_missingErrorsObject_createsEmptyOpticsList() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        String message = "Failed: {\"message\":\"Something\"}";

        extractor.extract("device1", message, metricsScope, deviceResults);

        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("device1");
        assertTrue(perDevice.containsKey("Optic Errors"));
        assertTrue(perDevice.get("Optic Errors").isEmpty());
    }

    @Test
    void extract_errorsObjectNotArray_createsEmptyOpticsList() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        String message = "Failed: {\"errors_object\":{}}";

        extractor.extract("device1", message, metricsScope, deviceResults);

        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("device1");
        assertTrue(perDevice.containsKey("Optic Errors"));
        assertTrue(perDevice.get("Optic Errors").isEmpty());
    }

    @Test
    void extract_failedPrefixWithoutJson_returnsEmptyOpticsList_noException() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();

        // Gracefully handle empty JSON after "Failed:" without throwing
        extractor.extract("device1", "Failed: ", metricsScope, deviceResults);

        assertTrue(deviceResults.containsKey("device1"));
        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("device1");
        assertTrue(perDevice.containsKey("Optic Errors"));
        assertTrue(perDevice.get("Optic Errors").isEmpty());

        // No unexpected format metric should be emitted since no parse exception was thrown
        verify(metricsScope, never())
                .emit(eq(MetricNames.ProcessNcpResult.OpticErrorFormatUnexpected), anyDouble());
    }

    @Test
    void extract_malformedJson_throwsRenderable_and_emitsMetric() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        RenderableException ex =
                assertThrows(
                        RenderableException.class,
                        () ->
                                extractor.extract(
                                        "device1",
                                        "Failed: not_json",
                                        metricsScope,
                                        deviceResults));

        assertTrue(ex.getMessage().contains("Optic Error in unexpected format"));
        verify(metricsScope, atLeastOnce())
                .emit(eq(MetricNames.ProcessNcpResult.OpticErrorFormatUnexpected), anyDouble());
    }

    @Test
    void constructor_withNullMapper_usesDefault() {
        OpticsTestResultExtractor extractorWithNull = new OpticsTestResultExtractor(null);
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        extractorWithNull.extract("device1", null, metricsScope, deviceResults);
        assertTrue(deviceResults.containsKey("device1"));
        assertTrue(deviceResults.get("device1").containsKey("Optic Errors"));
        assertTrue(deviceResults.get("device1").get("Optic Errors").isEmpty());
    }

    @Test
    void constructor_withCustomMapper_usesProvided() {
        ObjectMapper customMapper = new ObjectMapper();
        OpticsTestResultExtractor extractorWithCustom = new OpticsTestResultExtractor(customMapper);
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        extractorWithCustom.extract("device1", null, metricsScope, deviceResults);
        assertTrue(deviceResults.containsKey("device1"));
        assertTrue(deviceResults.get("device1").containsKey("Optic Errors"));
        assertTrue(deviceResults.get("device1").get("Optic Errors").isEmpty());
    }

    @Test
    void extract_errorObjectMissingFields_throwsNullPointerException() {
        // Using missing fields triggers NPE because extractor uses node.get(...).asText()
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        String message = "{\"errors_object\":[{\"device\":\"devA\"}]}";
        assertThrows(
                NullPointerException.class,
                () -> extractor.extract("device1", message, metricsScope, deviceResults));
    }
}
