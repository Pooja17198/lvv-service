package com.oracle.pic.networking.lvv.service.kiev;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.oracle.pic.commons.exceptions.server.ErrorCode;
import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.kiev.Transaction;
import com.oracle.pic.kiev.exceptions.CommitConflictException;
import com.oracle.pic.kiev.exceptions.DuplicateKeyException;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import com.oracle.pic.networking.lvv.service.dependencies.notificationservice.NotificationServiceHelper;
import java.sql.Timestamp;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * DAO for building-level monitoring:<br>
 * stores lastUpdated for each building(TIMESTAMP)<br>
 *
 * <p>HashKey: building (STRING)
 */
@Slf4j
@Singleton
@ToString
public class MonitoringDao {
    private final ConfigurationStore<String, Monitoring> monitoringStore;
    private final NotificationServiceHelper notificationServiceHelper;

    @Inject
    public MonitoringDao(
            @NonNull ConfigurationStore<String, Monitoring> monitoringStore,
            @NonNull NotificationServiceHelper notificationServiceHelper) {
        this.monitoringStore = monitoringStore;
        this.notificationServiceHelper = notificationServiceHelper;
    }

    public Monitoring getRowForBuilding(@NonNull String building) {
        try (MetricsScope scope =
                MetricsScope.create(MetricNames.MetricScopeNames.MONITORING.name())) {
            scope.emit(MetricNames.Monitoring.GetMonitoringRecord.name(), 1.0);
            Monitoring item = monitoringStore.getItem(building);
            scope.recordSuccess();
            return item;
        } catch (RuntimeException e) {
            log.info(
                    "No Monitoring record found or error while fetching for building {}",
                    building,
                    e);
            return null;
        }
    }

    public void upsertRowForBuilding(
            String building,
            Timestamp lastUpdated,
            String topicOcid,
            Timestamp lastEmailSentAt,
            Transaction txn) {

        Monitoring existing = getRowForBuilding(building);

        try (MetricsScope scope =
                MetricsScope.create(MetricNames.MetricScopeNames.MONITORING.name())) {
            if (lastUpdated == null) {
                lastUpdated = existing == null ? null : existing.getLastUpdated();
            }
            if (topicOcid == null) {
                topicOcid = existing == null ? null : existing.getTopicOcid();
            }
            if (lastEmailSentAt == null) {
                lastEmailSentAt = existing == null ? null : existing.getLastEmailSentAt();
            }

            Monitoring toPersist =
                    Monitoring.builder()
                            .building(building)
                            .lastUpdated(lastUpdated)
                            .topicOcid(topicOcid)
                            .lastEmailSentAt(lastEmailSentAt)
                            .build();

            if (existing == null) {
                monitoringStore.createItem(txn, toPersist);
                log.info("Created Monitoring for {} -> {}", building, toPersist);
            } else {
                monitoringStore.updateItem(txn, toPersist);
                log.info("Updated Monitoring for {} -> {}", building, toPersist);
            }
            scope.emit(MetricNames.Monitoring.UpsertMonitoringRecord.name(), 1.0);
            scope.recordSuccess();
        }
    }

    public void upsertLastUpdated(@NonNull String building, @NonNull Timestamp lastUpdated) {
        log.info("Upserting lastUpdated: {} for building {}", lastUpdated, building);

        try (Transaction txn = monitoringStore.beginTransaction(building)) {
            upsertRowForBuilding(building, lastUpdated, null, null, txn);
            try {
                txn.commit();
            } catch (CommitConflictException | DuplicateKeyException e) {
                txn.abort();
                log.error(
                        "Failed to commit after upserting lastUpdated for building {}",
                        building,
                        e);
                throw new RenderableException(
                        ErrorCode.ExternalServerInvalidResponse,
                        "Failed to commit after upserting lastUpdated for building {}",
                        building);
            }
        }
    }

    public String getTopicOcid(@NonNull String building) {
        Monitoring existing = getRowForBuilding(building);
        if (existing != null) {
            return existing.getTopicOcid();
        }
        return null;
    }

    public void upsertTopicOcid(
            @NonNull String building, @NonNull String topicOcid, Transaction txn) {
        log.info("Upserting topicOcid for building {} to {}", building, topicOcid);
        upsertRowForBuilding(building, null, topicOcid, null, txn);
    }

    public void upsertLastEmailSentAt(
            @NonNull String building, @NonNull Timestamp lastEmailSentAt) {
        log.info("Upserting lastEmailSentAt: {} for building {}", lastEmailSentAt, building);

        try (Transaction txn = monitoringStore.beginTransaction(building)) {
            upsertRowForBuilding(building, null, null, lastEmailSentAt, txn);
            try {
                txn.commit();
            } catch (CommitConflictException | DuplicateKeyException e) {
                txn.abort();
                log.error(
                        "Failed to commit after upserting lastEmailSentAt for building {}",
                        building,
                        e);
                throw new RenderableException(
                        ErrorCode.ExternalServerInvalidResponse,
                        "Failed to commit after upserting lastEmailSentAt for building {}",
                        building);
            }
        }
    }

    public String ensureTopicExistsForBuilding(String building, Transaction txn) {
        String topicOcid = getTopicOcid(building);
        if (topicOcid != null) {
            log.info("Topic for building {} already exists, topicOcid {}", building, topicOcid);
            return topicOcid;
        }
        topicOcid =
                notificationServiceHelper.createTopic(
                        "network-alerting-building-" + building,
                        "Topic for sending link down alerts for building " + building);
        if (topicOcid != null) {
            upsertTopicOcid(building, topicOcid, txn);
        } else {
            log.info(
                    "Either some error occurred during topic creation OR ONS is disabled by config so topic not created for building {}",
                    building);
        }
        return topicOcid;
    }
}
