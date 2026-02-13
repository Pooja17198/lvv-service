package com.oracle.pic.networking.lvv.service.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.oracle.pic.commons.exceptions.server.ErrorCode;
import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.networking.autonet.plan.service.model.Device;
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
            @NonNull JiraSDService jiraSDService) {
        this.planServiceHelper = planServiceHelper;
        this.ncpJobDetailsDao = ncpJobDetailsDao;
        this.cablingValidationService = cablingValidationService;
        this.resourceModelTransformer = resourceModelTransformer;
        this.projectItemDao = projectItemDao;
        this.blockDetailsDao = blockDetailsDao;
        this.storeKeeperHelper = storeKeeperHelper;
        this.jiraSDService = jiraSDService;
    }

    private List<Device> listDevicesInRack(
            String regionName, String rackNumber, String building, MetricsScope scope) {

        return planServiceHelper.getDeviceListInRack(rackNumber, building, regionName, scope);
    }

    public List<DeviceDetails> getDeviceDetailsInRack(
            String rackSerialNumber,
            String regionName,
            String rackNumber,
            String building,
            MetricsScope scope) {

        List<Device> devices = listDevicesInRack(regionName, rackNumber, building, scope);

        // Map that stores {deviceName -> NCP Job ID}
        HashMap<String, String> emptyNcpJobs = new HashMap<>();

        // If we get a list of devices, we create job entries for each of that device
        // Else, we create a single job for the entire rack, and set the device name to the rack
        // serial itself
        if (!devices.isEmpty()) {
            for (Device device : devices) {
                emptyNcpJobs.put(device.getName(), "");
            }
        } else {
            emptyNcpJobs.put(rackSerialNumber, "");
        }

        // We add empty job details in the DB for the devices which haven't been added to the DB yet
        ncpJobDetailsDao.addUpdateNcpJobDetails(emptyNcpJobs, rackSerialNumber, scope);

        Map<String, JobStatus> deviceJobStatus =
                cablingValidationService.getValidationJobStatus(
                        scope, regionName, rackSerialNumber, rackNumber, false);

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

                racks.add(resourceModelTransformer.toModel(rack, jiraTicket));
            }
        }

        return racks;
    }
}
