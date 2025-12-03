package com.oracle.pic.networking.lvv.service.dependencies.ncp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.oracle.pic.commons.exceptions.server.ErrorCode;
import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.networking.lvv.service.config.LvvServiceApiConfiguration;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import com.oracle.pic.networking.lvv.service.kiev.ValidationFailureResult;
import com.oracle.pic.networking.lvv.service.models.ncp.JobType;
import com.oracle.pic.networking.lvv.service.utils.RetryHelper;
import com.oracle.pic.networking.ncp.JobProgressClient;
import com.oracle.pic.networking.ncp.JobResultsClient;
import com.oracle.pic.networking.ncp.JobsClient;
import com.oracle.pic.networking.ncp.model.Job;
import com.oracle.pic.networking.ncp.model.JobRequest;
import com.oracle.pic.networking.ncp.model.JobTarget;
import com.oracle.pic.networking.ncp.model.UnitProgress;
import com.oracle.pic.networking.ncp.model.UnitProgressStatus;
import com.oracle.pic.networking.ncp.requests.CreateJobRequest;
import com.oracle.pic.networking.ncp.requests.GetJobRequest;
import com.oracle.pic.networking.ncp.requests.GetJobResultRequest;
import com.oracle.pic.networking.ncp.requests.ListJobUnitProgressRequest;
import com.oracle.pic.networking.ncp.requests.ListJobUnitsRequest;
import com.oracle.pic.networking.ncp.responses.CreateJobResponse;
import com.oracle.pic.networking.ncp.responses.GetJobResponse;
import com.oracle.pic.networking.ncp.responses.GetJobResultResponse;
import com.oracle.pic.networking.ncp.responses.ListJobUnitProgressResponse;
import com.oracle.pic.networking.ncp.responses.ListJobUnitsResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ToString
public class NcpClientHelper {

    private static final int DEFAULT_CLIENT_RETRY_COUNT = 3;
    private static final int RACK_VALIDATION_MAX_WAITING_CYCLES = 10;
    private static final int RACK_VALIDATION_JOB_WAITING_TIME_SECOND = 30;
    private static final String RESULT_NAME = "TestResults";

    private static final String NCP_JOB_UNKNOWN_STATE = "UNKNOWN";

    private static final List<Job.State> JOB_FAILED_STATES =
            List.of(Job.State.Failed, Job.State.Canceled, Job.State.Timeout, Job.State.Error);
    private static final List<Job.State> JOB_PENDING_STATES =
            List.of(Job.State.Pending, Job.State.Scheduled, Job.State.Started, Job.State.New);
    private static final List<Job.State> JOB_SUCCEEDED_STATE = List.of(Job.State.Succeeded);

    private final LvvServiceApiConfiguration config;
    private NcpClientSetup ncpClientSetup;

    @Inject
    public NcpClientHelper(LvvServiceApiConfiguration config) {
        this(config, new NcpClientSetup());
    }

    public NcpClientHelper(LvvServiceApiConfiguration config, NcpClientSetup ncpClientSetup) {
        this.config = config;
        this.ncpClientSetup = ncpClientSetup;
    }

    @Setter
    private Function<InputStream, NcpJobResultProcessor> jobResultProcessorFactory =
            NcpJobResultProcessor::new;

    public List<ValidationFailureResult> getNcpJobOutput(
            String jobId, String region, String rackSerialNumber, String rackUnit) {

        try (MetricsScope scope =
                MetricsScope.create(MetricNames.MetricScopeNames.PROCESS_NCP_JOB_OUTPUT.name())) {

            JobsClient ncpApiJobsClient = ncpClientSetup.getNcpClient(config, region);
            Job job = getJob(jobId, ncpApiJobsClient);
            if (Objects.equals(job.getRequest().getJobType(), JobType.PER_RACK_VALIDATION_JOB)) {
                jobId = getValidationJobId(jobId, region);
            }
            JobResultsClient ncpJobResultsClient =
                    ncpClientSetup.getNcpJobResultsClient(config, region);
            GetJobResultRequest getJobResultRequest =
                    GetJobResultRequest.builder().jobId(jobId).resultName(RESULT_NAME).build();
            GetJobResultResponse getJobResultResponse =
                    ncpJobResultsClient.getJobResult(getJobResultRequest);
            NcpJobResultProcessor jobResultProcessor =
                    jobResultProcessorFactory.apply(getJobResultResponse.getInputStream());

            scope.withDimension("rackSerial", rackSerialNumber);
            scope.withDimension("jobId", jobId);

            jobResultProcessor.processJobResult(scope);
            scope.recordSuccess();
            return jobResultProcessor.buildValidationFailureResults(rackSerialNumber, rackUnit);
        }
    }

