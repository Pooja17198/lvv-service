package com.oracle.pic.networking.lvv.service.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.oracle.pic.commons.exceptions.server.ErrorCode;
import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.networking.autonet.plan.service.model.Device;
import com.oracle.pic.networking.lvv.service.config.LvvServiceApiConfiguration;
import com.oracle.pic.networking.lvv.service.dependencies.jira.JiraSDService;
import com.oracle.pic.networking.lvv.service.dependencies.jira.JiraTicket;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import com.oracle.pic.networking.lvv.service.dependencies.planservice.PlanServiceHelper;
import com.oracle.pic.networking.lvv.service.dependencies.storekeeper.Rack;
import com.oracle.pic.networking.lvv.service.dependencies.storekeeper.StoreKeeperHelper;
import com.oracle.pic.networking.lvv.service.kiev.BlockDetails;
import com.oracle.pic.networking.lvv.service.kiev.BlockDetailsDao;
import com.oracle.pic.networking.lvv.service.kiev.JobStatus;
import com.oracle.pic.networking.lvv.service.kiev.NcpJobDetailsDao;
import com.oracle.pic.networking.lvv.service.kiev.ProjectItem;
import com.oracle.pic.networking.lvv.service.kiev.ProjectItemDao;
import com.oracle.pic.networking.lvv.service.model.DeviceDetails;
import com.oracle.pic.networking.lvv.service.model.ProjectRack;
import com.oracle.pic.networking.lvv.service.resources.ResourceModelTransformer;
import com.oracle.pic.networking.lvv.service.utils.DeviceValidationEligibilityUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ToString
@Singleton
public class RacksService {

    @NonNull private final PlanServiceHelper planServiceHelper;
    @NonNull private final NcpJobDetailsDao ncpJobDetailsDao;
    @NonNull private final CablingValidationService cablingValidationService;
    @NonNull private final ResourceModelTransformer resourceModelTransformer;
    @NonNull private final ProjectItemDao projectItemDao;
    @NonNull private final BlockDetailsDao blockDetailsDao;
    @NonNull private final StoreKeeperHelper storeKeeperHelper;
    @NonNull private final JiraSDService jiraSDService;
    @NonNull private final LvvServiceApiConfiguration config;

    private static final String NO_OPEN_TICKET = "No open ticket";

    @Inject
    public RacksService(
            @NonNull PlanServiceHelper planServiceHelper,
            @NonNull NcpJobDetailsDao ncpJobDetailsDao,
            @NonNull CablingValidationService cablingValidationService,
            @NonNull ResourceModelTransformer resourceModelTransformer,
            @NonNull ProjectItemDao projectItemDao,
            @NonNull BlockDetailsDao blockDetailsDao,
            @NonNull StoreKeeperHelper storeKeeperHelper,
            @NonNull JiraSDService jiraSDService,
            @NonNull LvvServiceApiConfiguration config) {
        this.planServiceHelper = planServiceHelper;
        this.ncpJobDetailsDao = ncpJobDetailsDao;
        this.cablingValidationService = cablingValidationService;
        this.resourceModelTransformer = resourceModelTransformer;
        this.projectItemDao = projectItemDao;
        this.blockDetailsDao = blockDetailsDao;
        this.storeKeeperHelper = storeKeeperHelper;
        this.jiraSDService = jiraSDService;
        this.config = config;
    }

    private List<Device> listDevicesInRack(
            String regionName, String rackNumber, String building, MetricsScope scope) {

        return planServiceHelper
                .getDeviceListInRack(rackNumber, building, regionName, scope)
                .stream()
                .filter(DeviceValidationEligibilityUtils::isDisplayEligibleDevice)
                .toList();
    }

    public List<DeviceDetails> getDeviceDetailsInRack(
            String rackSerialNumber,
            String regionName,
            String rackNumber,
            String building,
            MetricsScope scope) {

        log.info(
                "Getting device details for rack serial {} with rack number {} in building {} in region {}",
                rackSerialNumber,
                rackNumber,
                building,
                regionName);
        List<Device> devices = listDevicesInRack(regionName, rackNumber, building, scope);

        // Map that stores {deviceName -> NCP Job ID}
        HashMap<String, String> emptyNcpJobs = new HashMap<>();

        // We add job entries only for monitored+deployed devices.
        // If there are no devices at all (for example, Plan service unavailable in ViBE), keep the
        // existing rack-level fallback.
        for (Device device : devices) {
            if (DeviceValidationEligibilityUtils.isValidationEligibleDevice(device)) {
                emptyNcpJobs.put(device.getName(), "");
            }
        }
        if (devices.isEmpty()) {
            emptyNcpJobs.put(rackSerialNumber, "");
        }

        // We add empty job details in the DB for the devices which haven't been added to the DB yet
        if (!emptyNcpJobs.isEmpty()) {
            ncpJobDetailsDao.addUpdateNcpJobDetails(emptyNcpJobs, rackSerialNumber, scope);
        }

        Map<String, JobStatus> deviceJobStatus =
                cablingValidationService.getValidationJobStatus(
                        scope, regionName, building, rackSerialNumber, rackNumber, false);

        return resourceModelTransformer.toModel(deviceJobStatus, devices);
    }

