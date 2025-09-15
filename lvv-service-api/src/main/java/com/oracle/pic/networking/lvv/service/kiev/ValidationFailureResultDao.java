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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@ToString
public class ValidationFailureResultDao {
    private static final int DEFAULT_PAGE_SIZE = 1000;
    private final ConfigurationStore<ValidationFailureResult.LinkSource, ValidationFailureResult>
            validationResultStore;
    private final MappedHashBucket<ValidationFailureResult.LinkSource, ValidationFailureResult>
            validationResultProvider;

    private PaginationTokenSerializer serializer;

    private final Index<ValidationFailureResult.RackSerialIndex, ValidationFailureResult>
            rackSerialIndex;

    @Inject
    public ValidationFailureResultDao(
            @NonNull
                    ConfigurationStore<ValidationFailureResult.LinkSource, ValidationFailureResult>
                            validationResultStore,
            @NonNull PaginationTokenSerializer serializer,
            @NonNull
                    MappedHashBucket<ValidationFailureResult.LinkSource, ValidationFailureResult>
                            validationResultProvider) {
        this.validationResultStore = validationResultStore;
        this.validationResultProvider = validationResultProvider;
        this.serializer = serializer;
        this.rackSerialIndex =
                validationResultProvider.getIndex(
                        ValidationFailureResult.RACK_SERIAL_COLUMN_NAME,
                        ValidationFailureResult.RackSerialIndex.class);
    }

    private void updateExistingLinksStatusToUp(List<ValidationFailureResult> existingLinks) {

        log.info("Updating the following links status to UP: {}", existingLinks);
        if (existingLinks.isEmpty()) {
            return;
        }

        try (Transaction txn = validationResultStore.beginTransaction(existingLinks.toString())) {

            for (ValidationFailureResult existingLink : existingLinks) {
                ValidationFailureResult link =
                        validationResultStore.getItem(existingLink.getLinkSource());
                link.setLinkStatus(LinkStatus.UP);
                validationResultStore.updateItem(txn, link);
            }

            try {
                txn.commit();
            } catch (CommitConflictException | DuplicateKeyException e) {
                String message = "Failed to update existing links to UP";
                handleException(txn, e, message);
                throw new RenderableException(
                        ErrorCode.ExternalServerInvalidResponse,
                        "DB transaction failed. Please try again.");
            }
        }
    }

    private void updateOrAddDownLinks(
            List<ValidationFailureResult> links,
            Map<ValidationFailureResult.LinkSource, ValidationFailureResult> existingLinks,
            MetricsScope scope) {

        log.info("Adding/Updating the following links {}", links);
        if (links.isEmpty()) {
            scope.emit(MetricNames.AddValidationResults.NoMoreFailures, 1.0);
            return;
        }

        try (Transaction txn = validationResultStore.beginTransaction(links.toString())) {

            for (ValidationFailureResult link : links) {

                try (MetricsScope updateTimeScope =
                        MetricsScope.create(
                                MetricNames.MetricScopeNames.UPDATE_LINK_RESULTS.name())) {

                    updateTimeScope.withDimension("rackSerial", link.getRackSerial());
                    if (link.getLinkStatus() != LinkStatus.DOWN) {
                        // Any Validation Failure Result to be updated/added should have the link
                        // status
                        // as DOWN
                        throw new RenderableException(
                                ErrorCode.InternalError,
                                "Validation Failed link doesn't have Link Status set to DOWN");
                    }

                    if (!existingLinks.containsKey(link.getLinkSource())) {
                        Timestamp currValidationTime = Timestamp.from(Instant.now());
                        link.setLastValidatedTime(currValidationTime);
                        validationResultStore.createItem(txn, link);
                        log.info("Adding link result {}", link);
                    } else {
                        ValidationFailureResult existingLink =
                                existingLinks.get(link.getLinkSource());

                        Timestamp prevValidationTime = existingLink.getLastValidatedTime();
                        Timestamp currValidationTime = Timestamp.from(Instant.now());
                        long timeDiffFromLastValidation =
                                currValidationTime.getTime() - prevValidationTime.getTime();
                        timeDiffFromLastValidation /= 1000;
                        timeDiffFromLastValidation /= 60;

                        updateTimeScope.withDimension(
                                "linkSource", link.getLinkSource().toString());
                        updateTimeScope.emit(
                                MetricNames.AddValidationResults.TimeFromLastValidation.name(),
                                timeDiffFromLastValidation);

                        link.setLastValidatedTime(currValidationTime);
                        validationResultStore.updateItem(txn, link);
                        log.info("{} Link Result being updated to: {}", existingLink, link);
                    }
                }
            }

            try {
                txn.commit();
            } catch (CommitConflictException | DuplicateKeyException e) {
                String message = "Failed to add/update existing links to DOWN";
                scope.emit(MetricNames.AddValidationResults.KievResultUpdateFailure.name(), 1.0);
                handleException(txn, e, message);
                throw new RenderableException(
                        ErrorCode.ExternalServerInvalidResponse,
                        "DB transaction failed. Please try again.");
            }
        }
    }

