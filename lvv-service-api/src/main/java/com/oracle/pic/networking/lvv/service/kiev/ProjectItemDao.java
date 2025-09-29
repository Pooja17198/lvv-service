package com.oracle.pic.networking.lvv.service.kiev;

import com.google.api.client.util.Lists;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.oracle.pic.commons.exceptions.server.ErrorCode;
import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.kiev.Transaction;
import com.oracle.pic.kiev.exceptions.CommitConflictException;
import com.oracle.pic.kiev.exceptions.DuplicateKeyException;
import com.oracle.pic.kiev.mapping.Index;
import com.oracle.pic.kiev.mapping.MappedHashBucket;
import com.oracle.pic.kiev.mapping.ScanPage;
import com.oracle.pic.kiev.mapping.token.PaginationTokenSerializer;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/** Provides Kiev functions to help with managing the data stored in Kiev DB */
@Slf4j
@Singleton
@ToString
public class ProjectItemDao {
    private static final int DEFAULT_PAGE_SIZE = 1000;
    private final ConfigurationStore<Long, ProjectItem> projectItemStore;
    private final MappedHashBucket<Long, ProjectItem> projectItemProvider;
    private final PaginationTokenSerializer serializer;
    private final Index<ProjectItem.ProjectIdIndex, ProjectItem> projectItemIndex;
    private final Index<ProjectItem.VendorNameIndex, ProjectItem> vendorNameIndex;
    private final Index<ProjectItem.RegionNameIndex, ProjectItem> regionNameIndex;
    private final Index<ProjectItem.VendorRegionIndex, ProjectItem> vendorRegionIndex;
    private final BlockDetailsDao blockDetailsDao;

    @Inject
    public ProjectItemDao(
            @NonNull ConfigurationStore<Long, ProjectItem> projectItemStore,
            @NonNull PaginationTokenSerializer serializer,
            @NonNull MappedHashBucket<Long, ProjectItem> projectItemProvider,
            @NonNull BlockDetailsDao blockDetailsDao) {
        this.projectItemStore = projectItemStore;
        this.projectItemProvider = projectItemProvider;
        this.serializer = serializer;
        this.vendorNameIndex =
                projectItemProvider.getIndex(
                        ProjectItem.VENDOR_COLUMN_NAME, ProjectItem.VendorNameIndex.class);
        this.projectItemIndex =
                projectItemProvider.getIndex(
                        ProjectItem.PROJECT_ID_COLUMN_NAME, ProjectItem.ProjectIdIndex.class);
        this.regionNameIndex =
                projectItemProvider.getIndex(
                        ProjectItem.REGION_COLUMN_NAME, ProjectItem.RegionNameIndex.class);

        this.vendorRegionIndex =
                projectItemProvider.getIndex(
                        ProjectItem.VENDOR_REGION_INDEX_NAME, ProjectItem.VendorRegionIndex.class);
        this.blockDetailsDao = blockDetailsDao;
    }

    BlockDetails checkBlockIsUnassigned(BlockDetails blockDetail) {

        log.info("Fetching a project which has this block assigned to it");

        BlockDetails existingBlockDetails =
                blockDetailsDao.getBlockDetails(
                        blockDetail.getBlock().getBuilding(),
                        blockDetail.getBlock().getBlockNumber());

        if (existingBlockDetails == null
                || existingBlockDetails.getProjectId().equals(blockDetail.getProjectId())) {
            log.info("No project exists which has this block assigned to it");
            return null;
        } else {
            log.info(
                    "Block {} in building {} already assigned to project ID {}. Block Details {}",
                    existingBlockDetails.getBlock().getBlockNumber(),
                    existingBlockDetails.getBlock().getBuilding(),
                    existingBlockDetails.getProjectId(),
                    existingBlockDetails);
            return existingBlockDetails;
        }
    }

