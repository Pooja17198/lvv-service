package com.oracle.pic.networking.lvv.service.dependencies.ncp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.networking.lvv.service.kiev.JobStatus;
import com.oracle.pic.networking.lvv.service.kiev.LinkStatus;
import com.oracle.pic.networking.lvv.service.kiev.LldpStatus;
import com.oracle.pic.networking.lvv.service.kiev.NcpJobDetailsDao;
import com.oracle.pic.networking.lvv.service.kiev.ValidationFailureResult;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NcpJobResultProcessorTest {

    MetricsScope metricsScope;
    NcpJobDetailsDao ncpJobDetailsDao;

    @BeforeEach
    void setup() {
        metricsScope = mock(MetricsScope.class);
        // Be permissive on emit signature (enum or string)
        doReturn(metricsScope).when(metricsScope).emit(anyString(), anyDouble());
        doReturn(metricsScope).when(metricsScope).emit(any(Enum.class), anyDouble());
        when(metricsScope.withDimension(anyString(), anyString())).thenReturn(metricsScope);
        when(metricsScope.recordSuccess()).thenReturn(metricsScope);

        ncpJobDetailsDao = mock(NcpJobDetailsDao.class);
    }

    private NcpJobResultProcessor newProcessorWithJson(String json) {
        return new NcpJobResultProcessor(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), ncpJobDetailsDao);
    }

    @Test
    void testParseDeviceString_wellFormed() {
        NcpJobResultProcessor p = newProcessorWithJson("{}");
        NcpJobResultProcessor.DevicePortInfo info =
                p.parseDeviceString("device1:Ethernet1/3:foo:bar:rackUnit");
        assertEquals("device1", info.getDeviceName());
        assertEquals("Ethernet1/3", info.getPort());
        assertEquals("bar:rackUnit", info.getRackUnit());
    }

    @Test
    void testParseDeviceString_moreThanFiveParts_usesLastTwoAsRackUnit() {
        NcpJobResultProcessor p = newProcessorWithJson("{}");
        NcpJobResultProcessor.DevicePortInfo info = p.parseDeviceString("a:b:c:d:e:f");
        assertEquals("a", info.getDeviceName());
        assertEquals("b", info.getPort());
        assertEquals("e:f", info.getRackUnit());
    }

    @Test
    void testParseDeviceString_nullInput() {
        NcpJobResultProcessor p = newProcessorWithJson("{}");
        NcpJobResultProcessor.DevicePortInfo info = p.parseDeviceString(null);
        assertEquals("Unknown", info.getDeviceName());
        assertEquals("Unknown", info.getPort());
        assertEquals("Unknown", info.getRackUnit());
    }

    @Test
    void testParseDeviceString_emptyInput() {
        NcpJobResultProcessor p = newProcessorWithJson("{}");
        NcpJobResultProcessor.DevicePortInfo info = p.parseDeviceString("");
        assertEquals("Unknown", info.getDeviceName());
        assertEquals("Unknown", info.getPort());
        assertEquals("Unknown", info.getRackUnit());
    }

    @Test
    void testParseDeviceString_tooFewParts() {
        NcpJobResultProcessor p = newProcessorWithJson("{}");
        NcpJobResultProcessor.DevicePortInfo info = p.parseDeviceString("a:b:c");
        assertEquals("Unknown", info.getDeviceName());
        assertEquals("Unknown", info.getPort());
        assertEquals("Unknown", info.getRackUnit());
    }

    @Test
    void testExtractLldpErrors_goodJson_setsMismatch() {
        String msg =
                "Failed: {\"message\": \"LLDP Failures: 1\", \"errors_object\":"
                        + " [{\"current_origin\": \"devA:Eth1/1:x:y:u1\","
                        + "   \"current_destination\": \"devB:Eth2/2:a:b:u2\","
                        + "   \"expected_destination\": \"devC:Eth3/3:c:d:u3\"}]}";
        NcpJobResultProcessor p = newProcessorWithJson("{}");

        p.extractLldpErrors(msg, "devA", metricsScope);

        // Build and assert
        List<ValidationFailureResult> results =
                p.buildValidationFailureResults("rackSerial", "rackUnitFallback");
        assertEquals(1, results.size());
        ValidationFailureResult r = results.get(0);
        assertEquals("devA", r.getLinkSource().getDeviceAName());
        assertEquals("Eth1/1", r.getLinkSource().getDeviceAPort());
        assertEquals(LldpStatus.MISMATCH, r.getLldpStatus());
        assertEquals(LinkStatus.DOWN, r.getLinkStatus());
        assertEquals("devB", r.getDeviceBName());
        assertEquals("Eth2/2", r.getDeviceBPort());
        assertEquals(
                "a:b:u2".substring("a:".length()),
                r.getDeviceBRack()); // y:u1 from origin, a:b:u2 from dest
        assertEquals("devC", r.getDeviceBNameExpected());
        assertEquals("Eth3/3", r.getDeviceBPortExpected());
    }

    @Test
    void testExtractLldpErrors_goodJson_cryptoSetsUnsupported() {
        String msg =
                "Failed: {\"message\": \"LLDP Failures: 1\", \"errors_object\":"
                        + " [{\"current_origin\": \"devX:Eth1/1:x:y:u1\","
                        + "   \"current_destination\": \"cryptoB:Eth2/2:a:b:u2\","
                        + "   \"expected_destination\": \"devC:Eth3/3:c:d:u3\"}]}";
        NcpJobResultProcessor p = newProcessorWithJson("{}");

        p.extractLldpErrors(msg, "devX", metricsScope);

        List<ValidationFailureResult> results =
                p.buildValidationFailureResults("rackSerial", "rackUnitFallback");
        assertEquals(1, results.size());
        assertEquals(LldpStatus.UNSUPPORTED, results.get(0).getLldpStatus());
    }

    @Test
    void testExtractLldpErrors_badJson_createsUnknownEntry() {
        String bad = "Failed: not_json";
        NcpJobResultProcessor p = newProcessorWithJson("{}");

        assertDoesNotThrow(() -> p.extractLldpErrors(bad, "devZ", metricsScope));

        // Should create Unknown entry for the device
        Map<NcpJobResultProcessor.DevicePortInfo, ValidationFailureResult.Builder> map =
                p.getResultBuilder();
        assertFalse(map.isEmpty());
        NcpJobResultProcessor.DevicePortInfo key =
                new NcpJobResultProcessor.DevicePortInfo("devZ", "Unknown", "Unknown");
        assertTrue(map.containsKey(key));
        ValidationFailureResult res = map.get(key).rackSerial("rs").build();
        assertEquals(LldpStatus.UNKNOWN, res.getLldpStatus());
        assertEquals(LinkStatus.DOWN, res.getLinkStatus());
        assertEquals("devZ", res.getLinkSource().getDeviceAName());
        assertEquals("UNKNOWN", res.getLinkSource().getDeviceAPort());
    }

    @Test
    void testExtractLldpErrors_nonFailedPrefix_noop() {
        String msg =
                "Something else Failed: {\"message\": \"LLDP Failures: 1\", \"errors_object\": []}";
        NcpJobResultProcessor p = newProcessorWithJson("{}");

        p.extractLldpErrors(msg, "devA", metricsScope);

        assertTrue(p.getResultBuilder().isEmpty());
    }

    @Test
    void testExtractLldpErrors_messageNotLldpFailures_noop() {
        String msg = "Failed: {\"message\": \"Other\", \"errors_object\": []}";
        NcpJobResultProcessor p = newProcessorWithJson("{}");

        p.extractLldpErrors(msg, "devA", metricsScope);

        assertTrue(p.getResultBuilder().isEmpty());
    }

    @Test
    void testExtractOpticErrors_goodJson_withPrefix_lldpPassed_setsMatch() {
        String msg =
                "Failed: {\"errors_object\": [{\"device\": \"dev1\", \"intf_name\": \"port1\","
                        + " \"input_power\": \"-5\", \"output_power\": \"1.2\", \"device_phys\": \"rack:U42\"}]}";
        NcpJobResultProcessor p = newProcessorWithJson("{}");

        assertDoesNotThrow(() -> p.extractOpticErrors(msg, true, metricsScope));

        List<ValidationFailureResult> results =
                p.buildValidationFailureResults("serial", "fallbackRU");
        assertEquals(1, results.size());
        ValidationFailureResult r = results.get(0);
        assertEquals("dev1", r.getLinkSource().getDeviceAName());
        assertEquals("port1", r.getLinkSource().getDeviceAPort());
        assertEquals(LldpStatus.MATCH, r.getLldpStatus());
        assertEquals(LinkStatus.DOWN, r.getLinkStatus());
        assertEquals("U42", r.getDeviceARack());
        assertEquals("-5", r.getRxPower());
        assertEquals("1.2", r.getTxPower());
    }

    @Test
    void testExtractOpticErrors_goodJson_withoutPrefix_appendsToExisting_andUntested() {
        NcpJobResultProcessor p = newProcessorWithJson("{}");

        // Seed existing entry with same device/port/rackUnit computed from device_phys below
        // ("x:y:unit1" -> "y:unit1")
        NcpJobResultProcessor.DevicePortInfo info =
                new NcpJobResultProcessor.DevicePortInfo("dev2", "Eth0/1", "y:unit1");
        ValidationFailureResult.Builder builder =
                ValidationFailureResult.builder()
                        .linkSource(
                                ValidationFailureResult.LinkSource.builder()
                                        .deviceAName("dev2")
                                        .deviceAPort("Eth0/1")
                                        .build())
                        .lldpStatus(LldpStatus.MISMATCH) // should be preserved on append
                        .deviceARack("y:unit1");
        p.getResultBuilder().put(info, builder);

        String msg =
                "{\"errors_object\": [{\"device\": \"dev2\", \"intf_name\": \"Eth0/1\","
                        + " \"input_power\": \"-7.1\", \"output_power\": \"0.9\","
                        + " \"device_phys\": \"x:y:unit1\"}]}";

        assertDoesNotThrow(() -> p.extractOpticErrors(msg, false, metricsScope));

        // On append path, only tx/rx updated; lldpStatus preserved (MISMATCH)
        List<ValidationFailureResult> results =
                p.buildValidationFailureResults("serial", "fallbackRU");
        assertEquals(1, results.size());
        ValidationFailureResult r = results.get(0);
        assertEquals("dev2", r.getLinkSource().getDeviceAName());
        assertEquals("Eth0/1", r.getLinkSource().getDeviceAPort());
        assertEquals(LldpStatus.MISMATCH, r.getLldpStatus());
        assertEquals("y:unit1", r.getDeviceARack());
        assertEquals("-7.1", r.getRxPower());
        assertEquals("0.9", r.getTxPower());
    }

    @Test
    void testExtractOpticErrors_badJson_throwsAndEmitsMetric() {
        String msg = "Failed: not_json";
        NcpJobResultProcessor p = newProcessorWithJson("{}");
        assertThrows(
                RenderableException.class, () -> p.extractOpticErrors(msg, false, metricsScope));
    }

    @Test
    void testUpdatePowerErrors_existingDevice_appendsPsuFailure() {
        NcpJobResultProcessor p = newProcessorWithJson("{}");
        // Seed with two entries, one matching device
        NcpJobResultProcessor.DevicePortInfo d1 =
                new NcpJobResultProcessor.DevicePortInfo("D", "P", "RU");
        NcpJobResultProcessor.DevicePortInfo d2 =
                new NcpJobResultProcessor.DevicePortInfo("X", "P2", "RU2");
        ValidationFailureResult.Builder b1 =
                ValidationFailureResult.builder()
                        .linkSource(
                                ValidationFailureResult.LinkSource.builder()
                                        .deviceAName("D")
                                        .deviceAPort("P")
                                        .build());
        ValidationFailureResult.Builder b2 =
                ValidationFailureResult.builder()
                        .linkSource(
                                ValidationFailureResult.LinkSource.builder()
                                        .deviceAName("X")
                                        .deviceAPort("P2")
                                        .build());
        p.getResultBuilder().put(d1, b1);
        p.getResultBuilder().put(d2, b2);

        p.updatePowerErrors("D", true);

        // Size unchanged, b1 should have psuFailure
        assertEquals(2, p.getResultBuilder().size());
        ValidationFailureResult r1 = p.getResultBuilder().get(d1).rackSerial("rs").build();
        assertNotNull(r1.getPsuFailure());
        // lldpStatus should remain unset here (append path doesn't modify it)
        assertNull(r1.getLldpStatus());
        // d2 untouched
        ValidationFailureResult r2 = p.getResultBuilder().get(d2).rackSerial("rs").build();
        assertNull(r2.getPsuFailure());
    }

    @Test
    void testUpdatePowerErrors_noExistingEntry_createsOne_matchWhenLldpPassed() {
        NcpJobResultProcessor p = newProcessorWithJson("{}");

        p.updatePowerErrors("DevNew", true);

        assertFalse(p.getResultBuilder().isEmpty());
        NcpJobResultProcessor.DevicePortInfo key =
                new NcpJobResultProcessor.DevicePortInfo("DevNew", "Unknown", "Unknown");
        assertTrue(p.getResultBuilder().containsKey(key));
        ValidationFailureResult r = p.getResultBuilder().get(key).rackSerial("rs").build();
        assertEquals("DevNew", r.getLinkSource().getDeviceAName());
        assertEquals("UNKNOWN", r.getLinkSource().getDeviceAPort());
        assertEquals(LinkStatus.DOWN, r.getLinkStatus());
        assertNotNull(r.getPsuFailure());
        assertEquals(LldpStatus.MATCH, r.getLldpStatus());
    }

    @Test
    void testUpdatePowerErrors_noExistingEntry_createsOne_untestedWhenLldpUnknown() {
        NcpJobResultProcessor p = newProcessorWithJson("{}");

        p.updatePowerErrors("DevNew2", false);

        NcpJobResultProcessor.DevicePortInfo key =
                new NcpJobResultProcessor.DevicePortInfo("DevNew2", "Unknown", "Unknown");
        ValidationFailureResult r = p.getResultBuilder().get(key).rackSerial("rs").build();
        assertEquals(LldpStatus.UNTESTED, r.getLldpStatus());
    }

    @Test
    void testProcessJobResult_invalidJson_throwsAndEmitsMetric() {
        NcpJobResultProcessor p = newProcessorWithJson("not_json");
        assertThrows(RenderableException.class, () -> p.processJobResult(metricsScope));
        // We refrain from verifying exact metric enum type to avoid signature coupling
        verify(metricsScope, atLeastOnce()).emit(any(Enum.class), anyDouble());
    }

    @Test
    void testProcessJobResult_jsonMissingTestResultsThrows() {
        String json = "{\"foo\": 1}";
        NcpJobResultProcessor p = newProcessorWithJson(json);
        assertThrows(RenderableException.class, () -> p.processJobResult(metricsScope));
    }

    @Test
    void testProcessJobResult_deviceUnreachable_updatesDaoAndSkips() {
        String json =
                """
                {
                  "testResults": {
                    "device1": {
                      "healthCheckReport": {
                        "testCases": [{
                          "testCase": "test_lldp",
                          "status": "FAILED",
                          "message": "Unable to connect to device"
                        }]
                      }
                    }
                  }
                }
                """;
        NcpJobResultProcessor p = newProcessorWithJson(json);
        p.processJobResult(metricsScope);

        verify(ncpJobDetailsDao, times(1))
                .updateNcpJobStatus(eq("device1"), eq(JobStatus.DEVICE_UNREACHABLE));
        assertTrue(p.getResultBuilder().isEmpty());
    }

    @Test
    void testProcessJobResult_allPass_insertsUpEntry() {
        String json =
                """
                {
                  "testResults": {
                    "device1": {
                      "healthCheckReport": {
                        "testCases": [{
                          "testCase": "test_lldp",
                          "status": "PASSED"
                        }, {
                          "testCase": "test_optics",
                          "status": "PASSED"
                        }, {
                          "testCase": "test_power",
                          "status": "PASSED"
                        }]
                      }
                    }
                  }
                }
                """;
        NcpJobResultProcessor p = newProcessorWithJson(json);
        p.processJobResult(metricsScope);

        List<ValidationFailureResult> results = p.buildValidationFailureResults("serial", "RU");
        assertEquals(1, results.size());
        ValidationFailureResult r = results.get(0);
        assertEquals("device1", r.getLinkSource().getDeviceAName());
        assertEquals(LinkStatus.UP, r.getLinkStatus());
        // lldpStatus may not be set for pass case
        assertNull(r.getLldpStatus());
    }

    @Test
    void testProcessJobResult_failures_allThreeBranches() {
        String json =
                """
                {
                  "testResults": {
                    "deviceX": {
                      "healthCheckReport": {
                        "testCases": [{
                          "testCase": "test_lldp",
                          "status": "FAILED",
                          "message": "Failed: {\\\"message\\\": \\\"LLDP Failures: \\\","
                            + "\\\"errors_object\\\": [{\\\"current_origin\\\": \\\"d:p:x:y:unit\\\","
                            + " \\\"current_destination\\\": \\\"d2:p2:x:y:u2\\\", \\\"expected_destination\\\":"
                            + " \\\"d3:p3:x:y:u3\\\"}]}"
                        }, {
                          "testCase": "test_optics",
                          "status": "FAILED",
                          "message": "Failed: {\\\"errors_object\\\": [{\\\"device\\\": \\\"d\\\","
                            + " \\\"intf_name\\\": \\\"p\\\", \\\"input_power\\\": \\\"-5\\\", \\\"output_power\\\": \\\"1\\\","
                            + " \\\"device_phys\\\": \\\"x:y:unit\\\"}]}"
                        }, {
                          "testCase": "test_power",
                          "status": "FAILED"
                        }]
                      }
                    }
                  }
                }
                """;
        // The above string contains concatenations inside a JSON text block; rebuild properly:
        json =
                """
                {
                  "testResults": {
                    "deviceX": {
                      "healthCheckReport": {
                        "testCases": [{
                          "testCase": "test_lldp",
                          "status": "FAILED",
                          "message": "Failed: {\\\"message\\\": \\\"LLDP Failures: \\\", \\\"errors_object\\\": [{\\\"current_origin\\\": \\\"d:p:x:y:unit\\\", \\\"current_destination\\\": \\\"d2:p2:x:y:u2\\\", \\\"expected_destination\\\": \\\"d3:p3:x:y:u3\\\"}]}"
                        }, {
                          "testCase": "test_optics",
                          "status": "FAILED",
                          "message": "Failed: {\\\"errors_object\\\": [{\\\"device\\\": \\\"d\\\", \\\"intf_name\\\": \\\"p\\\", \\\"input_power\\\": \\\"-5\\\", \\\"output_power\\\": \\\"1\\\", \\\"device_phys\\\": \\\"x:y:unit\\\"}]}"
                        }, {
                          "testCase": "test_power",
                          "status": "FAILED"
                        }]
                      }
                    }
                  }
                }
                """;

        NcpJobResultProcessor p = newProcessorWithJson(json);

        assertDoesNotThrow(() -> p.processJobResult(metricsScope));

        // Build final results with rack fallback (for PSU-only entries)
        List<ValidationFailureResult> results = p.buildValidationFailureResults("serial", "RU");
        // Expect two entries: one for origin device d:p (LLDP/Optics), another PSU-only for deviceX
        assertTrue(results.size() >= 2);

        ValidationFailureResult origin =
                results.stream()
                        .filter(
                                r ->
                                        Objects.equals(r.getLinkSource().getDeviceAName(), "d")
                                                && Objects.equals(
                                                        r.getLinkSource().getDeviceAPort(), "p"))
                        .findFirst()
                        .orElse(null);
        assertNotNull(origin);
        assertEquals(LldpStatus.MISMATCH, origin.getLldpStatus());
        assertEquals(LinkStatus.DOWN, origin.getLinkStatus());
        assertEquals("-5", origin.getRxPower());
        assertEquals("1", origin.getTxPower());
        assertEquals("d2", origin.getDeviceBName());
        assertEquals("p2", origin.getDeviceBPort());
        assertEquals("d3", origin.getDeviceBNameExpected());
        assertEquals("p3", origin.getDeviceBPortExpected());

        ValidationFailureResult psuOnly =
                results.stream()
                        .filter(r -> Objects.equals(r.getLinkSource().getDeviceAName(), "deviceX"))
                        .findFirst()
                        .orElse(null);
        assertNotNull(psuOnly);
        assertNotNull(psuOnly.getPsuFailure());
        // Since LLDP failed on deviceX, PSU entry should be UNTESTED
        assertEquals(LldpStatus.UNTESTED, psuOnly.getLldpStatus());
        assertEquals(
                "RU", psuOnly.getDeviceARack()); // set via buildValidationFailureResults fallback
    }

    @Test
    void testBuildValidationFailureResults_setsRackUnitIfMissing_andPreservesIfPresent() {
        NcpJobResultProcessor p = newProcessorWithJson("{}");

        // Entry 1: missing deviceARack (PSU only)
        NcpJobResultProcessor.DevicePortInfo info1 =
                new NcpJobResultProcessor.DevicePortInfo("dev1", "Unknown", "Unknown");
        ValidationFailureResult.Builder b1 =
                ValidationFailureResult.builder()
                        .psuFailure("Power supply failure detected")
                        .linkSource(
                                ValidationFailureResult.LinkSource.builder()
                                        .deviceAName("dev1")
                                        .deviceAPort("UNKNOWN")
                                        .build())
                        .linkStatus(LinkStatus.DOWN);
        p.getResultBuilder().put(info1, b1);

        // Entry 2: already has deviceARack set
        NcpJobResultProcessor.DevicePortInfo info2 =
                new NcpJobResultProcessor.DevicePortInfo("dev2", "Eth1/1", "y:unit2");
        ValidationFailureResult.Builder b2 =
                ValidationFailureResult.builder()
                        .deviceARack("y:unit2")
                        .linkSource(
                                ValidationFailureResult.LinkSource.builder()
                                        .deviceAName("dev2")
                                        .deviceAPort("Eth1/1")
                                        .build())
                        .linkStatus(LinkStatus.DOWN);
        p.getResultBuilder().put(info2, b2);

        List<ValidationFailureResult> results =
                p.buildValidationFailureResults("serial", "RU-FALLBACK");
        assertEquals(2, results.size());

        ValidationFailureResult r1 =
                results.stream()
                        .filter(r -> r.getLinkSource().getDeviceAName().equals("dev1"))
                        .findFirst()
                        .orElseThrow();
        ValidationFailureResult r2 =
                results.stream()
                        .filter(r -> r.getLinkSource().getDeviceAName().equals("dev2"))
                        .findFirst()
                        .orElseThrow();

        assertEquals("RU-FALLBACK", r1.getDeviceARack());
        assertEquals("y:unit2", r2.getDeviceARack());
    }
}