    private List<JobTarget> getTargetForDevices(List<String> deviceNames, String region) {

        if (deviceNames.isEmpty()) {
            JobTarget target = JobTarget.builder().type(JobTarget.Type.Region).name(region).build();
            return List.of(target);
        }

        return deviceNames.stream()
                .map(
                        deviceName ->
                                JobTarget.builder()
                                        .type(JobTarget.Type.Device)
                                        .name(deviceName)
                                        .build())
                .toList();
    }

    public Job createJob(
            String jobType,
            String payload,
            List<String> deviceNames,
            MetricsScope scope,
            String region) {

        try {

            log.info(
                    "Creating NCP Job. Job Type: {}, payload: {}, deviceNames: {}",
                    jobType,
                    payload,
                    deviceNames);

            JobsClient ncpApiJobsClient = ncpClientSetup.getNcpClient(config, region);

            JobRequest jobRequest =
                    JobRequest.builder()
                            .jobType(jobType)
                            .jobProcessor(JobRequest.JobProcessor.NcpDefault)
                            .payload(payload)
                            .targets(getTargetForDevices(deviceNames, region))
                            .parentJobId(null)
                            .build();

            CreateJobRequest createJobRequest =
                    CreateJobRequest.builder()
                            .jobRequest(jobRequest)
                            .opcIdempotencyToken(null)
                            .build();

            CreateJobResponse createJobResponse =
                    RetryHelper.newRetryHelper(
                                    () -> ncpApiJobsClient.createJob(createJobRequest),
                                    DEFAULT_CLIENT_RETRY_COUNT,
                                    RetryHelper.retryAll)
                            .run();

            log.info("NCP Job created successfully");

            return createJobResponse.getJob();
        } catch (Exception e) {
            log.error(
                    "{} job with the following payload {} failed to create unexpectedly",
                    jobType,
                    payload,
                    e);
            scope.emit(MetricNames.ValidateCables.NcpJobCreationFailed.name(), 1.0);
            throw new RenderableException(
                    ErrorCode.ExternalServerInvalidResponse,
                    "Failed to create NCP job unexpectedly");
        }
    }