    void checkBlockIsAlreadyAssigned(@NonNull List<BlockDetails> blockDetails) {

        log.info("Checking if the block is already assigned to any other project");

        try {
            List<BlockDetails> alreadyAssignedBlocks =
                    blockDetails.stream()
                            .map(this::checkBlockIsUnassigned) // apply method to each blockDetail
                            .filter(Objects::nonNull) // keep only non-null results
                            .collect(Collectors.toList());

            if (!alreadyAssignedBlocks.isEmpty()) {
                log.error(
                        "Blocks [{}] are already assigned to the projects. Cancelling project creation.",
                        alreadyAssignedBlocks);
                throw new RenderableException(
                        ErrorCode.InvalidParameter,
                        "Blocks are already assigned: " + alreadyAssignedBlocks);
            } else {
                log.info(
                        "None of the blocks are assigned to other projects. Continuing project creation");
            }
        } catch (RenderableException e) {
            log.error("Blocks are already assigned to other projects", e);
            throw e;
        }
    }

    public void addProjectItem(
            @NonNull ProjectItem item,
            @NonNull List<BlockDetails> blockDetails,
            MetricsScope scope) {
        ProjectItem existingItem = null;

        try (Transaction txn = projectItemStore.beginTransaction(item.getProjectId())) {
            scope.emit(MetricNames.AddProjectItem.AddProject.name(), 1.0);

            log.info(
                    "Creating Project Entry: Project Item: {}, BlockDetails: {}",
                    item,
                    blockDetails);

            // Check if the item already exists
            try {
                existingItem = getProjectItemForProjectId(item.getProjectId());
            } catch (RuntimeException exception) {
                log.info(
                        "Project with ID {} does not exist. Creating new Project",
                        item.getProjectId());
            }

            if (existingItem != null) {
                log.error("Project ID {} already exists", item.getProjectId());
                log.error(
                        "Existing project details: {} {} \n",
                        existingItem,
                        blockDetailsDao.getBlockDetailsForProject(item.getProjectId()));

                scope.emit(MetricNames.AddProjectItem.ItemAlreadyExists.name(), 1.0);

                throw new RenderableException(
                        ErrorCode.ResourceAlreadyExists, "Project ID already exists");
            }

            try {
                checkBlockIsAlreadyAssigned(blockDetails);
            } catch (RenderableException e) {
                scope.emit(MetricNames.AddProjectItem.BlockAlreadyAssigned.name(), 1.0);
                throw e;
            }

            log.info("Adding blocks for the project");

            blockDetailsDao.addBlockDetails(blockDetails, txn);

            this.projectItemStore.createItem(txn, item);

            try {
                txn.commit();
                log.info("Project ID {} has been created successfully", item.getProjectId());
            } catch (CommitConflictException | DuplicateKeyException exception) {
                String message = "Failed to create project " + item;
                scope.emit(MetricNames.AddProjectItem.KievCommitFailure.name(), 1.0);
                handleException(txn, exception, message);
                throw new RenderableException(
                        ErrorCode.ExternalServerInvalidResponse,
                        "Failed to store project details in Kiev. Please try again.");
            }
        }
    }

    public void updateProjectItem(
            @NonNull ProjectItem item,
            @NonNull List<BlockDetails> blockDetails,
            MetricsScope scope) {
        ProjectItem existingItem = null;

        try (Transaction txn = projectItemStore.beginTransaction(item.getProjectId())) {

            log.info(
                    "Updating Project Entry: Project Item: {}, BlockDetails: {}",
                    item,
                    blockDetails);

            // Check if the item exists
            try {
                existingItem = getProjectItemForProjectId(item.getProjectId());
            } catch (RuntimeException exception) {
                log.info("Project with ID {} does not exist.", item.getProjectId());
                scope.emit(MetricNames.UpdateProjectItem.ItemDoesNotExist.name(), 1.0);
                throw new RenderableException(
                        ErrorCode.NotAuthorizedOrNotFound, "Project ID not found");
            }

            // Deleting existing block entries to be updated with new incoming blocks
            log.info("Deleting any existing block entries in the same project ID");

            if (getProjectItem(item.getProjectId()) != null) {
                blockDetailsDao.deleteBlockDetails(item.getProjectId(), txn);
            }

            try {
                checkBlockIsAlreadyAssigned(blockDetails);
            } catch (RenderableException e) {
                scope.emit(MetricNames.UpdateProjectItem.BlockAlreadyAssigned.name(), 1.0);
                throw e;
            }

            log.info("Adding blocks for the project");
            blockDetailsDao.addBlockDetails(blockDetails, txn);

            if (existingItem != null) {
                log.info("Updating existing project {}", item.getProjectId());
                item.setProjectKey(existingItem.getProjectKey());
                this.projectItemStore.updateItem(txn, item);
            } else {
                log.error("Project with ID {} does not exist", item.getProjectId());
                scope.emit(MetricNames.UpdateProjectItem.ItemDoesNotExist.name(), 1.0);
                throw new RenderableException(
                        ErrorCode.NotAuthorizedOrNotFound, "Project ID not found");
            }

            try {
                txn.commit();
                log.info("Project ID {} has been updated successfully", item.getProjectId());
            } catch (CommitConflictException | DuplicateKeyException exception) {
                String message = "Failed to update project " + item;
                scope.emit(MetricNames.UpdateProjectItem.KievCommitFailure.name(), 1.0);
                handleException(txn, exception, message);
                throw new RenderableException(
                        ErrorCode.ExternalServerInvalidResponse,
                        "Failed to store project details in Kiev");
            }
        }
    }

