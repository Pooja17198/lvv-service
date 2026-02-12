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

class FanTestResultExtractorTest {

    private FanTestResultExtractor extractor;
    private MetricsScope metricsScope;

    @BeforeEach
    void setup() {
        extractor = new FanTestResultExtractor();
        metricsScope = mock(MetricsScope.class);
        when(metricsScope.emit(any(Enum.class), anyDouble())).thenReturn(metricsScope);
    }

    @Test
    void testName_returnsTestFans() {
        assertEquals("test_fans", extractor.testName());
    }

    @Test
    void extract_nullMessage_createsEmptyFanList() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        extractor.extract("device1", null, metricsScope, deviceResults);

        assertTrue(deviceResults.containsKey("device1"));
        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("device1");
        assertTrue(perDevice.containsKey("Fan Errors"));
        assertTrue(perDevice.get("Fan Errors").isEmpty());
    }

    @Test
    void extract_withFailedPrefix_validJson_parsesSingleEntry() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        String message =
                "Failed: {\"message\":\"Fan issues found\",\"errors_object\":[{\"fan_name\":\"\",\"fan_slot\":3,\"status\":false}]}";

        extractor.extract("devA", message, metricsScope, deviceResults);

        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("devA");
        assertNotNull(perDevice);
        List<Map<String, String>> fans = perDevice.get("Fan Errors");
        assertNotNull(fans);
        assertEquals(1, fans.size());

        Map<String, String> row = fans.get(0);
        assertEquals("devA", row.get("Device Name"));
        assertEquals("", row.get("Fan Name"));
        assertEquals("3", row.get("Fan Slot"));
        assertEquals("false", row.get("Status"));
    }

    @Test
    void extract_withoutPrefix_validJson_parsesSingleEntry() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        String message =
                "{\"message\":\"Fan issues found\",\"errors_object\":[{\"fan_name\":\"FanTrayA\",\"fan_slot\":1,\"status\":false}]}";

        extractor.extract("devB", message, metricsScope, deviceResults);

        List<Map<String, String>> fans = deviceResults.get("devB").get("Fan Errors");
        assertEquals(1, fans.size());
        Map<String, String> row = fans.get(0);
        assertEquals("devB", row.get("Device Name"));
        assertEquals("FanTrayA", row.get("Fan Name"));
        assertEquals("1", row.get("Fan Slot"));
        assertEquals("false", row.get("Status"));
    }

    @Test
    void extract_multipleErrors_parsesAll() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        String message =
                "Failed: {\"message\":\"Fan issues found\",\"errors_object\":["
                        + "{\"fan_name\":\"FanA\",\"fan_slot\":1,\"status\":false},"
                        + "{\"fan_name\":\"FanB\",\"fan_slot\":2,\"status\":false}"
                        + "]}";

        extractor.extract("devC", message, metricsScope, deviceResults);

        List<Map<String, String>> fans = deviceResults.get("devC").get("Fan Errors");
        assertEquals(2, fans.size());
        assertEquals("FanA", fans.get(0).get("Fan Name"));
        assertEquals("1", fans.get(0).get("Fan Slot"));
        assertEquals("FanB", fans.get(1).get("Fan Name"));
        assertEquals("2", fans.get(1).get("Fan Slot"));
    }

    @Test
    void extract_missingFields_usesUnknownDefaults() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        String message = "{\"errors_object\":[{}]}";

        extractor.extract("devD", message, metricsScope, deviceResults);

        List<Map<String, String>> fans = deviceResults.get("devD").get("Fan Errors");
        assertEquals(1, fans.size());
        Map<String, String> row = fans.get(0);
        assertEquals("devD", row.get("Device Name"));
        assertEquals("Unknown", row.get("Fan Name"));
        assertEquals("Unknown", row.get("Fan Slot"));
        assertEquals("Unknown", row.get("Status"));
    }

    @Test
    void extract_emptyErrorsArray_createsEmptyFanList() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        extractor.extract("devE", "Failed: {\"errors_object\":[]}", metricsScope, deviceResults);

        assertTrue(deviceResults.containsKey("devE"));
        assertTrue(deviceResults.get("devE").containsKey("Fan Errors"));
        assertTrue(deviceResults.get("devE").get("Fan Errors").isEmpty());
    }

    @Test
    void extract_missingErrorsObject_createsEmptyFanList() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        extractor.extract(
                "devF", "Failed: {\"message\":\"Fan issues found\"}", metricsScope, deviceResults);

        assertTrue(deviceResults.containsKey("devF"));
        assertTrue(deviceResults.get("devF").containsKey("Fan Errors"));
        assertTrue(deviceResults.get("devF").get("Fan Errors").isEmpty());
    }

    @Test
    void extract_failedPrefixWithoutJson_returnsEmptyFanList_noException() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        extractor.extract("devG", "Failed: ", metricsScope, deviceResults);

        assertTrue(deviceResults.containsKey("devG"));
        assertTrue(deviceResults.get("devG").containsKey("Fan Errors"));
        assertTrue(deviceResults.get("devG").get("Fan Errors").isEmpty());
        verify(metricsScope, never())
                .emit(eq(MetricNames.ProcessNcpResult.FanErrorFormatUnexpected), anyDouble());
    }

    @Test
    void extract_malformedJson_throwsRenderable_andEmitsMetric() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();

        RenderableException ex =
                assertThrows(
                        RenderableException.class,
                        () ->
                                extractor.extract(
                                        "devH", "Failed: not_json", metricsScope, deviceResults));

        assertTrue(ex.getMessage().contains("Fan Error in unexpected format"));
        verify(metricsScope, atLeastOnce())
                .emit(eq(MetricNames.ProcessNcpResult.FanErrorFormatUnexpected), anyDouble());
    }

    @Test
    void constructor_withNullMapper_usesDefault() {
        FanTestResultExtractor custom = new FanTestResultExtractor((ObjectMapper) null);
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        custom.extract("devI", null, metricsScope, deviceResults);

        assertTrue(deviceResults.containsKey("devI"));
        assertTrue(deviceResults.get("devI").containsKey("Fan Errors"));
        assertTrue(deviceResults.get("devI").get("Fan Errors").isEmpty());
    }

    @Test
    void constructor_withCustomMapper_usesProvided() {
        FanTestResultExtractor custom = new FanTestResultExtractor(new ObjectMapper());
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        custom.extract("devJ", null, metricsScope, deviceResults);

        assertTrue(deviceResults.containsKey("devJ"));
        assertTrue(deviceResults.get("devJ").containsKey("Fan Errors"));
        assertTrue(deviceResults.get("devJ").get("Fan Errors").isEmpty());
    }
}
