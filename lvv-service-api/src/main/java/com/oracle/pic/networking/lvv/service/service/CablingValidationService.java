package com.oracle.pic.networking.lvv.service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.oracle.pic.commons.exceptions.server.ErrorCode;
import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.networking.lvv.service.dependencies.jira.JiraSDService;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import com.oracle.pic.networking.lvv.service.dependencies.ncp.NcpClientHelper;
import com.oracle.pic.networking.lvv.service.kiev.JobStatus;
import com.oracle.pic.networking.lvv.service.kiev.NcpJobDetails;
import com.oracle.pic.networking.lvv.service.kiev.NcpJobDetailsDao;
import com.oracle.pic.networking.lvv.service.kiev.ValidationFailureResultDao;
import com.oracle.pic.networking.lvv.service.models.ncp.JobType;
import com.oracle.pic.networking.lvv.service.utils.GeneralUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@ToString
public class CablingValidationService {

    @lombok.Builder
    @lombok.Value
    private static class ValidationMetricContext {
        String region;
        String building;
        String rackNumber;
        String rackSerial;
    }

    @lombok.Builder
    @lombok.Value
    private static class ValidationJobExecutionContext {
        JobStatus jobStatus;
        String jobType;
        String startDate;
        String endDate;
    }

    private final NcpClientHelper ncpClientHelper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final List<String> testSuites =
            List.of("dcs_rack_validation", "ls_rack_validation");
    private final JiraSDService jiraSDService;
    private final ValidationFailureResultDao validationFailureResultDao;
    private final NcpJobDetailsDao ncpJobDetailsDao;

    @Inject
    public CablingValidationService(
            NcpClientHelper ncpClientHelper,
            JiraSDService jiraSDService,
            ValidationFailureResultDao validationFailureResultDao,
            NcpJobDetailsDao ncpJobDetailsDao) {
        this.ncpClientHelper = ncpClientHelper;
        this.jiraSDService = jiraSDService;
        this.validationFailureResultDao = validationFailureResultDao;
        this.ncpJobDetailsDao = ncpJobDetailsDao;
    }

    public void validateCablingTasks(
            String regionName,
            String building,
            String rackSerialNumber,
            String rackLocation,
            List<String> deviceNames,
            MetricsScope scope) {
        try {
            scope.withDimension("buildingName", building);
            scope.withDimension("rackLocation", rackLocation);
            scope.withDimension("region", GeneralUtils.getRegionInternalName(regionName));

            String payload =
                    objectMapper.writeValueAsString(
                            Map.of(
                                    "testSuites", testSuites,
                                    "rackNumber", rackLocation,
                                    "building", building));

            String jobType = JobType.HEALTH_CHECK;
            List<String> devicesList = new ArrayList<>(deviceNames);

            if (devicesList.isEmpty()) {
                devicesList =
                        ncpJobDetailsDao.getNcpJobDetailsForRack(rackSerialNumber).stream()
                                .map(NcpJobDetails::getDeviceName)
                                .collect(Collectors.toList());

                // If we are not able to fetch device names, we create a PER_RACK_VALIDATION_JOB
                // Will happen when a region is in ViBE phase, where Plan Service is not reachable
                // to fetch device list
                if (devicesList.isEmpty()) {
                    jobType = JobType.PER_RACK_VALIDATION_JOB;

                    payload =
                            objectMapper.writeValueAsString(
                                    Map.of(
                                            "rack_number", rackLocation,
                                            "building", building,
                                            "dry_run", true));
                }
            }

            scope.emit(MetricNames.ValidateCables.ValidateCable.name(), 1.0);

            log.info("Validating Building:Rack {}:{}", building, rackLocation);

            // If an NCP job is already In Progress for any of the devices/rack, we skip creating a
            // job for it
            if (jobType.equals(JobType.PER_RACK_VALIDATION_JOB)) {
                if (ncpJobDetailsDao.getNcpJobDetails(rackSerialNumber) != null
                        && ncpJobDetailsDao.getNcpJobDetails(rackSerialNumber).getJobStatus()
                                == JobStatus.IN_PROGRESS) {
                    return;
                }
            } else {
                devicesList.removeIf(
                        deviceName ->
                                ncpJobDetailsDao.getNcpJobDetails(deviceName) != null
                                        && ncpJobDetailsDao
                                                        .getNcpJobDetails(deviceName)
                                                        .getJobStatus()
                                                == JobStatus.IN_PROGRESS);
                if (devicesList.isEmpty()) {
                    return;
                }
            }

            HashMap<String, String> jobs =
                    ncpClientHelper.createJobs(
                            jobType, rackSerialNumber, payload, devicesList, scope, regionName);

            ncpJobDetailsDao.addUpdateNcpJobDetails(jobs, rackSerialNumber, scope);

        } catch (JsonProcessingException e) {
            log.error("Error while generating payload ", e);
            throw new RenderableException(
                    ErrorCode.InternalError, "Error while generating payload for NCP Job");
        }
    }

