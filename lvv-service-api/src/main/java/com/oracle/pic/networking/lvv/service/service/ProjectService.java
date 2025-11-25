package com.oracle.pic.networking.lvv.service.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.oracle.pic.commons.exceptions.server.ErrorCode;
import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import com.oracle.pic.networking.lvv.service.kiev.BlockDetails;
import com.oracle.pic.networking.lvv.service.kiev.BlockDetailsDao;
import com.oracle.pic.networking.lvv.service.kiev.ProjectItem;
import com.oracle.pic.networking.lvv.service.kiev.ProjectItemDao;
import com.oracle.pic.networking.lvv.service.model.Project;
import com.oracle.pic.networking.lvv.service.resources.ResourceModelTransformer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@ToString
public class ProjectService {
    private final ProjectItemDao projectItemDao;
    private final BlockDetailsDao blockDetailsDao;

    @NonNull ResourceModelTransformer resourceModelTransformer;

    @Inject
    public ProjectService(
            ProjectItemDao projectItemDao,
            BlockDetailsDao blockDetailsDao,
            ResourceModelTransformer resourceModelTransformer) {
        this.projectItemDao = projectItemDao;
        this.blockDetailsDao = blockDetailsDao;
        this.resourceModelTransformer = resourceModelTransformer;
    }

    boolean checkBlockIsUnassigned(String block, String building) {
        BlockDetails blockDetails = blockDetailsDao.getBlockDetails(block, building);
        if (blockDetails == null) {
            return true;
        } else {
            log.info(
                    "Block {} in building {} already assigned to project ID {}",
                    block,
                    building,
                    blockDetails.getProjectId());
            return false;
        }
    }

    void checkNullParameters(
            String projectId,
            String vendorName,
            String createdBy,
            String region,
            String building,
            List<String> blocks) {
        // NULL Checks
        List<String> missing = new ArrayList<>();

        if (projectId == null || projectId.isBlank()) {
            missing.add("projectId");
        }
        if (vendorName == null || vendorName.isBlank()) {
            missing.add("vendorName");
        }
        if (createdBy == null || createdBy.isBlank()) {
            missing.add("createdBy");
        }
        if (region == null || region.isBlank()) {
            missing.add("region");
        }
        if (building == null || building.isBlank()) {
            missing.add("building");
        }
        if (blocks == null || blocks.isEmpty()) {
            missing.add("blocks");
        }

        if (!missing.isEmpty()) {
            throw new RenderableException(
                    ErrorCode.MissingParameter,
                    "Missing or empty parameters: " + String.join(", ", missing));
        }
    }

    public void createProject(
            String projectId,
            String vendorName,
            String createdBy,
            String cmLink,
            String region,
            String building,
            List<String> blocks,
            MetricsScope scope) {

        // since CM link is optional(can be null/empty) so we are not checking it here
        checkNullParameters(projectId, vendorName, createdBy, region, building, blocks);

        log.info("Creating Project with ID {}", projectId);

        List<BlockDetails> blockDetails = buildBlockDetailsForProject(projectId, building, blocks);

        ProjectItem item =
                ProjectItem.builder()
                        .projectId(projectId)
                        .vendorName(
                                vendorName.toLowerCase()) // Converting vendor name to lowercase to
                        // make vendor names case-insensitive
                        .createdBy(createdBy)
                        .cmLink(cmLink)
                        .regionName(region)
                        .build();

        projectItemDao.addProjectItem(item, blockDetails, scope);
    }

    public void updateProject(
            String projectId,
            String vendorName,
            String createdBy,
            String cmLink,
            String region,
            String building,
            List<String> blocks,
            MetricsScope scope) {

        // since CM link is optional(can be null/empty) so we are not checking it here
        checkNullParameters(projectId, vendorName, createdBy, region, building, blocks);

        log.info("Updating Project with ID {}", projectId);

        List<BlockDetails> blockDetails = buildBlockDetailsForProject(projectId, building, blocks);

        // Converting vendor name to lowercase to make vendor names case-insensitive
        vendorName = vendorName.toLowerCase();

        ProjectItem item =
                ProjectItem.builder()
                        .projectId(projectId)
                        .vendorName(vendorName)
                        .createdBy(createdBy)
                        .cmLink(cmLink)
                        .regionName(region)
                        .build();

        projectItemDao.updateProjectItem(item, blockDetails, scope);
    }

