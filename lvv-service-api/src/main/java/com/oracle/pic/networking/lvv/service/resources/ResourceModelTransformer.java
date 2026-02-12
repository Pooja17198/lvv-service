package com.oracle.pic.networking.lvv.service.resources;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.oracle.pic.commons.util.Region;
import com.oracle.pic.networking.autonet.plan.service.model.Device;
import com.oracle.pic.networking.lvv.service.dependencies.jira.JiraTicket;
import com.oracle.pic.networking.lvv.service.dependencies.storekeeper.Rack;
import com.oracle.pic.networking.lvv.service.kiev.BlockDetails;
import com.oracle.pic.networking.lvv.service.kiev.JobStatus;
import com.oracle.pic.networking.lvv.service.kiev.ProjectItem;
import com.oracle.pic.networking.lvv.service.model.DeviceDetails;
import com.oracle.pic.networking.lvv.service.model.DeviceValidationStatus;
import com.oracle.pic.networking.lvv.service.model.Project;
import com.oracle.pic.networking.lvv.service.model.ProjectRack;
import com.oracle.pic.networking.lvv.service.model.RegionObject;
import com.oracle.pic.networking.lvv.service.utils.GeneralUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
                .createdBy(project.getCreatedBy())
                .cmLink(project.getCmLink())
                .blocks(blocks)
                .building(building)
                .region(region)
                .build();
    }

    public List<RegionObject> toModel(Region[] regions) {
        if (regions == null) {
            return new ArrayList<>();
        }
        List<RegionObject> regionsList = new ArrayList<>();
        for (Region region : regions) {

            regionsList.add(
                    RegionObject.builder()
                            .name(region.getPublicRegionName())
                            .airportCode(region.getAirportCode())
                            .build());
        }
        return regionsList;
    }

    public List<DeviceDetails> toModel(Map<String, JobStatus> jobStatus, List<Device> devices) {

        List<DeviceDetails> deviceValidationStatuses = new ArrayList<>();

        for (Device device : devices) {
            DeviceDetails deviceDetails =
                    DeviceDetails.builder()
                            .deviceName(device.getName())
                            .jobStatus(jobStatus.get(device.getName()).name())
                            .elevation(Integer.parseInt(device.getLocation().getElevation()))
                            .role(device.getRole())
                            .build();
            deviceValidationStatuses.add(deviceDetails);
        }

        return deviceValidationStatuses;
    }

    public List<DeviceValidationStatus> toModel(Map<String, JobStatus> jobStatus) {

        List<DeviceValidationStatus> deviceValidationStatuses = new ArrayList<>();

        for (Map.Entry<String, JobStatus> entry : jobStatus.entrySet()) {
            DeviceValidationStatus status =
                    DeviceValidationStatus.builder()
                            .deviceName(entry.getKey())
                            .jobStatus(entry.getValue().name())
                            .build();
            deviceValidationStatuses.add(status);
        }

        return deviceValidationStatuses;
    }

    public ProjectRack toModel(
            Rack rack,
            JiraTicket jiraTicket,
            String rackValidationStatus,
            int devicesInDeployedState,
            List<String> fabricType) {

        return ProjectRack.builder()
                .building(rack.getBuilding())
                .block(rack.getBlock())
                .rackLocation(rack.getRackLocation())
                .rackSerialNumber(rack.getRackSerial())
                .ticketId(jiraTicket.getTicketId())
                .ticketType(jiraTicket.getTicketCategory())
                .resolveEnabled(jiraTicket.isResolveEnabled())
                .resolveDisabledReason(jiraTicket.getResolveDisabledReason())
                .rackState(rack.getRackState())
                .platformName(rack.getPlatformName())
                .validationStatus(rackValidationStatus)
                .devicesInDeployedState(devicesInDeployedState)
                .fabricType(fabricType)
                .build();
    }
}