    public void updateValidationJobStatus(
            Map<String, NcpClientHelper.ValidationJobRuntimeInfo> jobStatus,
            String rackSerialNumber,
            String region,
            String building,
            String rackUnit,
            Boolean lastAttempt,
            MetricsScope scope) {

        ValidationMetricContext metricContext =
                ValidationMetricContext.builder()
                        .region(GeneralUtils.getRegionInternalName(region))
                        .building(building)
                        .rackNumber(rackUnit)
                        .rackSerial(rackSerialNumber)
                        .build();

        for (Map.Entry<String, NcpClientHelper.ValidationJobRuntimeInfo> entry :
                jobStatus.entrySet()) {

            NcpJobDetails currJobDetails = ncpJobDetailsDao.getNcpJobDetails(entry.getKey());
            if (currJobDetails.getJobStatus() == JobStatus.IN_PROGRESS) {
                ValidationJobExecutionContext executionContext =
                        ValidationJobExecutionContext.builder()
                                .jobStatus(entry.getValue().getJobStatus())
                                .jobType(entry.getValue().getJobType())
                                .startDate(entry.getValue().getStartDate())
                                .endDate(entry.getValue().getEndDate())
                                .build();

                if (entry.getValue().getJobStatus().equals(JobStatus.FAILED)) {
                    // Nothing to do here, validation job has failed
                    currJobDetails.setJobStatus(JobStatus.FAILED);
                    ncpJobDetailsDao.updateNcpJobDetails(currJobDetails);
                } else if (entry.getValue().getJobStatus().equals(JobStatus.COMPLETED)) {

                    // If the job has just completed, we update the database with the new results
                    try (MetricsScope addResultsScope =
                            MetricsScope.create(
                                    MetricNames.MetricScopeNames.ADD_VALIDATION_RESULTS.name())) {

                        addResultsScope.withDimension("rackSerialNumber", rackSerialNumber);
                        addResultsScope.withDimension(
                                "region", GeneralUtils.getRegionInternalName(region));

                        // Parse the output to fetch the results
                        Map<String, Map<String, List<Map<String, String>>>> output =
                                ncpClientHelper.getNcpJobOutput(
                                        currJobDetails.getJobId(),
                                        region,
                                        rackSerialNumber,
                                        rackUnit);

                        // Update the results to the Database
                        validationFailureResultDao.addUpdateValidationFailureResultsForDevices(
                                rackSerialNumber, output, addResultsScope);
                        scope.recordSuccess();
                    } catch (RenderableException e) {
                        if (isRecoverableResultParsingError(e)) {
                            log.error(
                                    "Failed to parse NCP result for device {} jobId {}. Marking job as FAILED.",
                                    currJobDetails.getDeviceName(),
                                    currJobDetails.getJobId(),
                                    e);
                            currJobDetails.setJobStatus(JobStatus.FAILED);
                            ncpJobDetailsDao.updateNcpJobDetails(currJobDetails);
                            continue;
                        }
                        throw e;
                    }

                    // Check if Device status was updated to Unreachable
                    if (ncpJobDetailsDao.getNcpJobDetails(entry.getKey()).getJobStatus()
                            != JobStatus.DEVICE_UNREACHABLE) {
                        emitValidationDurationMetricIfEligible(
                                currJobDetails, executionContext, metricContext, scope);
                        currJobDetails.setJobStatus(JobStatus.COMPLETED);
                        ncpJobDetailsDao.updateNcpJobDetails(currJobDetails);
                    }

                } else if (entry.getValue().getJobStatus().equals(JobStatus.IN_PROGRESS)) {
                    // We don't do anything here,and just wait for job to complete
                    // But, if it's the last attempt of polling the job, that means we've been
                    // waiting for the job to complete for quite some time,
                    // And we mark the device as Unreachable
                    if (lastAttempt) {
                        log.info(
                                "Last polling attempt. Setting the Job status to DEVICE_UNREACHABLE since it's taking too long to validate");
                        currJobDetails.setJobStatus(JobStatus.DEVICE_UNREACHABLE);
                        ncpJobDetailsDao.updateNcpJobDetails(currJobDetails);
                    }
                }
            }
        }
    }

