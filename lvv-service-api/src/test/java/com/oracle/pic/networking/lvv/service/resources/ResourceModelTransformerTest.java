package com.oracle.pic.networking.lvv.service.resources;

import static org.junit.jupiter.api.Assertions.*;

import com.oracle.pic.networking.lvv.service.kiev.BlockDetails;
import com.oracle.pic.networking.lvv.service.kiev.ProjectItem;
import com.oracle.pic.networking.lvv.service.model.Project;
import java.util.List;
import org.junit.jupiter.api.Test;

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
