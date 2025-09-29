package com.oracle.pic.networking.lvv.service.resources;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.oracle.pic.networking.lvv.service.kiev.BlockDetails;
import com.oracle.pic.networking.lvv.service.kiev.ProjectItem;
import com.oracle.pic.networking.lvv.service.kiev.ValidationFailureResult;
import com.oracle.pic.networking.lvv.service.model.Project;
import com.oracle.pic.networking.lvv.service.model.ValidationFailureDisplayDTO;
import com.oracle.pic.networking.lvv.service.utils.GeneralUtils;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@ToString
public class ResourceModelTransformer {

    public Project toModel(ProjectItem project, List<BlockDetails> blockDetails) {

        List<String> blocks = new ArrayList<>();
        String building = "";

        for (BlockDetails blockDetail : blockDetails) {
            blocks.add(blockDetail.getBlock().getBlockNumber());
            building = blockDetail.getBlock().getBuilding();
        }

        String derivedRegion = GeneralUtils.getRegionFromBuilding(building);
        String region =
                project.getRegionName() != null && !project.getRegionName().isBlank()
                        ? project.getRegionName()
                        : derivedRegion;

        return Project.builder()
                .projectId(project.getProjectId())
                .vendorName(project.getVendorName())
                .blocks(blocks)
                .building(building)
                .region(region)
                .build();
    }

    public ValidationFailureDisplayDTO toModel(ValidationFailureResult result) {
        return ValidationFailureDisplayDTO.builder()
                .rackSerial(result.getRackSerial())
                .deviceARack(result.getDeviceARack())
                .deviceAName(result.getLinkSource().getDeviceAName())
                .deviceAPort(result.getLinkSource().getDeviceAPort())
                .deviceBRack(result.getDeviceBRack())
                .deviceBName(result.getDeviceBName())
                .deviceBPort(result.getDeviceBPort())
                .txPower(result.getTxPower())
                .rxPower(result.getRxPower())
                .lldpStatus(result.getLldpStatus().name())
                .deviceBRackExpected(result.getDeviceBRackExpected())
                .deviceBNameExpected(result.getDeviceBNameExpected())
                .deviceBPortExpected(result.getDeviceBPortExpected())
                .psuFailure(result.getPsuFailure())
                .build();
    }
}