    // Fetching the HEALTH_CHECK Job ID which is triggered from the PER_RACK_VALIDATION_JOB
    private String getValidationJobId(String jobId, String region) {

        log.info("Fetching HEALTH_CHECK JOB ID from PER_RACK_VALIDATION Job {}", jobId);
        JobProgressClient jobProgressClient =
                ncpClientSetup.getNcpJobProgressClient(config, region);
        List<UnitProgress> unitProgressList = new ArrayList<>();
        String pageToken;
        log.info("Gathering all units for job {}", jobId);
        try {
            do {
                ListJobUnitsRequest request = ListJobUnitsRequest.builder().jobId(jobId).build();

                ListJobUnitsResponse response =
                        RetryHelper.newRetryHelper(
                                        () -> jobProgressClient.listJobUnits(request),
                                        DEFAULT_CLIENT_RETRY_COUNT,
                                        RetryHelper.retryAll)
                                .run();
                pageToken = response.getOpcNextPage();
                unitProgressList.addAll(response.getItems());
            } while (pageToken != null);

        } catch (Exception e) {
            log.error("Error fetching job units for job {}", jobId, e);
            throw new RenderableException(
                    ErrorCode.ExternalServerInvalidResponse, "Failed to fetch job units");
        }

        log.info("Unit Progress List: {}", unitProgressList);

        for (UnitProgress unitProgress : unitProgressList) {
            log.debug("Gathering Unit Progress Status for Unit Progress: {}", unitProgress);
            String unitName = unitProgress.getUnitName();
            if (Objects.equals(unitName, "StartValidation")
                    || Objects.equals(unitName, "PreChecks")) {
                ListJobUnitProgressRequest request =
                        ListJobUnitProgressRequest.builder()
                                .jobId(jobId)
                                .unitName(unitName)
                                .build();
                List<UnitProgressStatus> unitProgressStatusList;
                try {
                    ListJobUnitProgressResponse response =
                            RetryHelper.newRetryHelper(
                                            () -> jobProgressClient.listJobUnitProgress(request),
                                            DEFAULT_CLIENT_RETRY_COUNT,
                                            RetryHelper.retryAll)
                                    .run();
                    unitProgressStatusList = response.getItems();
                } catch (Exception e) {
                    log.error(
                            "Error fetching Unit Progress Status for job {} unit {}",
                            jobId,
                            unitName,
                            e);
                    throw new RenderableException(
                            ErrorCode.ExternalServerInvalidResponse,
                            String.format(
                                    "Error fetching Unit Progress Status for job %s unit %s",
                                    jobId, unitName));
                }
                for (UnitProgressStatus unitProgressStatus : unitProgressStatusList) {
                    log.info("Unit Progress Status: {}", unitProgressStatus);
                    String verboseMessage = unitProgressStatus.getMessage().getVerboseMessage();

                    // Check if Validation has started
                    String prefix = "Validation Started: ";
                    if (verboseMessage != null && verboseMessage.startsWith(prefix)) {
                        log.info(
                                "Extracting HEALTH_CHECK Job Id from verbose message: {}",
                                verboseMessage);
                        String jsonPart = verboseMessage.substring(prefix.length());
                        ObjectMapper mapper = new ObjectMapper();
                        try {
                            JsonNode root = mapper.readTree(jsonPart);
                            JsonNode jobIdNode = root.get("validationJobId");
                            if (jobIdNode != null) {
                                log.info(
                                        "Validation Job Id extracted from PER_RACK_VALIDATION_JOB is {}",
                                        jobIdNode.asText());
                                return jobIdNode.asText();
                            } else {
                                throw new RenderableException(
                                        ErrorCode.InternalError,
                                        "validationJobId not found in PER_RACK_VALIDATION Job Unit Progress Status");
                            }
                        } catch (IOException e) {
                            log.error(
                                    "Error while fetching validation job ID from PER_RACK_VALIDATION Job Unit Progress Status");
                            throw new RenderableException(
                                    ErrorCode.InternalError,
                                    "Error while fetching validation job ID from PER_RACK_VALIDATION Job Unit Progress Status");
                        }
                    }

                    // Check prechecks step in the PER_RACK_VALIDATION_JOB
                    if (unitProgressStatus.getUnitName().equals("PreChecks")) {
                        verboseMessage = unitProgressStatus.getMessage().getVerboseMessage();

                        // If devices are already in service, Healthcheck job will not be triggered,
                        // and we sh
                        if (verboseMessage != null
                                && (verboseMessage.contains(
                                                "All device(s) in the rack are already in-service.")
                                        || verboseMessage.contains("device(s) not reachable")
                                        || verboseMessage.contains(
                                                "device(s) are in maintenance state"))) {
                            throw new RenderableException(
                                    ErrorCode.InvalidParameter, verboseMessage);
                        }
                    }
                }
            }
        }
        return null;
    }

    private Job getJob(String jobId, JobsClient ncpApiJobsClient) {

        try {
            GetJobRequest getJobRequest = GetJobRequest.builder().jobId(jobId).build();

            GetJobResponse getJobResponse =
                    RetryHelper.newRetryHelper(
                                    () -> ncpApiJobsClient.getJob(getJobRequest),
                                    DEFAULT_CLIENT_RETRY_COUNT,
                                    RetryHelper.retryAll)
                            .run();

            return getJobResponse.getJob();
        } catch (Exception e) {
            log.error("Unable to fetch Job {}", jobId, e);
            throw new RenderableException(
                    ErrorCode.ExternalServerInvalidResponse, "Failed to fetch Job unexpectedly");
        }
    }

