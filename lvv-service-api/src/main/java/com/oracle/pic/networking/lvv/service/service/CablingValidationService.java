package com.oracle.pic.networking.lvv.service.service;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueField;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.oracle.pic.commons.exceptions.server.ErrorCode;
import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.networking.lvv.service.dependencies.jira.JiraQueries;
import com.oracle.pic.networking.lvv.service.dependencies.jira.JiraSDService;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import com.oracle.pic.networking.lvv.service.dependencies.ncp.NcpClientHelper;
import com.oracle.pic.networking.lvv.service.kiev.ValidationFailureResult;
import com.oracle.pic.networking.lvv.service.kiev.ValidationFailureResultDao;
import com.oracle.pic.networking.lvv.service.models.ncp.JobType;
import com.oracle.pic.networking.lvv.service.utils.GeneralUtils;
import com.oracle.pic.networking.ncp.model.Job;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@ToString
public class CablingValidationService {

    private final NcpClientHelper ncpClientHelper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // private static final List<String> testSuites = List.of("dcs_rack_validation");
    private static final List<String> testSuites = List.of("dcs_rack_validation");
    private final JiraSDService jiraSDService;
    private final ValidationFailureResultDao validationFailureResultDao;

    @Inject
    public CablingValidationService(
            NcpClientHelper ncpClientHelper,
            JiraSDService jiraSDService,
            ValidationFailureResultDao validationFailureResultDao) {
        this.ncpClientHelper = ncpClientHelper;
        this.jiraSDService = jiraSDService;
        this.validationFailureResultDao = validationFailureResultDao;
    }

    private String fetchRackLocation(String rackSerialNumber) {
        SearchResult initialCablingTickets = this.searchInitialCablingTickets(rackSerialNumber);
        String rackLocation = null;

        for (Issue issue : initialCablingTickets.getIssues()) {
            for (IssueField issueField : issue.getFields()) {
                if (issueField.getName().equals("Rack Location")) {
                    rackLocation = issueField.getValue().toString();
                    break;
                }
            }
        }

        return rackLocation;
    }

    public String validateCablingTasks(
            String building,
            String rackSerialNumber,
            List<String> deviceNames,
            MetricsScope scope) {
        try {
            // Fetch Rack Number from Rack Deployment Ticket
            String rackLocation = fetchRackLocation(rackSerialNumber);
            if (rackLocation == null) {
                log.error(
                        "Could not find a corresponding Rack Location for the rack serial from Jira");
                throw new RenderableException(
                        ErrorCode.IncorrectState,
                        "Rack Deployment ticket for the corresponding rack serial not found");
            }

            String region = GeneralUtils.getRegionFromBuilding(building);

            String jobType;

            String payload;

            if (deviceNames.isEmpty()) {
                jobType = JobType.PER_RACK_VALIDATION_JOB;

                payload =
                        objectMapper.writeValueAsString(
                                Map.of(
                                        "rack_number", rackLocation,
                                        "building", building,
                                        "dry_run", true));
            } else {
                jobType = JobType.HEALTH_CHECK;

                payload =
                        objectMapper.writeValueAsString(
                                Map.of(
                                        "testSuites", testSuites,
                                        "rackNumber", rackLocation,
                                        "building", building));
            }

            scope.withDimension("JobType", jobType);
            scope.emit(MetricNames.ValidateCables.ValidateCable.name(), 1.0);

            log.info("Validating Building:Rack {}:{}", building, rackLocation);

            Job job = ncpClientHelper.createJob(jobType, payload, deviceNames, scope, region);

            log.info("NCP Job Created. Job ID: {}", job.getId());

            return job.getId();

        } catch (JsonProcessingException e) {
            log.error("Error while generating payload ", e);
            throw new RenderableException(
                    ErrorCode.InternalError, "Error while generating payload for NCP Job");
        }
    }

    public String getValidationJobStatus(
            String jobId, MetricsScope scope, String region, String rackSerialNumber) {

        log.info("Fetching Validation Job Status for Rack: {}", rackSerialNumber);
        String jobStatus = ncpClientHelper.fetchJobStatus(jobId, scope, region);

        String rackUnit = fetchRackLocation(rackSerialNumber);
        log.info("Rack Serial: {} Job Status: {}", rackSerialNumber, jobStatus);

        if (Objects.equals(jobStatus, Job.State.Succeeded.name())) {

            try (MetricsScope addResultsScope =
                    MetricsScope.create(
                            MetricNames.MetricScopeNames.ADD_VALIDATION_RESULTS.name())) {

                addResultsScope.withDimension("rackSerialNumber", rackSerialNumber);
                List<ValidationFailureResult> output =
                        ncpClientHelper.getNcpJobOutput(jobId, region, rackSerialNumber, rackUnit);
                String jobType = ncpClientHelper.getJobType(jobId, region);

                if (jobType.equals(JobType.PER_RACK_VALIDATION_JOB)) {
                    validationFailureResultDao.addValidationFailureResultsForRack(
                            output, rackSerialNumber, addResultsScope);
                } else if (jobType.equals(JobType.HEALTH_CHECK)) {
                    validationFailureResultDao.updateValidationFailureResultsForDevices(
                            output, addResultsScope);
                }

                scope.recordSuccess();
            }

        } else if (Objects.equals(jobStatus, Job.State.Failed.name())) {
            throw new RenderableException(
                    ErrorCode.ExternalServerInvalidResponse, "Validation Job Failed");
        }

        return jobStatus;
    }

    private SearchResult searchInitialCablingTickets(String rackSerialNumber) {

        String initialCablingJql;
        initialCablingJql = String.format(JiraQueries.JQL_PROJECT + JiraQueries.RACK_DEPLOYMENT);

        if (rackSerialNumber != null) {
            initialCablingJql += String.format(JiraQueries.SERIAL_NUMBER, rackSerialNumber);
        } else {
            throw new RenderableException(
                    ErrorCode.InvalidParameter, "Cannot find Rack Unit without Rack Serial Number");
        }
        return this.jiraSDService.searchJiraSD(initialCablingJql);
    }
}
