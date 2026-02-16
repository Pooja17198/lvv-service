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
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * DAO for per-link BadLinks detail rows (building, device, remoteDevice, jiraTicket).<br>
 * HashKey: jiraTicket <br>
 * Index: building (to list all rows for a building)<br>
 */
@Slf4j
@Singleton
@ToString
public class BadLinksDao {

    private static final int DEFAULT_PAGE_SIZE = 1000;
    private static final int MAX_WRITES_PER_TRANSACTION = 50;

    private final ConfigurationStore<String, BadLinks> badLinksStore;
    private final MappedHashBucket<String, BadLinks> badLinksBucket;
    private final PaginationTokenSerializer serializer;

    private final Index<BadLinks.BuildingIndex, BadLinks> buildingIndex;

    @Inject
    public BadLinksDao(
            @NonNull ConfigurationStore<String, BadLinks> badLinksStore,
            @NonNull PaginationTokenSerializer serializer,
            @NonNull MappedHashBucket<String, BadLinks> badLinksBucket) {
        this.badLinksStore = badLinksStore;
        this.serializer = serializer;
        this.badLinksBucket = badLinksBucket;
        this.buildingIndex =
                badLinksBucket.getIndex(
                        BadLinks.BUILDING_COLUMN_NAME_IDX, BadLinks.BuildingIndex.class);
    }

    /** List all bad-link rows for a building. */
    public List<BadLinks> getBadLinksForBuilding(@NonNull String building) {
        try (MetricsScope scope =
                MetricsScope.create(MetricNames.MetricScopeNames.BAD_LINKS.name())) {

            log.info("Getting Bad links for building {} from the database", building);

            scope.emit(MetricNames.BadLinks.GetForBuilding.name(), 1.0);

            Preconditions.checkNotNull(buildingIndex, "buildingIndex is null");

            List<BadLinks> result = Lists.newArrayList();

            BadLinks.BuildingIndex prefix =
                    BadLinks.BuildingIndex.builder().building(building).build();

            ScanPage<BadLinks> page = buildingIndex.beginPrefixScan(prefix, DEFAULT_PAGE_SIZE);
            while (page != null) {
                KievRateLimiter.throttle();
                List<BadLinks> pageResults = page.results();
                if (pageResults != null) {
                    List<BadLinks> filteredPage =
                            pageResults.stream()
                                    .filter(Objects::nonNull)
                                    .filter(row -> building.equals(row.getBuilding()))
                                    .toList();
                    result.addAll(filteredPage);
                }
                if (page.hasNext()) {
                    page = buildingIndex.scan(page.paginationToken());
                } else {
                    page = null;
                }
            }

            scope.recordSuccess();

            log.info("Found {} bad-links for building {}: {}", result.size(), building, result);

            return result;
        }
    }

    /**
     * Delete only the specified rows by their jira tickets for a building. Batches deletes to
     * respect transaction limits.
     */
    public void deleteRowsByJiraTickets(
            @NonNull String building, @NonNull List<String> jiraTickets) {
        if (jiraTickets.isEmpty()) {
            return;
        }
        try (MetricsScope scope =
                MetricsScope.create(MetricNames.MetricScopeNames.BAD_LINKS.name())) {
            scope.emit(MetricNames.BadLinks.DeleteByIds.name(), 1.0);

            log.info("Deleting {} bad-link rows for building {}", jiraTickets.size(), building);
            int deletedCount = 0;

            // Delete in batches to respect transaction write limits without allocating extra lists
            for (int startIndexOfBatch = 0;
                    startIndexOfBatch < jiraTickets.size();
                    startIndexOfBatch += MAX_WRITES_PER_TRANSACTION) {
                int deletedCountInThisBatch = 0;
                int end =
                        Math.min(
                                startIndexOfBatch + MAX_WRITES_PER_TRANSACTION, jiraTickets.size());
                KievRateLimiter.throttle();
                try (Transaction txn = badLinksStore.beginTransaction(building)) {
                    for (int indexInJiraTicketsList = startIndexOfBatch;
                            indexInJiraTicketsList < end;
                            indexInJiraTicketsList++) {
                        String ticket = jiraTickets.get(indexInJiraTicketsList);
                        if (ticket == null || ticket.isBlank()) {
                            continue;
                        }
                        badLinksStore.deleteItem(txn, ticket);
                        deletedCountInThisBatch++;
                    }
                    try {
                        txn.commit();
                        log.info("Deleted {} bad-link rows in batch", deletedCountInThisBatch);
                        scope.emit(MetricNames.BadLinks.DeleteIdsBatchSuccess.name(), 1.0);
                    } catch (CommitConflictException | DuplicateKeyException e) {
                        scope.emit(MetricNames.BadLinks.DeleteIdsBatchFailure.name(), 1.0);
                        txn.abort();
                        log.error(
                                "Failed to commit after deleting one or more bad-link rows for building {}",
                                building,
                                e);
                        throw new RenderableException(
                                ErrorCode.ExternalServerInvalidResponse,
                                "Failed to delete badLinks' details in Kiev.");
                    }
                }
                deletedCount += deletedCountInThisBatch;
            }

            scope.recordSuccess();
            log.info("Deleted total {} bad-link rows for building {}.", deletedCount, building);
        }
    }

    /**
     * Insert the provided rows in batches for the building. Assumes caller filtered out any
     * duplicates by id. If duplicates are encountered due to race, the batch will fail.
     */
    public void insertRows(@NonNull String building, @NonNull List<BadLinks> rows) {
        if (rows.isEmpty()) {
            return;
        }
        try (MetricsScope scope =
                MetricsScope.create(MetricNames.MetricScopeNames.BAD_LINKS.name())) {
            scope.emit(MetricNames.BadLinks.InsertRows.name(), 1.0);

            log.info(
                    "Inserting {} bad-links rows for building {}, row details: {}",
                    rows.size(),
                    building,
                    rows);

            int insertedCount = 0;
            // Insert in batches without allocating extra lists
            for (int startIndexOfBatch = 0;
                    startIndexOfBatch < rows.size();
                    startIndexOfBatch += MAX_WRITES_PER_TRANSACTION) {
                int insertedCountInThisBatch = 0;
                int end = Math.min(startIndexOfBatch + MAX_WRITES_PER_TRANSACTION, rows.size());
                KievRateLimiter.throttle();
                try (Transaction txn = badLinksStore.beginTransaction(building)) {
                    for (int indexInJiraTicketsList = startIndexOfBatch;
                            indexInJiraTicketsList < end;
                            indexInJiraTicketsList++) {
                        BadLinks row = rows.get(indexInJiraTicketsList);
                        if (row == null) {
                            continue;
                        }
                        badLinksStore.createItem(txn, row);
                        insertedCountInThisBatch++;
                    }
                    try {
                        txn.commit();
                        log.info("Inserted {} bad-link rows in batch", insertedCountInThisBatch);
                        scope.emit(MetricNames.BadLinks.InsertRowsBatchSuccess.name(), 1.0);
                    } catch (CommitConflictException | DuplicateKeyException e) {
                        scope.emit(MetricNames.BadLinks.InsertRowsBatchFailure.name(), 1.0);
                        txn.abort();
                        log.error(
                                "Failed to commit after inserting one or more bad-link rows for building {}",
                                building,
                                e);
                        throw new RenderableException(
                                ErrorCode.ExternalServerInvalidResponse,
                                "Failed to store badLinks' details in Kiev.");
                    }
                }
                insertedCount += insertedCountInThisBatch;
            }

            scope.recordSuccess();
            log.info("Inserted total {} bad-link rows for building {}.", insertedCount, building);
        }
    }
}