    public ProjectItem getProjectItem(@NonNull String projectId) {
        try {
            return getProjectItemForProjectId(projectId);
        } catch (RuntimeException exception) {
            log.info("Project ID does not exist {}", projectId);
            return null;
        }
    }

    public void deleteProjectItem(@NonNull String projectId, MetricsScope scope) {

        try (Transaction txn = projectItemStore.beginTransaction(projectId)) {

            log.info("Deleting the project {}", projectId);
            try {
                ProjectItem existingItem = getProjectItemForProjectId(projectId);
                this.projectItemStore.deleteItem(txn, existingItem.getProjectKey());
            } catch (RuntimeException exception) {
                log.error("Project with ID {} does not exist", projectId);
                scope.emit(MetricNames.DeleteProjectItem.ItemDoesNotExist.name(), 1.0);
                throw new RenderableException(
                        ErrorCode.NotAuthorizedOrNotFound, "Project does not exist");
            }

            log.info("Deleting all the blocks assigned in the project");

            blockDetailsDao.deleteBlockDetails(projectId, txn);

            try {
                txn.commit();
                log.info("Project deleted successfully");
            } catch (CommitConflictException | DuplicateKeyException exception) {
                String message = "Failed to delete projectType item " + projectId;
                scope.emit(MetricNames.DeleteProjectItem.KievCommitFailure.name(), 1.0);
                handleException(txn, exception, message);
                throw new RenderableException(
                        ErrorCode.ExternalServerInvalidResponse,
                        "Failed to delete project in Kiev");
            }
        }
    }

    public ProjectItem getProjectItemForProjectId(String projectId) {

        log.info("Fetching project with ID: {}", projectId);

        ProjectItem.ProjectIdIndex prefix =
                ProjectItem.ProjectIdIndex.builder().projectId(projectId).build();

        List<ProjectItem> result = Lists.newArrayList();

        Preconditions.checkNotNull(projectItemIndex, "projectItemIndex is null");
        ScanPage<ProjectItem> page = projectItemIndex.beginPrefixScan(prefix, DEFAULT_PAGE_SIZE);
        while (page != null) {
            KievRateLimiter.throttle();
            List<ProjectItem> pageResults = page.results();
            if (pageResults != null) {
                List<ProjectItem> filteredPage =
                        pageResults.stream()
                                .filter(Objects::nonNull)
                                .filter(proj -> proj.getProjectId().equals(projectId))
                                .toList();

                result.addAll(filteredPage);
            }

            // Setup next page
            if (page.hasNext()) {
                page = projectItemIndex.scan(page.paginationToken());
            } else {
                page = null;
            }
        }

        log.info("Found {} project with ID {}", result.size(), projectId);

        if (result.size() > 1) {
            throw new RenderableException(
                    ErrorCode.IncorrectState, "More than one project item found");
        }

        if (result.isEmpty()) {
            throw new RuntimeException("No project item found");
        }

        return result.get(0);
    }