    public void addValidationFailureResultsForRack(
            @NonNull List<ValidationFailureResult> results,
            @NonNull String rackSerial,
            MetricsScope scope) {

        Map<ValidationFailureResult.LinkSource, ValidationFailureResult> existingLinks =
                getValidationFailuresByRack(rackSerial, false).stream()
                        .collect(Collectors.toMap(ValidationFailureResult::getLinkSource, l -> l));

        // First, we update all the links to UP, and then based on the new results we get, we either
        // update a link to DOWN or add a new link with DOWN status
        updateExistingLinksStatusToUp(new ArrayList<>(existingLinks.values()));

        List<ValidationFailureResult> newDownLinks =
                results.stream().filter(link -> link.getLinkStatus() == LinkStatus.DOWN).toList();
        updateOrAddDownLinks(newDownLinks, existingLinks, scope);
    }

    public void updateValidationFailureResultsForDevices(
            @NonNull List<ValidationFailureResult> results, MetricsScope scope) {

        Map<String, List<ValidationFailureResult>> newLinks =
                results.stream()
                        .collect(Collectors.groupingBy(l -> l.getLinkSource().getDeviceAName()));

        log.info(
                "Loop through the results of all the devices we want to validate: {}",
                newLinks.keySet());
        for (Map.Entry<String, List<ValidationFailureResult>> entry : newLinks.entrySet()) {
            String deviceName = entry.getKey();
            List<ValidationFailureResult> newDeviceLinks = entry.getValue();

            Map<ValidationFailureResult.LinkSource, ValidationFailureResult> existingLinks =
                    getValidationFailuresByDevice(deviceName, false).stream()
                            .collect(
                                    Collectors.toMap(
                                            ValidationFailureResult::getLinkSource, l -> l));
            // First, we update all the links from the device to UP, and then based on the new
            // results we get, we either update a link to DOWN or add a new link with DOWN status
            updateExistingLinksStatusToUp(new ArrayList<>(existingLinks.values()));
            // We check if the first link on the device has the status DOWN, and only update those
            // devices
            // Devices with no links DOWN, will have the first link as a minimal
            // ValidationFailureResult object with the device name and Link Status as UP
            if (!newDeviceLinks.isEmpty()
                    && newDeviceLinks.get(0).getLinkStatus().equals(LinkStatus.DOWN)) {
                updateOrAddDownLinks(newDeviceLinks, existingLinks, scope);
            }
        }
    }

    public List<ValidationFailureResult> getValidationFailuresByDevice(
            String deviceName, boolean onlyDown) {
        List<ValidationFailureResult> result = Lists.newArrayList();

        log.info("Finding validation failures for device {}", deviceName);

        Preconditions.checkNotNull(deviceName, "deviceName is null");

        ValidationFailureResult.LinkSource prefix =
                ValidationFailureResult.LinkSource.builder()
                        .deviceAName(deviceName)
                        .deviceAPort(null)
                        .build();

        ScanPage<ValidationFailureResult> page =
                validationResultProvider.beginPrefixScan(prefix, DEFAULT_PAGE_SIZE);

        while (page != null) {
            KievRateLimiter.throttle();
            List<ValidationFailureResult> pageResults = page.results();
            if (pageResults != null) {
                List<ValidationFailureResult> filteredPage =
                        pageResults.stream()
                                .filter(Objects::nonNull)
                                .filter(
                                        link ->
                                                link.getLinkSource()
                                                        .getDeviceAName()
                                                        .equals(deviceName))
                                .filter(
                                        link ->
                                                !onlyDown
                                                        || link.getLinkStatus()
                                                                .equals(LinkStatus.DOWN))
                                .toList();

                result.addAll(filteredPage);
            }

            // Setup next page
            if (page.hasNext()) {
                page = validationResultProvider.scan(page.paginationToken());
            } else {
                page = null;
            }
        }

        log.info("Validation failures found for device {}: {}", deviceName, result);

        return result;
    }

    public List<ValidationFailureResult> getValidationFailuresByRack(
            @NonNull String rackSerial, @NonNull boolean onlyDown) {
        ValidationFailureResult.RackSerialIndex prefix =
                ValidationFailureResult.RackSerialIndex.builder().rackSerial(rackSerial).build();

        List<ValidationFailureResult> result = Lists.newArrayList();

        log.info("Finding validation failures for rack {}", rackSerial);

        Preconditions.checkNotNull(rackSerialIndex, "rackSerialIndex is null");
        ScanPage<ValidationFailureResult> page =
                rackSerialIndex.beginPrefixScan(prefix, DEFAULT_PAGE_SIZE);
        while (page != null) {
            KievRateLimiter.throttle();
            List<ValidationFailureResult> pageResults = page.results();
            if (pageResults != null) {
                List<ValidationFailureResult> filteredPage =
                        pageResults.stream()
                                .filter(Objects::nonNull)
                                .filter(link -> link.getRackSerial().equals(rackSerial))
                                .filter(
                                        link ->
                                                !onlyDown
                                                        || link.getLinkStatus()
                                                                .equals(LinkStatus.DOWN))
                                .toList();

                result.addAll(filteredPage);
            }

            // Setup next page
            if (page.hasNext()) {
                page = rackSerialIndex.scan(page.paginationToken());
            } else {
                page = null;
            }
        }

        log.info("Existing Validation failures found for rack {}: {}", rackSerial, result);

        return result;
    }

    private void handleException(Transaction txn, Exception exception, String message) {
        txn.abort();
        log.error("Error message: {}", message, exception);
    }
}