    public List<ProjectRack> listProjectRacks(String projectId, MetricsScope scope) {

        ProjectItem projectItem = projectItemDao.getProjectItem(projectId);

        if (projectItem == null) {
            scope.emit(MetricNames.FetchRacks.ProjectNotFound.name(), 1.0);
            throw new RenderableException(
                    ErrorCode.NotAuthorizedOrNotFound, "Project {} not found", projectId);
        }

        List<BlockDetails> blockDetails = blockDetailsDao.getBlockDetailsForProject(projectId);

        if (blockDetails.isEmpty()) {
            scope.emit(MetricNames.FetchRacks.NoBlocksInProject.name(), 1.0);
            throw new RenderableException(
                    ErrorCode.IncorrectState, "No blocks found in project {}", projectId);
        } else {
            // If a block exists for the project, we fetch the building name from the first block in
            // the list to emit it as a dimension while fetching Project Racks, since a project maps
            // to only one building
            scope.withDimension("buildingName", blockDetails.get(0).getBlock().getBuilding());
        }

        // Validate project region before proceeding
        String projectRegionForValidation = projectItem.getRegionName();
        if (projectRegionForValidation == null || projectRegionForValidation.isBlank()) {
            scope.emit(MetricNames.FetchRacks.RegionMissing.name(), 1.0);
            log.warn("Project region is null/blank for project {}", projectId);
            throw new RenderableException(
                    ErrorCode.MissingParameter,
                    "Project region is missing for project {}",
                    projectId);
        }

        scope.emit(MetricNames.FetchRacks.FetchRacks, 1.0);
        List<ProjectRack> racks = new ArrayList<>();

        for (BlockDetails blockDetail : blockDetails) {

            String building = blockDetail.getBlock().getBuilding();
            String block = blockDetail.getBlock().getBlockNumber();
            Map<String, JiraTicket> blockTickets =
                    jiraSDService.findOpenTicketsBySerialForBlock(building, block);

            List<Rack> blockRacks = storeKeeperHelper.listRacks(block, building, scope);

            if (blockRacks == null || blockRacks.isEmpty()) {
                log.info("No racks found in building {} block {}", building, block);
                continue;
            }

            for (Rack rack : blockRacks) {
                JiraTicket jiraTicket = null;
                String rackSerial = rack.getRackSerial();

                if (rackSerial != null) {
                    jiraTicket = blockTickets.get(rackSerial);
                }

                // If Jira has an active ticket for this rack, prefer it over SK ticket_number.
                // Otherwise, do not attempt per-ticket Jira lookups; resolve stays disabled.

                if (jiraTicket != null) {
                    jiraTicket.setResolveEnabled(true);
                    jiraTicket.setResolveDisabledReason(null);
                } else {
                    // No open Jira ticket matched for this rack -> resolve disabled.
                    jiraTicket =
                            JiraTicket.builder()
                                    .ticketId(null)
                                    .ticketCategory(null)
                                    .resolveEnabled(false)
                                    .resolveDisabledReason(NO_OPEN_TICKET)
                                    .build();
                }

                // disable resolve for specific regions
                String projectRegion = projectItem.getRegionName();
                if (isResolveDisabledForRegion(projectRegion, scope, projectId)) {
                    jiraTicket.setResolveEnabled(false);
                    jiraTicket.setResolveDisabledReason("Resolve disabled for this region");
                    scope.emit(MetricNames.FetchRacks.ResolveDisabledRegionCount.name(), 1.0);
                }

                racks.add(resourceModelTransformer.toModel(rack, jiraTicket));
            }
        }

        return racks;
    }

    private boolean isResolveDisabledForRegion(
            @NonNull String regionName, MetricsScope scope, String projectId) {
        if (config == null || config.getResolveDisabledRegions() == null) {
            return false;
        }
        for (String disabled : config.getResolveDisabledRegions()) {
            if (disabled != null && disabled.equalsIgnoreCase(regionName)) {
                return true;
            }
        }
        return false;
    }
}
