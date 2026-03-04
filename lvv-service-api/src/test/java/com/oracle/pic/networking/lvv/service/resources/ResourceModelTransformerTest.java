package com.oracle.pic.networking.lvv.service.resources;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.oracle.pic.networking.autonet.plan.service.model.Device;
import com.oracle.pic.networking.lvv.service.kiev.BlockDetails;
import com.oracle.pic.networking.lvv.service.kiev.JobStatus;
import com.oracle.pic.networking.lvv.service.kiev.ProjectItem;
import com.oracle.pic.networking.lvv.service.model.DeviceDetails;
import com.oracle.pic.networking.lvv.service.model.Project;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mockito;

class ResourceModelTransformerTest {

    private final ResourceModelTransformer transformer = new ResourceModelTransformer();

    @Test
    void testToModelProject() {
        // Build a sample blockDetails list
        BlockDetails.Block block1 =
                BlockDetails.Block.builder().blockNumber("101").building("Alpha").build();
        BlockDetails.Block block2 =
                BlockDetails.Block.builder().blockNumber("102").building("Alpha").build();
        BlockDetails bd1 = BlockDetails.builder().block(block1).projectId("proj1").build();
        BlockDetails bd2 = BlockDetails.builder().block(block2).projectId("proj2").build();
        List<BlockDetails> blockDetails = List.of(bd1, bd2);

        ProjectItem projectItem =
                ProjectItem.builder().projectId("P1").vendorName("VendorX").build();

        Project result = transformer.toModel(projectItem, blockDetails);

        assertEquals("P1", result.getProjectId());
        assertEquals("VendorX", result.getVendorName());
        assertEquals(List.of("101", "102"), result.getBlocks());
        assertEquals("Alpha", result.getBuilding());
    }

    @Test
    void testToModelDeviceDetails_setsEligibilityAndReason() {
        Device eligible = mockDevice("dev-eligible", "10", true, "deployed");
        Device ineligible = mockDevice("dev-ineligible", "8", false, "deployed");

        Map<String, JobStatus> jobStatuses = new HashMap<>();
        jobStatuses.put("dev-eligible", JobStatus.COMPLETED);
        jobStatuses.put("dev-ineligible", JobStatus.NOT_TRIGGERED);

        List<DeviceDetails> result =
                transformer.toModel(jobStatuses, List.of(eligible, ineligible));

        assertEquals(2, result.size());

        DeviceDetails first = result.get(0);
        assertEquals("dev-eligible", first.getDeviceName());
        assertEquals("COMPLETED", first.getJobStatus());
        assertEquals(10, first.getElevation());
        assertTrue(readValidationEligible(first));
        assertNull(readValidationEligibilityReason(first));

        DeviceDetails second = result.get(1);
        assertEquals("dev-ineligible", second.getDeviceName());
        assertEquals("NOT_TRIGGERED", second.getJobStatus());
        assertEquals(8, second.getElevation());
        assertFalse(readValidationEligible(second));
        assertEquals(
                "Device is not in monitored and deployed state.",
                readValidationEligibilityReason(second));
    }

    @Test
    void testToModelDeviceDetails_missingStatusAndInvalidElevation_defaultsGracefully() {
        Device unknown = mockDevice("dev-unknown", "invalid", false, null);
        when(unknown.getLocation()).thenReturn(null);

        List<DeviceDetails> result = transformer.toModel(new HashMap<>(), List.of(unknown));

        assertEquals(1, result.size());
        DeviceDetails only = result.get(0);
        assertEquals("dev-unknown", only.getDeviceName());
        assertEquals("NOT_TRIGGERED", only.getJobStatus());
        assertEquals(-1, only.getElevation());
        assertFalse(readValidationEligible(only));
        assertEquals(
                "Device is not in monitored and deployed state.",
                readValidationEligibilityReason(only));
    }

    private Device mockDevice(
            String name, String elevation, boolean monitored, String deviceStateValue) {
        Device device = Mockito.mock(Device.class, Answers.RETURNS_DEEP_STUBS);
        when(device.getName()).thenReturn(name);
        when(device.getLocation().getElevation()).thenReturn(elevation);

        if (monitored) {
            when(device.getConfigAttributes())
                    .thenReturn(Map.of("monitoring.interfaces", List.of("Eth0/1")));
        } else {
            when(device.getConfigAttributes()).thenReturn(Map.of());
        }

        if (deviceStateValue == null) {
            when(device.getState()).thenReturn(Map.of());
        } else {
            when(device.getState())
                    .thenReturn(Map.of("conf", Map.of("device.state", deviceStateValue)));
        }

        return device;
    }

    private boolean readValidationEligible(DeviceDetails details) {
        try {
            return (boolean) details.getClass().getMethod("isValidationEligible").invoke(details);
        } catch (ReflectiveOperationException ignored) {
            try {
                return (boolean)
                        details.getClass().getMethod("getValidationEligible").invoke(details);
            } catch (ReflectiveOperationException ex) {
                fail("Unable to read validationEligible from DeviceDetails model");
                return false;
            }
        }
    }

    private String readValidationEligibilityReason(DeviceDetails details) {
        try {
            return (String)
                    details.getClass().getMethod("getValidationEligibilityReason").invoke(details);
        } catch (ReflectiveOperationException ex) {
            fail("Unable to read validationEligibilityReason from DeviceDetails model");
            return null;
        }
    }

    //    @Test
    //    void testToModelCsvFriendlyValidationFailureDisplayDTO() {
    //        // Create the necessary nested mock data
    //        LinkSource linkSource =
    //                LinkSource.builder().deviceAName("devA").deviceAPort("portA").build();
    //
    //        ValidationFailureResult result =
    //                ValidationFailureResult.builder()
    //                        .rackSerial("RS1")
    //                        .deviceARack("rackA:A")
    //                        .linkSource(linkSource)
    //                        .deviceBRack("rackB:B")
    //                        .deviceBName("devB")
    //                        .deviceBPort("portB")
    //                        .txPower("1.2")
    //                        .rxPower("2.3")
    //                        .lldpStatus(LldpStatus.MISMATCH)
    //                        .deviceBRackExpected("rackB-exp:B")
    //                        .deviceBNameExpected("devB-exp")
    //                        .deviceBPortExpected("portB-exp")
    //                        .psuFailure("failure")
    //                        .build();
    //
    //        ValidationFailureDisplayDTO dto = transformer.toModel(result, true);
    //
    //        assertEquals("RS1", dto.getRackSerial());
    //        assertEquals("rackA - A", dto.getDeviceARack());
    //        assertEquals("devA", dto.getDeviceAName());
    //        assertEquals("portA", dto.getDeviceAPort());
    //        assertEquals("rackB - B", dto.getDeviceBRack());
    //        assertEquals("devB", dto.getDeviceBName());
    //        assertEquals("portB", dto.getDeviceBPort());
    //        assertEquals("1.2", dto.getTxPower());
    //        assertEquals("2.3", dto.getRxPower());
    //        assertEquals("MISMATCH", dto.getLldpStatus());
    //        assertEquals("rackB-exp - B", dto.getDeviceBRackExpected());
    //        assertEquals("devB-exp", dto.getDeviceBNameExpected());
    //        assertEquals("portB-exp", dto.getDeviceBPortExpected());
    //        assertEquals("failure", dto.getPsuFailure());
    //    }
}
