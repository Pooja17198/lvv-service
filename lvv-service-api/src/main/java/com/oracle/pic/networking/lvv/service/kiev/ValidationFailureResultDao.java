package com.oracle.pic.networking.lvv.service.kiev;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@ToString
public class ValidationFailureResultDao {
    private static final int DEFAULT_PAGE_SIZE = 1000;
    private final ConfigurationStore<String, ValidationFailureResult> validationResultStore;
    private final MappedHashBucket<String, ValidationFailureResult> validationResultProvider;

    private PaginationTokenSerializer serializer;

    private final Index<ValidationFailureResult.RackSerialIndex, ValidationFailureResult>
            rackSerialIndex;

    private static final int BATCH_SIZE = 50;

    @Inject
    public ValidationFailureResultDao(
            @NonNull ConfigurationStore<String, ValidationFailureResult> validationResultStore,
            @NonNull PaginationTokenSerializer serializer,
            @NonNull MappedHashBucket<String, ValidationFailureResult> validationResultProvider) {
        this.validationResultStore = validationResultStore;
        this.validationResultProvider = validationResultProvider;
        this.serializer = serializer;
        this.rackSerialIndex =
                validationResultProvider.getIndex(
                        ValidationFailureResult.RACK_SERIAL_COLUMN_NAME,
                        ValidationFailureResult.RackSerialIndex.class);
    }

    public void addValidationFailureResultForDevice(
            String rackSerial,
            String deviceName,
            Map<String, List<Map<String, String>>> result,
            MetricsScope scope) {
        try (Transaction txn = validationResultStore.beginTransaction(deviceName)) {

            log.info("Adding results for device {}. Result: {}", deviceName, result);

            Timestamp now = Timestamp.from(Instant.now());
            ValidationFailureResult validationFailureResult =
                    ValidationFailureResult.builder()
                            .deviceName(deviceName)
                            .validationResults(result)
                            .firstValidatedTime(now)
                            .lastValidatedTime(now)
                            .numberOfValidations(1)
                            .rackSerial(rackSerial)
                            .build();

            validationResultStore.createItem(txn, validationFailureResult);

            try {
                txn.commit();
                log.info("Validation result for device {} has been added successfully", deviceName);
            } catch (CommitConflictException | DuplicateKeyException exception) {
                String message = "Failed to add validation result for device " + deviceName;
                scope.emit(MetricNames.AddValidationResults.KievResultUpdateFailure.name(), 1.0);
                handleException(txn, exception, message);
                throw new RenderableException(
                        ErrorCode.ExternalServerInvalidResponse,
                        "Failed to add validation result in Kiev. Please try again.");
            }
        }
    }

    public void updateValidationFailureResultsForDevice(
            ValidationFailureResult existingResult,
            String deviceName,
            Map<String, List<Map<String, String>>> result,
            MetricsScope scope) {
        try (Transaction txn = validationResultStore.beginTransaction(deviceName)) {

            log.info("Updating results for device {}. Result: {}", deviceName, result);

            existingResult.setValidationResults(result);
            existingResult.setNumberOfValidations(existingResult.getNumberOfValidations() + 1);
            existingResult.setLastValidatedTime(Timestamp.from(Instant.now()));

            validationResultStore.updateItem(txn, existingResult);

            try {
                txn.commit();
                log.info(
                        "Validation result for device {} has been updated successfully",
                        deviceName);
            } catch (CommitConflictException | DuplicateKeyException exception) {
                String message = "Failed to update validation result for device " + deviceName;
                scope.emit(MetricNames.AddValidationResults.KievResultUpdateFailure.name(), 1.0);
                handleException(txn, exception, message);
                throw new RenderableException(
                        ErrorCode.ExternalServerInvalidResponse,
                        "Failed to update validation result in Kiev. Please try again.");
            }
        }
    }

    public void addUpdateValidationFailureResultsForDevices(
            String rackSerial,
            @NonNull Map<String, Map<String, List<Map<String, String>>>> results,
            MetricsScope scope) {

        results.forEach(
                (String deviceName, Map<String, List<Map<String, String>>> result) -> {
                    ValidationFailureResult existingDeviceResult = null;

                    try {
                        existingDeviceResult = validationResultStore.getItem(deviceName);
                    } catch (RuntimeException e) {
                        log.info("Result for device {} doesn't exist. Adding one", deviceName);
                    }

                    if (existingDeviceResult == null) {
                        addValidationFailureResultForDevice(rackSerial, deviceName, result, scope);
                    } else {
                        updateValidationFailureResultsForDevice(
                                existingDeviceResult, deviceName, result, scope);
                    }
                });
    }

    public Object getValidationFailuresByRack(@NonNull String rackSerial) {
        ValidationFailureResult.RackSerialIndex prefix =
                ValidationFailureResult.RackSerialIndex.builder().rackSerial(rackSerial).build();

        HashMap<String, Object> result = new HashMap<>();

        log.info("Finding validation failures for rack {}", rackSerial);

        Preconditions.checkNotNull(rackSerialIndex, "rackSerialIndex is null");
        ScanPage<ValidationFailureResult> page =
                rackSerialIndex.beginPrefixScan(prefix, DEFAULT_PAGE_SIZE);
        while (page != null) {
            KievRateLimiter.throttle();
            List<ValidationFailureResult> pageResults = page.results();
            if (pageResults != null) {
                pageResults.stream()
                        .filter(Objects::nonNull)
                        .filter(device -> Objects.equals(device.getRackSerial(), rackSerial))
                        .forEach(
                                device ->
                                        result.put(
                                                device.getDeviceName(), buildDeviceResult(device)));
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

    private Map<String, Object> buildDeviceResult(ValidationFailureResult deviceResult) {
        Map<String, Object> result = new HashMap<>(deviceResult.getValidationResults());
        result.put("Last Validated", deviceResult.getLastValidatedTime());
        return result;
    }

    private void handleException(Transaction txn, Exception exception, String message) {
        txn.abort();
        log.error("Error message: {}", message, exception);
    }
}
