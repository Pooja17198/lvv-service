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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@ToString
public class CablingValidationService {

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
            Map<String, JobStatus> jobStatus,
            String rackSerialNumber,
            String region,
            String rackUnit,
            Boolean lastAttempt,
            MetricsScope scope) {

        for (Map.Entry<String, JobStatus> entry : jobStatus.entrySet()) {

            NcpJobDetails currJobDetails = ncpJobDetailsDao.getNcpJobDetails(entry.getKey());
            if (currJobDetails.getJobStatus() == JobStatus.IN_PROGRESS) {
                if (entry.getValue().equals(JobStatus.FAILED)) {
                    // Nothing to do here, validation job has failed
                    currJobDetails.setJobStatus(JobStatus.FAILED);
                    ncpJobDetailsDao.updateNcpJobDetails(currJobDetails);
                } else if (entry.getValue().equals(JobStatus.COMPLETED)) {

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
                    }

                    // Check if Device status was updated to Unreachable
                    if (ncpJobDetailsDao.getNcpJobDetails(entry.getKey()).getJobStatus()
                            != JobStatus.DEVICE_UNREACHABLE) {
                        currJobDetails.setJobStatus(JobStatus.COMPLETED);
                        ncpJobDetailsDao.updateNcpJobDetails(currJobDetails);
                    }

                } else if (entry.getValue().equals(JobStatus.IN_PROGRESS)) {
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

    public Map<String, JobStatus> getValidationJobStatus(
            MetricsScope scope,
            String region,
            String rackSerialNumber,
            String rackUnit,
            Boolean lastAttempt) {

        log.info("Fetching Validation Job Status for Rack: {}", rackSerialNumber);
        Map<String, JobStatus> jobStatus = ncpClientHelper.fetchJobStatus(rackSerialNumber, region);

        log.info(
                "Rack Serial: {} Rack Unit: {} Job Status: \n {}",
                rackSerialNumber,
                rackUnit,
                jobStatus);

        updateValidationJobStatus(
                jobStatus, rackSerialNumber, region, rackUnit, lastAttempt, scope);

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