    public String getJobType(String jobId, String region) {
        try {
            JobsClient ncpApiJobsClient = ncpClientSetup.getNcpClient(config, region);
            Job job = getJob(jobId, ncpApiJobsClient);

            String jobType = job.getRequest().getJobType();
            log.info("Job Type fetched is {}", jobType);
            return jobType;
        } catch (Exception e) {
            log.error("Unable to fetch Job Type for job {}", jobId, e);
            throw new RenderableException(
                    ErrorCode.ExternalServerInvalidResponse,
                    "Failed to fetch Job type unexpectedly");
        }
    }

    public String fetchJobStatus(String jobId, MetricsScope scope, String region) {
        JobsClient ncpApiJobsClient = ncpClientSetup.getNcpClient(config, region);
        Job job = getJob(jobId, ncpApiJobsClient);
        String jobType = job.getRequest().getJobType();
        String healthCheckJobId;

        scope.withDimension("jobType", jobType);

        if (job.getState() != null) {
            Job.State state = job.getState();

            log.debug("{} Job State is {}", jobType, state);

            if (JOB_FAILED_STATES.contains(state)) {
                scope.emit(MetricNames.GetValidationJobStatus.Fail, 1.0);
                return Job.State.Failed.name();
            } else {
                if (Objects.equals(jobType, JobType.PER_RACK_VALIDATION_JOB)) {
                    healthCheckJobId = getValidationJobId(jobId, region);
                    if (healthCheckJobId == null) {
                        if (JOB_PENDING_STATES.contains(state)) {
                            // We are still waiting for the PER_RACK_VALIDATION_JOB to trigger a
                            // HEALTH_CHECK Job
                            scope.emit(MetricNames.GetValidationJobStatus.Pending, 1.0);
                            return Job.State.Pending.name();
                        } else if (JOB_SUCCEEDED_STATE.contains(state)) {
                            // PER_RACK_VALIDATION_JOB succeeded but was unable to trigger a
                            // Health_check job
                            scope.emit(MetricNames.GetValidationJobStatus.Fail, 1.0);
                            return Job.State.Failed.name();
                        }
                    } else {
                        // PER_RACK_VALIDATION_JOB has triggered a HEALTH_CHECK Job
                        Job healthcheckJob = getJob(healthCheckJobId, ncpApiJobsClient);
                        Job.State healthcheckState = healthcheckJob.getState();
                        log.info(
                                "{} Job State is {}",
                                healthcheckJob.getRequest().getJobType(),
                                healthcheckState);
                        if (JOB_FAILED_STATES.contains(healthcheckState)) {
                            scope.emit(MetricNames.GetValidationJobStatus.Fail, 1.0);
                            return Job.State.Failed.name();
                        } else if (JOB_PENDING_STATES.contains(healthcheckState)) {
                            scope.emit(MetricNames.GetValidationJobStatus.Pending, 1.0);
                            return Job.State.Pending.name();
                        } else {
                            scope.emit(MetricNames.GetValidationJobStatus.Success, 1.0);
                            return Job.State.Succeeded.name();
                        }
                    }
                } else {
                    if (JOB_PENDING_STATES.contains(state)) {
                        scope.emit(MetricNames.GetValidationJobStatus.Pending, 1.0);
                        return Job.State.Pending.name();
                    } else {
                        scope.emit(MetricNames.GetValidationJobStatus.Success, 1.0);
                        return Job.State.Succeeded.name();
                    }
                }
            }
        }

        log.warn("Unable to fetch Job Status for job {}", jobId);
        scope.emit(MetricNames.GetValidationJobStatus.Unknown, 1.0);
        return NCP_JOB_UNKNOWN_STATE;
    }
}