    private boolean isRecoverableResultParsingError(RenderableException exception) {
        if (exception == null || exception.getMessage() == null) {
            return false;
        }

        String message = exception.getMessage();
        return "Failed to parse NCP job result as JSON".equals(message)
                || message.contains("Error in unexpected format");
    }

    private void emitValidationDurationMetricIfEligible(
            NcpJobDetails currJobDetails,
            ValidationJobExecutionContext executionContext,
            ValidationMetricContext metricContext,
            MetricsScope scope) {

        if (currJobDetails == null) {
            return;
        }

        if (executionContext.getStartDate() == null || executionContext.getEndDate() == null) {
            String missingTimestamps =
                    Stream.of(
                                    executionContext.getStartDate() == null ? "startDate" : null,
                                    executionContext.getEndDate() == null ? "endDate" : null)
                            .filter(Objects::nonNull)
                            .collect(Collectors.joining(","));
            log.warn(
                    "Skipping validation duration metric due to missing timestamp(s) [{}] for device {} jobId {}",
                    missingTimestamps,
                    currJobDetails.getDeviceName(),
                    currJobDetails.getJobId());
            return;
        }

        long durationMillis;
        try {
            durationMillis =
                    Instant.parse(executionContext.getEndDate()).toEpochMilli()
                            - Instant.parse(executionContext.getStartDate()).toEpochMilli();
        } catch (Exception e) {
            log.warn(
                    "Skipping validation duration metric due to invalid timestamp format for device {} jobId {} startDate {} endDate {}",
                    currJobDetails.getDeviceName(),
                    currJobDetails.getJobId(),
                    executionContext.getStartDate(),
                    executionContext.getEndDate(),
                    e);
            return;
        }

        if (durationMillis < 0) {
            log.warn(
                    "Skipping validation duration metric due to negative duration for device {} jobId {} startDate {} endDate {}",
                    currJobDetails.getDeviceName(),
                    currJobDetails.getJobId(),
                    executionContext.getStartDate(),
                    executionContext.getEndDate());
            return;
        }

        if (metricContext.getRackSerial() != null) {
            scope.withDimension("rackSerial", metricContext.getRackSerial());
        }

        if (JobType.PER_RACK_VALIDATION_JOB.equals(executionContext.getJobType())) {
            scope.emit(
                    MetricNames.ValidationDuration.RackValidationDuration.name(),
                    (double) durationMillis);
        } else {
            if (currJobDetails.getDeviceName() != null) {
                scope.withDimension("device", currJobDetails.getDeviceName());
            }
            scope.emit(
                    MetricNames.ValidationDuration.DeviceValidationDuration.name(),
                    (double) durationMillis);
        }
    }

    public Map<String, JobStatus> getValidationJobStatus(
            MetricsScope scope,
            String region,
            String rackSerialNumber,
            String rackUnit,
            Boolean lastAttempt) {

        return getValidationJobStatus(scope, region, null, rackSerialNumber, rackUnit, lastAttempt);
    }

    public Map<String, JobStatus> getValidationJobStatus(
            MetricsScope scope,
            String region,
            String building,
            String rackSerialNumber,
            String rackUnit,
            Boolean lastAttempt) {

        log.info("Fetching Validation Job Status for Rack: {}", rackSerialNumber);
        Map<String, NcpClientHelper.ValidationJobRuntimeInfo> jobStatus =
                ncpClientHelper.fetchJobStatus(rackSerialNumber, region);

        log.info(
                "Rack Serial: {} Rack Unit: {} Job Status: \n {}",
                rackSerialNumber,
                rackUnit,
                jobStatus.entrySet().stream()
                        .collect(
                                Collectors.toMap(
                                        Map.Entry::getKey,
                                        entry -> entry.getValue().getJobStatus())));

        updateValidationJobStatus(
                jobStatus, rackSerialNumber, region, building, rackUnit, lastAttempt, scope);

        List<NcpJobDetails> ncpJobDetails =
                ncpJobDetailsDao.getNcpJobDetailsForRack(rackSerialNumber);

        return ncpJobDetails.stream()
                .collect(
                        Collectors.toMap(
                                NcpJobDetails::getDeviceName, NcpJobDetails::getJobStatus));
    }

    public Object getValidationFailuresByRack(String rackSerialNumber) {
        Object result = validationFailureResultDao.getValidationFailuresByRack(rackSerialNumber);

        Map<String, Object> validationResultsForRack = new HashMap<>();
        validationResultsForRack.put(rackSerialNumber, result);

        return validationResultsForRack;
    }
}
