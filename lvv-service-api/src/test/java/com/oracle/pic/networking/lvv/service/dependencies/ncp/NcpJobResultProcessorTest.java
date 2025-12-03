package com.oracle.pic.networking.lvv.service.dependencies.ncp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.networking.lvv.service.kiev.ValidationFailureResult;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NcpJobResultProcessorTest {

    MetricsScope metricsScope;

    @BeforeEach
    void setup() {
        metricsScope = mock(MetricsScope.class);
        when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);
        when(metricsScope.withDimension(anyString(), anyString())).thenReturn(metricsScope);
        when(metricsScope.recordSuccess()).thenReturn(metricsScope);
    }

    @Test
    void testParseDeviceString_wellFormed() {
        NcpJobResultProcessor p =
                new NcpJobResultProcessor(new ByteArrayInputStream("{}".getBytes()));
        NcpJobResultProcessor.DevicePortInfo info =
                p.parseDeviceString("device1:Ethernet1/3:foo:bar:rackUnit");
        assertEquals("device1", info.deviceName);
        assertEquals("Ethernet1/3", info.port);
        assertEquals("bar:rackUnit", info.rackUnit);
    }

    @Test
    void testParseDeviceString_malformed() {
        NcpJobResultProcessor p =
                new NcpJobResultProcessor(new ByteArrayInputStream("{}".getBytes()));
        NcpJobResultProcessor.DevicePortInfo info = p.parseDeviceString(null);
        assertEquals("Unknown", info.deviceName);
    }

    @Test
    void testExtractLldpErrors_goodJson() {
        String msg =
                "Failed: {\"message\": \"LLDP Failures: x\", \"errors_object\": [{\"current_origin\": \"devA:portA:x:y:unitA\", \"current_destination\": \"devB:portB:x:y:unitB\", \"expected_destination\": \"devC:portC:x:y:unitC\"}]}";
        NcpJobResultProcessor p =
                new NcpJobResultProcessor(new ByteArrayInputStream("{}".getBytes()));
        p.extractLldpErrors(msg, metricsScope);
        assertFalse(p.getResultBuilder().isEmpty());
    }

    @Test
    void testExtractLldpErrors_formatError() {
        String badMsg = "Failed: not_json";
        NcpJobResultProcessor p =
                new NcpJobResultProcessor(new ByteArrayInputStream("{}".getBytes()));
        assertThrows(RenderableException.class, () -> p.extractLldpErrors(badMsg, metricsScope));
    }

    @Test
    void testExtractOpticErrors_goodJson() {
        String msg =
                "Failed: {\"errors_object\": [{\"device\": \"dev1\", \"intf_name\": \"port1\", \"input_power\": \"-5\", \"output_power\": \"1.2\", \"device_phys\": \"x:y:unit1\"}]}";
        NcpJobResultProcessor p =
                new NcpJobResultProcessor(new ByteArrayInputStream("{}".getBytes()));
        assertDoesNotThrow(() -> p.extractOpticErrors(msg, true, metricsScope));
    }

    @Test
    void testExtractOpticErrors_badJson() {
        String msg = "Failed: not_json";
        NcpJobResultProcessor p =
                new NcpJobResultProcessor(new ByteArrayInputStream("{}".getBytes()));
        assertThrows(
                RenderableException.class, () -> p.extractOpticErrors(msg, false, metricsScope));
    }

    @Test
    void testUpdatePowerErrors_existingDevice() {
        NcpJobResultProcessor p =
                new NcpJobResultProcessor(new ByteArrayInputStream("{}".getBytes()));
        // Seed resultBuilder with device
        NcpJobResultProcessor.DevicePortInfo info =
                new NcpJobResultProcessor.DevicePortInfo("D", "P", "U");
        ValidationFailureResult.Builder builder = ValidationFailureResult.builder();
        p.getResultBuilder().put(info, builder);
        p.updatePowerErrors("D", true);

        assertNotNull(p.getResultBuilder().get(info));
    }

    @Test
    void testUpdatePowerErrors_noExistingEntry_createsOne() {
        NcpJobResultProcessor p =
                new NcpJobResultProcessor(new ByteArrayInputStream("{}".getBytes()));
        p.updatePowerErrors("DNE", false);
        assertFalse(p.getResultBuilder().isEmpty());
    }

    @Test
    void testProcessJobResult_jsonMissingTestResultsThrows() {
        String json = "{\"foo\": 1}";
        NcpJobResultProcessor p =
                new NcpJobResultProcessor(
                        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        assertThrows(RenderableException.class, () -> p.processJobResult(metricsScope));
    }

    @Test
    void testProcessJobResult_goodJsonEmitsEvents() {
        String json =
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
        NcpJobResultProcessor p =
                new NcpJobResultProcessor(
                        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        assertDoesNotThrow(() -> p.processJobResult(metricsScope));
        assertFalse(p.getResultBuilder().isEmpty());
    }

    @Test
    void testBuildValidationFailureResults_setsRackUnitIfMissing() {
        NcpJobResultProcessor p =
                new NcpJobResultProcessor(new ByteArrayInputStream("{}".getBytes()));
        NcpJobResultProcessor.DevicePortInfo info =
                new NcpJobResultProcessor.DevicePortInfo("dev1", "port1", "unitX");
        ValidationFailureResult.LinkSource linkSource =
                ValidationFailureResult.LinkSource.builder().build();
        ValidationFailureResult.Builder builder =
                ValidationFailureResult.builder().rackSerial("serial").linkSource(linkSource);
        p.getResultBuilder().put(info, builder);
        List<ValidationFailureResult> results = p.buildValidationFailureResults("serial", "unit1");
        assertFalse(results.isEmpty());
    }
}
