package com.oracle.pic.networking.lvv.service.kiev;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.kiev.Bucket;
import com.oracle.pic.kiev.Transaction;
import com.oracle.pic.kiev.exceptions.CommitConflictException;
import com.oracle.pic.kiev.exceptions.ExpiredTokenException;
import com.oracle.pic.kiev.mapping.PaginationDirection;
import com.oracle.pic.kiev.mapping.token.PaginationTokenSerializer;
import com.oracle.pic.networking.lvv.service.utils.PaginationToken;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/** Provides Kiev functions to help with managing the data stored in Kiev DB */
@Slf4j
@Singleton
public class KievManager {
    private final ConfigurationStore<String, ProjectItem> projectItemStore;
    private PaginationTokenSerializer serializer;

    @Inject
    public KievManager(
            @NonNull ConfigurationStore<String, ProjectItem> projectItemStore,
            @NonNull PaginationTokenSerializer serializer) {
        this.projectItemStore = projectItemStore;
        this.serializer = serializer;
    }

    public ProjectItem addProjectItem(@NonNull ProjectItem item) throws Exception {
        ProjectItem existingItem = null;
        ProjectItem projectItem;

        try (Transaction txn = projectItemStore.beginTransaction(item.getProjectId());
                MetricsScope scope = MetricsScope.create("AddConfigTypeItem")) {

            // Try to get the item if it already exists
            try {
                existingItem = projectItemStore.getItem(item.getProjectId());
            } catch (Exception exception) {
                log.info("Item does not exist. Creating new Item with key {}", item.getProjectId());
            }

            if (existingItem != null) {
                existingItem.setType(item.getType());
                existingItem.setVendorName(item.getVendorName());
                existingItem.setBuilding(item.getBuilding());
                existingItem.setBlock(item.getBlock());
                projectItem = this.projectItemStore.updateItem(txn, existingItem);
            } else {
                projectItem = this.projectItemStore.createItem(txn, item);
            }

            try {
                txn.commit();
                scope.recordSuccess();
                return projectItem;
            } catch (CommitConflictException exception) {
                String message = "Failed to add projectType item " + item.toString();
                handleException(txn, exception, message);
                throw new Exception(message, exception);
            }
        }
    }

    public ProjectItem getProjectItem(@NonNull String projectId) throws Exception {

        try {
            ProjectItem existingItem = projectItemStore.getItem(projectId);
            return existingItem;
        } catch (Exception exception) {
            log.info("Item does Not Exist {}", projectId);
            throw exception;
        }
    }

    public List<ProjectItem> getAllProjectItem(PaginationToken paginationToken, String vendorName)
            throws Exception {

        try {
            Optional<com.oracle.pic.kiev.mapping.PaginationToken> pt =
                    getPaginationToken(paginationToken.getToken());
            ScanResult<ProjectItem> projectItemScanResult =
                    projectItemStore.scanBucket(
                            50, pt, Bucket.Direction.ASCENDING, PaginationDirection.FORWARD);

            List<ProjectItem> result = new ArrayList<>();

            projectItemScanResult
                    .getResults()
                    .forEach(
                            (item) -> {
                                if (item.getVendorName().equals(vendorName)) {
                                    result.add(item);
                                }
                            });
            paginationToken.setToken(projectItemScanResult.getPaginationToken());

            return result;
        } catch (Exception exception) {
            log.info("Error while scanning projectlist");
            throw exception;
        }
    }

    private void handleException(Transaction txn, Exception exception, String message) {
        txn.abort();
        log.error("Error message: {}", message, exception);
    }

    private Optional<com.oracle.pic.kiev.mapping.PaginationToken> getPaginationToken(
            Optional<String> token) throws ExpiredTokenException {
        if (token.isPresent()) {
            return Optional.of(serializer.deserialize(token.get()));
        }
        return Optional.empty();
    }
}
