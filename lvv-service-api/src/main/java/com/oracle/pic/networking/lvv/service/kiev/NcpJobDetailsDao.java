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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/** Provides Kiev functions to help with managing the data stored in Kiev DB */
@Slf4j
@Singleton
@ToString
public class NcpJobDetailsDao {
    private static final int DEFAULT_PAGE_SIZE = 1000;
    private final ConfigurationStore<String, NcpJobDetails> ncpJobDetailsStore;
    private final MappedHashBucket<String, NcpJobDetails> ncpJobDetailsProvider;
    private PaginationTokenSerializer serializer;

    private final Index<NcpJobDetails.RackSerialIndex, NcpJobDetails> rackSerialIndex;

    private static final int BATCH_SIZE = 50;

    @Inject
    public NcpJobDetailsDao(
            @NonNull ConfigurationStore<String, NcpJobDetails> ncpJobDetailsStore,
            @NonNull PaginationTokenSerializer serializer,
            @NonNull MappedHashBucket<String, NcpJobDetails> ncpJobDetailsProvider) {
        this.ncpJobDetailsStore = ncpJobDetailsStore;
        this.serializer = serializer;
        this.ncpJobDetailsProvider = ncpJobDetailsProvider;
        this.rackSerialIndex =
                ncpJobDetailsProvider.getIndex(
                        NcpJobDetails.RACK_SERIAL_COLUMN_NAME, NcpJobDetails.RackSerialIndex.class);
    }

    // jobs is a map of deviceName -> Job ID
    // If jobID is empty, we set the Job status as NOT_TRIGGERED
    // If it's a PER_RACK_VALIDATION_JOB, the deviceName will be the rackSerialNumber
    public void addUpdateNcpJobDetails(
            @NonNull HashMap<String, String> jobs, String rackSerial, MetricsScope scope) {

        log.info("Get existing Job details for rack {}", rackSerial);

        Map<String, NcpJobDetails> existingJobDetails =
                getNcpJobDetailsForRack(rackSerial).stream()
                        .collect(Collectors.toMap(NcpJobDetails::getDeviceName, j -> j));

        List<Map.Entry<String, String>> jobList = new ArrayList<>(jobs.entrySet());

        for (int i = 0; i < jobList.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, jobList.size());
            List<Map.Entry<String, String>> batch = jobList.subList(i, end);

            try (Transaction txn = ncpJobDetailsStore.beginTransaction(rackSerial)) {

                log.info("Adding the following job details to database: {}", batch);

                scope.emit(MetricNames.NcpJobDetails.AddNcpJobDetails.name(), 1.0);

                for (Map.Entry<String, String> job : batch) {
                    String deviceName = job.getKey();

                    // If NCP JobId is empty, we set the Job Status is NOT_TRIGGERED (Calls made
                    // from RackDetailsService)
                    // If there's a JobId present, we set it's state to IN_PROGRESS  (Calls made
                    // from CablingValidationService)
                    JobStatus jobStatus =
                            Objects.equals(job.getValue(), "")
                                    ? JobStatus.NOT_TRIGGERED
                                    : JobStatus.IN_PROGRESS;

                    if (existingJobDetails.containsKey(deviceName)) {
                        // If the incoming job status is NOT_TRIGGERED, we don't want it to
                        // overwrite
                        // the existing job details
                        if (jobStatus != JobStatus.NOT_TRIGGERED) {
                            NcpJobDetails existingJob = existingJobDetails.get(deviceName);
                            existingJob.setJobId(jobs.get(deviceName));
                            existingJob.setJobStatus(jobStatus);
                            ncpJobDetailsStore.updateItem(txn, existingJob);
                        }
                    } else {
                        NcpJobDetails newJob =
                                NcpJobDetails.builder()
                                        .deviceName(deviceName)
                                        .jobId(jobs.get(deviceName))
                                        .jobStatus(jobStatus)
                                        .rackSerial(rackSerial)
                                        .build();
                        ncpJobDetailsStore.createItem(txn, newJob);
                    }
                }

                try {
                    txn.commit();
                } catch (CommitConflictException | DuplicateKeyException e) {
                    String message = "Failed to add/update NCP Jobs";
                    handleException(txn, e, message);
                    throw new RenderableException(
                            ErrorCode.ExternalServerInvalidResponse,
                            "DB transaction failed. Please try again.");
                }
            }
        }
    }

    public NcpJobDetails getNcpJobDetails(@NonNull String deviceName) {
        try {
            return ncpJobDetailsStore.getItem(deviceName);
        } catch (RuntimeException e) {
            log.info("NcpJob Details for device {} does not exist", deviceName);
            return null;
        }
    }

    public void updateNcpJobDetails(NcpJobDetails ncpJobDetails) {

        if (ncpJobDetails == null) {
            return;
        }

        try (Transaction txn = ncpJobDetailsStore.beginTransaction(ncpJobDetails.getDeviceName())) {

            ncpJobDetailsStore.updateItem(txn, ncpJobDetails);

            try {
                txn.commit();
            } catch (CommitConflictException | DuplicateKeyException e) {
                String message = "Failed to update NCP Job Status for device";
                handleException(txn, e, message);
                throw new RenderableException(
                        ErrorCode.ExternalServerInvalidResponse,
                        "DB transaction failed. Please try again.");
            }
        }
    }

    public void updateNcpJobStatus(@NonNull String deviceName, JobStatus newStatus) {

        try (Transaction txn = ncpJobDetailsStore.beginTransaction(deviceName)) {
            NcpJobDetails ncpJobDetails = getNcpJobDetails(deviceName);

            if (ncpJobDetails != null) {
                ncpJobDetails.setJobStatus(newStatus);
                ncpJobDetailsStore.updateItem(txn, ncpJobDetails);
            }

            try {
                txn.commit();
            } catch (CommitConflictException | DuplicateKeyException e) {
                String message = "Failed to update NCP Job Status for device";
                handleException(txn, e, message);
                throw new RenderableException(
                        ErrorCode.ExternalServerInvalidResponse,
                        "DB transaction failed. Please try again.");
            }
        }
    }

    public List<NcpJobDetails> getNcpJobDetailsForRack(@NonNull String rackSerial) {

        log.info("Getting Ncp Job details for rack {}", rackSerial);

        NcpJobDetails.RackSerialIndex prefix =
                NcpJobDetails.RackSerialIndex.builder().rackSerial(rackSerial).build();

        List<NcpJobDetails> result = Lists.newArrayList();
        Preconditions.checkNotNull(rackSerialIndex, "rackSerial is null");
        ScanPage<NcpJobDetails> page = rackSerialIndex.beginPrefixScan(prefix, DEFAULT_PAGE_SIZE);

        while (page != null) {
            KievRateLimiter.throttle();
            List<NcpJobDetails> pageResults = page.results();
            if (pageResults != null) {
                List<NcpJobDetails> filteredPage =
                        pageResults.stream()
                                .filter(job -> job != null)
                                .filter(job -> job.getRackSerial().equals(rackSerial))
                                .collect(Collectors.toList());
                result.addAll(filteredPage);
            }

            // Setup next page
            if (page.hasNext()) {
                page = rackSerialIndex.scan(page.paginationToken());
            } else {
                page = null;
            }
        }

        log.info("Found {} job details for rack {}: {}", result.size(), rackSerial, result);
        return result;
    }

    private void handleException(Transaction txn, Exception exception, String message) {
        txn.abort();
        log.error("Error message: {}", message, exception);
    }
}