    public Project getProject(String projectId, MetricsScope scope) {

        log.info("Fetching project details for Project {}", projectId);

        ProjectItem projectItem = projectItemDao.getProjectItem(projectId);

        if (projectItem == null) {
            log.error("Project ID {} not found", projectId);
            scope.emit(MetricNames.GetProjectItem.ProjectNotFound.name(), 1.0);
            throw new RenderableException(
                    ErrorCode.NotAuthorizedOrNotFound, "Project ID not found");
        }

        String regionName = projectItem.getRegionName();
        scope.withDimension("region", regionName);
        scope.withDimension("projectId", projectId);
        scope.emit(MetricNames.GetProjectItem.GetProject.name(), 1.0);

        List<BlockDetails> blockDetails = blockDetailsDao.getBlockDetailsForProject(projectId);
        if (blockDetails.isEmpty()) {
            log.error("No blocks assigned for this project");
            scope.emit(MetricNames.GetProjectItem.NoBlocksInProject.name(), 1.0);
            throw new RenderableException(
                    ErrorCode.IncorrectState,
                    "Project ID must have at least 1 block assigned to it");
        }

        return resourceModelTransformer.toModel(projectItem, blockDetails);
    }

    public void deleteProject(String projectId, MetricsScope scope) {

        log.info("Deleting the project entry");

        projectItemDao.deleteProjectItem(projectId, scope);
    }

    public List<Project> getProjectListByVendor(String vendorName) {
        log.info("Fetching List of Projects by Vendor Name");

        List<ProjectItem> projects = projectItemDao.getProjectItemsForVendor(vendorName);

        if (projects.isEmpty()) {
            log.info("No projects associated with the vendor {}", vendorName);
            return Collections.emptyList();
        }

        List<Project> result = new ArrayList<>();

        for (ProjectItem project : projects) {

            log.info("Fetching blocks associated with project {}", project.getProjectId());

            List<BlockDetails> blockDetails =
                    blockDetailsDao.getBlockDetailsForProject(project.getProjectId());

            Project proj = resourceModelTransformer.toModel(project, blockDetails);

            result.add(proj);
        }

        return result;
    }

    public List<Project> getProjectListByRegion(String regionName) {
        log.info("Fetching List of Projects by Region Name");

        List<ProjectItem> projects = projectItemDao.getProjectItemsForRegion(regionName);

        if (projects.isEmpty()) {
            log.info("No projects found in region {}", regionName);
            return Collections.emptyList();
        }

        List<Project> result = new ArrayList<>();

        for (ProjectItem project : projects) {

            log.info("Fetching blocks associated with project {}", project.getProjectId());

            List<BlockDetails> blockDetails =
                    blockDetailsDao.getBlockDetailsForProject(project.getProjectId());

            Project proj = resourceModelTransformer.toModel(project, blockDetails);

            result.add(proj);
        }

        return result;
    }

    public List<Project> getProjectListByVendorAndRegion(String vendorName, String regionName) {
        log.info("Fetching List of Projects by Vendor and Region");

        List<ProjectItem> projects =
                projectItemDao.getProjectItemsForVendorAndRegion(vendorName, regionName);

        if (projects.isEmpty()) {
            log.info(
                    "No projects associated with the vendor {} in region {}",
                    vendorName,
                    regionName);
            return Collections.emptyList();
        }

        List<Project> result = new ArrayList<>();

        for (ProjectItem project : projects) {

            log.info("Fetching blocks associated with project {}", project.getProjectId());

            List<BlockDetails> blockDetails =
                    blockDetailsDao.getBlockDetailsForProject(project.getProjectId());

            Project proj = resourceModelTransformer.toModel(project, blockDetails);

            result.add(proj);
        }

        return result;
    }

    private static List<BlockDetails> buildBlockDetailsForProject(
            String projectId, String building, List<String> blocks) {
        List<BlockDetails> blockDetails = new ArrayList<>();
        for (String block : blocks) {
            BlockDetails blockDetail =
                    BlockDetails.builder()
                            .projectId(projectId)
                            .block(
                                    BlockDetails.Block.builder()
                                            .blockNumber(block)
                                            .building(building)
                                            .build())
                            .build();
            blockDetails.add(blockDetail);
        }
        return blockDetails;
    }

    public List<Project> getProjectList() {
        log.info("Fetching List of all Projects");

        List<ProjectItem> projects = projectItemDao.getAllProjects();

        if (projects.isEmpty()) {
            log.info("No projects found");
            return Collections.emptyList();
        }

        List<Project> result = new ArrayList<>();

        for (ProjectItem project : projects) {

            log.info("Fetching blocks associated with project {}", project.getProjectId());

            List<BlockDetails> blockDetails =
                    blockDetailsDao.getBlockDetailsForProject(project.getProjectId());

            Project proj = resourceModelTransformer.toModel(project, blockDetails);

            result.add(proj);
        }

        return result;
    }
}