    public List<ProjectItem> getProjectItemsForVendor(String vendorName) {

        log.info("Fetching all the projects assigned to the vendor {}", vendorName);

        ProjectItem.VendorNameIndex prefix =
                ProjectItem.VendorNameIndex.builder().vendorName(vendorName).build();

        List<ProjectItem> result = Lists.newArrayList();

        Preconditions.checkNotNull(vendorNameIndex, "vendorNameIndex is null");
        ScanPage<ProjectItem> page = vendorNameIndex.beginPrefixScan(prefix, DEFAULT_PAGE_SIZE);
        while (page != null) {
            KievRateLimiter.throttle();
            List<ProjectItem> pageResults = page.results();
            if (pageResults != null) {
                List<ProjectItem> filteredPage =
                        pageResults.stream()
                                .filter(Objects::nonNull)
                                .filter(proj -> proj.getVendorName().equals(vendorName))
                                .toList();

                result.addAll(filteredPage);
            }

            // Setup next page
            if (page.hasNext()) {
                page = vendorNameIndex.scan(page.paginationToken());
            } else {
                page = null;
            }
        }

        log.info("Found {} projects assigned to the vendor {}", result.size(), vendorName);

        return result;
    }

    public List<ProjectItem> getAllProjects() {

        log.info("Fetching all projects in the region");

        List<ProjectItem> result = com.google.common.collect.Lists.newArrayList();

        Preconditions.checkNotNull(projectItemProvider, "bucket not present");

        ScanPage<ProjectItem> page = projectItemProvider.beginScan(DEFAULT_PAGE_SIZE);
        while (page != null) {
            KievRateLimiter.throttle();
            List<ProjectItem> pageResults = page.results();
            if (pageResults != null) {
                List<ProjectItem> filteredPage =
                        pageResults.stream().filter(Objects::nonNull).toList();

                result.addAll(filteredPage);
            }

            // Setup next page
            if (page.hasNext()) {
                page = projectItemProvider.scan(page.paginationToken());
            } else {
                page = null;
            }
        }

        log.info("Found {} projects", result.size());

        return result;
    }

    public List<ProjectItem> getProjectItemsForRegion(String regionName) {

        log.info("Fetching all the projects for region {}", regionName);

        List<ProjectItem> result = com.google.common.collect.Lists.newArrayList();
        if (regionNameIndex != null) {
            ProjectItem.RegionNameIndex prefix =
                    ProjectItem.RegionNameIndex.builder().regionName(regionName).build();

            ScanPage<ProjectItem> page = regionNameIndex.beginPrefixScan(prefix, DEFAULT_PAGE_SIZE);
            while (page != null) {
                KievRateLimiter.throttle();
                List<ProjectItem> pageResults = page.results();
                if (pageResults != null) {
                    List<ProjectItem> filteredPage =
                            pageResults.stream()
                                    .filter(Objects::nonNull)
                                    .filter(proj -> regionName.equals(proj.getRegionName()))
                                    .toList();

                    result.addAll(filteredPage);
                }

                if (page.hasNext()) {
                    page = regionNameIndex.scan(page.paginationToken());
                } else {
                    page = null;
                }
            }
        } else {
            // Fallback: full scan and filter by region; derive if missing
            ScanPage<ProjectItem> page = projectItemProvider.beginScan(DEFAULT_PAGE_SIZE);
            while (page != null) {
                KievRateLimiter.throttle();
                List<ProjectItem> pageResults = page.results();
                if (pageResults != null) {
                    for (ProjectItem proj : pageResults) {
                        if (proj == null) {
                            continue;
                        }
                        if (isProjectInRegion(proj, regionName)) {
                            result.add(proj);
                        }
                    }
                }
                if (page.hasNext()) {
                    page = projectItemProvider.scan(page.paginationToken());
                } else {
                    page = null;
                }
            }
        }

        log.info("Found {} projects in region {}", result.size(), regionName);

        return result;
    }

