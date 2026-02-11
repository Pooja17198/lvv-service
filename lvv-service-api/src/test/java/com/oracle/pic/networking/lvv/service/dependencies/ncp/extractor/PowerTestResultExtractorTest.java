package com.oracle.pic.networking.lvv.service.dependencies.ncp.extractor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.oracle.pic.commons.metrics.MetricsScope;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PowerTestResultExtractorTest {

    private PowerTestResultExtractor extractor;
    private MetricsScope metricsScope;

    @BeforeEach
    void setup() {
        extractor = new PowerTestResultExtractor();
        metricsScope = mock(MetricsScope.class);
        // No emissions expected for power extractor, but stubbing avoids NPE if behavior changes
        when(metricsScope.emit(any(Enum.class), anyDouble())).thenReturn(metricsScope);
    }

    @Test
    void testName_returnsTestPower() {
        assertEquals("test_power", extractor.testName());
    }

    @Test
    void extract_addsPowerErrorRow_withDeviceId() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        extractor.extract("device1", "any-message", metricsScope, deviceResults);

        assertTrue(deviceResults.containsKey("device1"));
        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("device1");
        assertTrue(perDevice.containsKey("Power Errors"));
        List<Map<String, String>> rows = perDevice.get("Power Errors");
        assertEquals(1, rows.size());
        Map<String, String> row = rows.get(0);
        assertEquals("device1", row.get("Device A Name"));
    }

    @Test
    void extract_nullMessage_stillAddsRow() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        extractor.extract("deviceX", null, metricsScope, deviceResults);

        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("deviceX");
        List<Map<String, String>> rows = perDevice.get("Power Errors");
        assertEquals(1, rows.size());
        assertEquals("deviceX", rows.get(0).get("Device A Name"));
    }

    @Test
    void extract_appendsToExistingList_whenCalledMultipleTimes() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        extractor.extract("dev", null, metricsScope, deviceResults);
        extractor.extract("dev", "ignored", metricsScope, deviceResults);

        List<Map<String, String>> rows = deviceResults.get("dev").get("Power Errors");
        assertEquals(2, rows.size());
        assertEquals("dev", rows.get(0).get("Device A Name"));
        assertEquals("dev", rows.get(1).get("Device A Name"));
    }

    @Test
    void extract_createsCategoryWhenAbsent_butPreservesOtherCategories() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        Map<String, List<Map<String, String>>> existing = new HashMap<>();
        existing.put("LLDP Errors", new ArrayList<>(List.of(Map.of("foo", "bar"))));
        deviceResults.put("device1", existing);

        extractor.extract("device1", null, metricsScope, deviceResults);

        Map<String, List<Map<String, String>>> perDevice = deviceResults.get("device1");
        assertTrue(perDevice.containsKey("LLDP Errors"));
        assertEquals(1, perDevice.get("LLDP Errors").size());

        assertTrue(perDevice.containsKey("Power Errors"));
        assertEquals(1, perDevice.get("Power Errors").size());
        assertEquals("device1", perDevice.get("Power Errors").get(0).get("Device A Name"));
    }

    @Test
    void extract_existingPowerList_appends() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        Map<String, List<Map<String, String>>> perDevice = new HashMap<>();
        perDevice.put(
                "Power Errors",
                new ArrayList<>(
                        List.of(Map.of("Device A Name", "pre1"), Map.of("Device A Name", "pre2"))));
        deviceResults.put("deviceA", perDevice);

        extractor.extract("deviceA", null, metricsScope, deviceResults);

        List<Map<String, String>> rows = deviceResults.get("deviceA").get("Power Errors");
        assertEquals(3, rows.size());
        assertEquals("pre1", rows.get(0).get("Device A Name"));
        assertEquals("pre2", rows.get(1).get("Device A Name"));
        assertEquals("deviceA", rows.get(2).get("Device A Name"));
    }

    @Test
    void extract_separateDeviceIds_isolatedResults() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        extractor.extract("dev1", null, metricsScope, deviceResults);
        extractor.extract("dev2", null, metricsScope, deviceResults);

        assertTrue(deviceResults.containsKey("dev1"));
        assertTrue(deviceResults.containsKey("dev2"));

        assertEquals(1, deviceResults.get("dev1").get("Power Errors").size());
        assertEquals(1, deviceResults.get("dev2").get("Power Errors").size());
        assertEquals(
                "dev1", deviceResults.get("dev1").get("Power Errors").get(0).get("Device A Name"));
        assertEquals(
                "dev2", deviceResults.get("dev2").get("Power Errors").get(0).get("Device A Name"));
    }

    @Test
    void extract_doesNotUseMetricsScope_noInteractions() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        extractor.extract("dev", "ignored", metricsScope, deviceResults);
        verifyNoInteractions(metricsScope);
    }

    @Test
    void extract_nullDeviceId_throwsNullPointerException() {
        Map<String, Map<String, List<Map<String, String>>>> deviceResults = new HashMap<>();
        // Map.of does not allow null values, so a null deviceId should throw NPE
        assertThrows(
                NullPointerException.class,
                () -> extractor.extract(null, null, metricsScope, deviceResults));
    }
}