    public List<ProjectItem> getProjectItemsForVendorAndRegion(
            String vendorName, String regionName) {

        log.info("Fetching all the projects for vendor {} in region {}", vendorName, regionName);

        List<ProjectItem> result = com.google.common.collect.Lists.newArrayList();
        java.util.Set<String> seenProjectIds = new java.util.HashSet<>();
        if (vendorRegionIndex != null) {
            ProjectItem.VendorRegionIndex prefix =
                    ProjectItem.VendorRegionIndex.builder()
                            .vendorName(vendorName)
                            .regionName(regionName)
                            .build();

            ScanPage<ProjectItem> page =
                    vendorRegionIndex.beginPrefixScan(prefix, DEFAULT_PAGE_SIZE);
            while (page != null) {
                KievRateLimiter.throttle();
                List<ProjectItem> pageResults = page.results();
                if (pageResults != null) {
                    List<ProjectItem> filteredPage =
                            pageResults.stream()
                                    .filter(Objects::nonNull)
                                    .filter(
                                            proj ->
                                                    vendorName.equals(proj.getVendorName())
                                                            && regionName.equals(
                                                                    proj.getRegionName()))
                                    .toList();

                    result.addAll(filteredPage);
                    for (ProjectItem p : filteredPage) {
                        if (p != null) {
                            seenProjectIds.add(p.getProjectId());
                        }
                    }
                }

                if (page.hasNext()) {
                    page = vendorRegionIndex.scan(page.paginationToken());
                } else {
                    page = null;
                }
            }
            // Temporary union fallback: include vendor rows with missing regionName whose derived
            // region matches
            ProjectItem.VendorNameIndex vendorPrefixFallback =
                    ProjectItem.VendorNameIndex.builder().vendorName(vendorName).build();
            ScanPage<ProjectItem> vendorScanPage =
                    vendorNameIndex.beginPrefixScan(vendorPrefixFallback, DEFAULT_PAGE_SIZE);
            while (vendorScanPage != null) {
                KievRateLimiter.throttle();
                List<ProjectItem> vendorScanPageResults = vendorScanPage.results();
                if (vendorScanPageResults != null) {
                    for (ProjectItem proj : vendorScanPageResults) {
                        if (proj == null) {
                            continue;
                        }
                        if (!vendorName.equals(proj.getVendorName())) {
                            continue;
                        }
                        String projRegion = proj.getRegionName();
                        if (projRegion != null && !projRegion.isBlank()) {
                            continue;
                        }
                        if (isProjectInRegion(proj, regionName)
                                && !seenProjectIds.contains(proj.getProjectId())) {
                            result.add(proj);
                            seenProjectIds.add(proj.getProjectId());
                        }
                    }
                }
                if (vendorScanPage.hasNext()) {
                    vendorScanPage = vendorNameIndex.scan(vendorScanPage.paginationToken());
                } else {
                    vendorScanPage = null;
                }
            }
        } else {
            // Fallback: use vendor index and then filter by region
            ProjectItem.VendorNameIndex prefix =
                    ProjectItem.VendorNameIndex.builder().vendorName(vendorName).build();
            ScanPage<ProjectItem> page = vendorNameIndex.beginPrefixScan(prefix, DEFAULT_PAGE_SIZE);
            while (page != null) {
                KievRateLimiter.throttle();
                List<ProjectItem> pageResults = page.results();
                if (pageResults != null) {
                    for (ProjectItem proj : pageResults) {
                        if (proj == null) {
                            continue;
                        }
                        if (!vendorName.equals(proj.getVendorName())) {
                            continue;
                        }
                        if (isProjectInRegion(proj, regionName)) {
                            result.add(proj);
                        }
                    }
                }
                if (page.hasNext()) {
                    page = vendorNameIndex.scan(page.paginationToken());
                } else {
                    page = null;
                }
            }
        }

        log.info(
                "Found {} projects for vendor {} in region {}",
                result.size(),
                vendorName,
                regionName);

        return result;
    }

    private boolean isProjectInRegion(ProjectItem projectItem, String expectedRegion) {
        String projRegion = projectItem.getRegionName();
        if (projRegion == null || projRegion.isBlank()) {
            List<BlockDetails> blocks =
                    blockDetailsDao.getBlockDetailsForProject(projectItem.getProjectId());
            if (!blocks.isEmpty()) {
                String building = blocks.get(0).getBlock().getBuilding();
                projRegion =
                        com.oracle.pic.networking.lvv.service.utils.GeneralUtils
                                .getRegionFromBuilding(building);
            }
        }
        return expectedRegion.equals(projRegion);
    }

    private void handleException(Transaction txn, Exception exception, String message) {
        txn.abort();
        log.error("Error message: {}", message, exception);
    }
}
