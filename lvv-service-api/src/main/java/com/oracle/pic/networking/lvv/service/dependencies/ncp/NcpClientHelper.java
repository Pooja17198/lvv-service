package com.oracle.pic.networking.lvv.service.dependencies.ncp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.oracle.pic.commons.exceptions.server.ErrorCode;
import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.networking.lvv.service.config.LvvServiceApiConfiguration;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import com.oracle.pic.networking.lvv.service.kiev.JobStatus;
import com.oracle.pic.networking.lvv.service.kiev.NcpJobDetails;
import com.oracle.pic.networking.lvv.service.kiev.NcpJobDetailsDao;
import com.oracle.pic.networking.lvv.service.models.ncp.JobType;
import com.oracle.pic.networking.lvv.service.utils.GeneralUtils;
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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ToString
public class NcpClientHelper {

    private static final int DEFAULT_CLIENT_RETRY_COUNT = 3;
    private static final String RESULT_NAME = "TestResults";
    private static final int PRE_PARSE_PREVIEW_CHARS = 1024;
    private static final int PARSE_FAILURE_PREVIEW_CHARS = 2048;

    private static final List<Job.State> JOB_FAILED_STATES =
            List.of(Job.State.Failed, Job.State.Canceled, Job.State.Timeout, Job.State.Error);
    private static final List<Job.State> JOB_PENDING_STATES =
            List.of(Job.State.Pending, Job.State.Scheduled, Job.State.Started, Job.State.New);
    private static final List<Job.State> JOB_SUCCEEDED_STATE = List.of(Job.State.Succeeded);

    private static final int MAX_BATCHES = 20;

    private final LvvServiceApiConfiguration config;
    private final NcpClientSetup ncpClientSetup;
    private final NcpJobDetailsDao ncpJobDetailsDao;

    @Inject
    public NcpClientHelper(LvvServiceApiConfiguration config, NcpJobDetailsDao ncpJobDetailsDao) {
        this(config, new NcpClientSetup(), ncpJobDetailsDao);
    }

    public NcpClientHelper(
            LvvServiceApiConfiguration config,
            NcpClientSetup ncpClientSetup,
            NcpJobDetailsDao ncpJobDetailsDao) {
        this.config = config;
        this.ncpClientSetup = ncpClientSetup;
        this.ncpJobDetailsDao = ncpJobDetailsDao;
    }

    @Setter
    private BiFunction<InputStream, NcpJobDetailsDao, NcpJobResultProcessor>
            jobResultProcessorFactory = NcpJobResultProcessor::new;

    public Map<String, Map<String, List<Map<String, String>>>> getNcpJobOutput(
            String jobId, String region, String rackSerialNumber, String rackUnit) {

        try (MetricsScope scope =
                MetricsScope.create(MetricNames.MetricScopeNames.PROCESS_NCP_JOB_OUTPUT.name())) {

            JobResultsClient ncpJobResultsClient =
                    ncpClientSetup.getNcpJobResultsClient(config, region);
            GetJobResultRequest getJobResultRequest =
                    GetJobResultRequest.builder().jobId(jobId).resultName(RESULT_NAME).build();
            GetJobResultResponse getJobResultResponse =
                    ncpJobResultsClient.getJobResult(getJobResultRequest);
            byte[] payloadBytes = readResultBytes(getJobResultResponse.getInputStream());
            String payloadPreview = toPayloadPreview(payloadBytes, PRE_PARSE_PREVIEW_CHARS);

            log.info(
                    "[NCP] Raw {} payload preview before parse for jobId {} ({} bytes, first {} chars): {}",
                    RESULT_NAME,
                    jobId,
                    payloadBytes.length,
                    PRE_PARSE_PREVIEW_CHARS,
                    payloadPreview);

            NcpJobResultProcessor jobResultProcessor =
                    jobResultProcessorFactory.apply(
                            new ByteArrayInputStream(payloadBytes), ncpJobDetailsDao);

            scope.withDimension("jobId", jobId);
            scope.withDimension("region", GeneralUtils.getRegionInternalName(region));

            try {
                jobResultProcessor.processJobResult(scope);
            } catch (RenderableException e) {
                if ("Failed to parse NCP job result as JSON".equals(e.getMessage())) {
                    log.error(
                            "[NCP] Failed to parse {} payload as JSON for jobId {}. Preview(first {} chars): {}",
                            RESULT_NAME,
                            jobId,
                            PARSE_FAILURE_PREVIEW_CHARS,
                            toPayloadPreview(payloadBytes, PARSE_FAILURE_PREVIEW_CHARS));
                }
                throw e;
            }
            scope.recordSuccess();
            return jobResultProcessor.getDeviceResults();
        }
    }

    private byte[] readResultBytes(InputStream inputStream) {
        if (inputStream == null) {
            return new byte[0];
        }
        try {
            return inputStream.readAllBytes();
        } catch (IOException e) {
            log.warn("Unable to read NCP result stream for preview logging", e);
            return new byte[0];
        }
    }

    private String toPayloadPreview(byte[] payloadBytes, int maxChars) {
        if (payloadBytes == null || payloadBytes.length == 0) {
            return "<empty>";
        }

        String payloadText =
                new String(payloadBytes, StandardCharsets.UTF_8)
                        .replace("\r", "\\r")
                        .replace("\n", "\\n");
        if (payloadText.length() <= maxChars) {
            return payloadText;
        }

        return payloadText.substring(0, maxChars)
                + "...(truncated,totalChars="
                + payloadText.length()
                + ")";
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

    private List<JobTarget> getTargetsForDevices(List<String> deviceNames, String region) {

        // If deviceName list is empty, we create a target for PER_RACK_VALIDATION_JOB
        if (deviceNames.isEmpty()) {
            JobTarget target = JobTarget.builder().type(JobTarget.Type.Region).name(region).build();
            return List.of(target);
        }

        List<JobTarget> jobTargets = new ArrayList<>();

        for (String deviceName : deviceNames) {
            jobTargets.add(
                    JobTarget.builder().type(JobTarget.Type.Device).name(deviceName).build());
        }

        return jobTargets;
    }

    private HashMap<String, String> createHealthcheckJob(
            List<String> deviceNames, String payload, String region) throws Exception {

        HashMap<String, String> jobList = new HashMap<>();
        JobsClient ncpApiJobsClient = ncpClientSetup.getNcpClient(config, region);

        // We create a max of MAX_BATCH jobs, and group the devices equally into each batch
        int batchSize = deviceNames.size() / MAX_BATCHES + 1;

        for (int i = 0; i < deviceNames.size(); i += batchSize) {

            List<String> batch =
                    deviceNames.subList(i, Math.min(i + batchSize, deviceNames.size()));

            JobRequest jobRequest =
                    JobRequest.builder()
                            .jobType(JobType.HEALTH_CHECK)
                            .jobProcessor(JobRequest.JobProcessor.NcpDefault)
                            .payload(payload)
                            .targets(getTargetsForDevices(batch, region))
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

            log.info("NCP Job for devices {} created successfully", batch);

            for (String deviceName : batch) {
                jobList.put(deviceName, createJobResponse.getJob().getId());
            }
        }
        return jobList;
    }

    public HashMap<String, String> createRackValidationJob(
            String payload, String rackSerialNumber, String region) throws Exception {
        HashMap<String, String> jobList = new HashMap<>();
        JobsClient ncpApiJobsClient = ncpClientSetup.getNcpClient(config, region);

        JobRequest jobRequest =
                JobRequest.builder()
                        .jobType(JobType.PER_RACK_VALIDATION_JOB)
                        .jobProcessor(JobRequest.JobProcessor.NcpDefault)
                        .payload(payload)
                        .targets(getTargetsForDevices(Collections.emptyList(), region))
                        .parentJobId(null)
                        .build();

        CreateJobRequest createJobRequest =
                CreateJobRequest.builder().jobRequest(jobRequest).opcIdempotencyToken(null).build();

        CreateJobResponse createJobResponse =
                RetryHelper.newRetryHelper(
                                () -> ncpApiJobsClient.createJob(createJobRequest),
                                DEFAULT_CLIENT_RETRY_COUNT,
                                RetryHelper.retryAll)
                        .run();

        log.info("NCP PER_RACK_VALIDATION_JOB for rack {} created successfully", rackSerialNumber);
        jobList.put(rackSerialNumber, createJobResponse.getJob().getId());

        return jobList;
    }

    public HashMap<String, String> createJobs(
            String jobType,
            String rackSerialNumber,
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

            if (Objects.equals(jobType, JobType.HEALTH_CHECK)) {
                return createHealthcheckJob(deviceNames, payload, region);
            } else {
                return createRackValidationJob(payload, rackSerialNumber, region);
            }

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

    private Job getJob(String jobId, JobsClient ncpApiJobsClient, Map<String, Job> jobsMap) {

        try {

            if (jobsMap.containsKey(jobId)) {
                return jobsMap.get(jobId);
            }

            GetJobRequest getJobRequest = GetJobRequest.builder().jobId(jobId).build();

            GetJobResponse getJobResponse =
                    RetryHelper.newRetryHelper(
                                    () -> ncpApiJobsClient.getJob(getJobRequest),
                                    DEFAULT_CLIENT_RETRY_COUNT,
                                    RetryHelper.retryAll)
                            .run();

            // Since each job can be mapped to multiple devices, we memoize the jobs,
            // so that we don't fetch the same job again and again for each device
            jobsMap.put(jobId, getJobResponse.getJob());

            return getJobResponse.getJob();
        } catch (Exception e) {
            log.error("Unable to fetch Job {}", jobId, e);
            throw new RenderableException(
                    ErrorCode.ExternalServerInvalidResponse, "Failed to fetch Job unexpectedly");
        }
    }

    public Map<String, JobStatus> fetchJobStatus(String rackSerialNumber, String region) {
        JobsClient ncpApiJobsClient = ncpClientSetup.getNcpClient(config, region);

        // Fetch NcpJob Details for all devices in the rack
        List<NcpJobDetails> jobDetails = ncpJobDetailsDao.getNcpJobDetailsForRack(rackSerialNumber);

        // Map of Device name -> Job status
        Map<String, JobStatus> deviceJobStatusMap = new HashMap<>();

        // Map of Job ID -> Job
        Map<String, Job> jobsMap = new HashMap<>();

        log.info("Fetching job statuses for rack {}", rackSerialNumber);

        for (NcpJobDetails ncpJobDetails : jobDetails) {

            if (ncpJobDetails.getJobStatus() != JobStatus.IN_PROGRESS) {
                log.info("No jobs in progress for device {}", ncpJobDetails.getDeviceName());
                continue;
            }

            Job job = getJob(ncpJobDetails.getJobId(), ncpApiJobsClient, jobsMap);
            String jobType = job.getRequest().getJobType();

            if (job.getState() != null) {
                Job.State state = job.getState();

                log.info("Device {} Job State is {}", ncpJobDetails.getDeviceName(), state);

                if (JOB_FAILED_STATES.contains(state)) {
                    deviceJobStatusMap.put(ncpJobDetails.getDeviceName(), JobStatus.FAILED);
                } else {

                    // If Job Type is PER_RACK_VALIDATION_JOB
                    if (Objects.equals(jobType, JobType.PER_RACK_VALIDATION_JOB)) {
                        String jobId = job.getId();
                        String healthCheckJobId = getValidationJobId(jobId, region);
                        if (healthCheckJobId == null) {
                            if (JOB_PENDING_STATES.contains(state)) {
                                // We are still waiting for the PER_RACK_VALIDATION_JOB to trigger a
                                // HEALTH_CHECK Job
                                deviceJobStatusMap.put(
                                        ncpJobDetails.getDeviceName(), JobStatus.IN_PROGRESS);
                            } else if (JOB_SUCCEEDED_STATE.contains(state)) {
                                // PER_RACK_VALIDATION_JOB succeeded but was unable to trigger a
                                // Health_check job
                                deviceJobStatusMap.put(
                                        ncpJobDetails.getDeviceName(), JobStatus.FAILED);
                            }
                        } else {
                            // PER_RACK_VALIDATION_JOB has triggered a HEALTH_CHECK Job
                            // Update DB's Job ID from RACK_VALIDATION JOB to corresponding
                            // HEALTH_CHECK Job
                            ncpJobDetails.setJobId(healthCheckJobId);
                            ncpJobDetailsDao.updateNcpJobDetails(ncpJobDetails);
                        }
                    } else { // If Job Type is HEALTH_CHECK
                        if (JOB_PENDING_STATES.contains(state)) {
                            deviceJobStatusMap.put(
                                    ncpJobDetails.getDeviceName(), JobStatus.IN_PROGRESS);
                        } else {
                            deviceJobStatusMap.put(
                                    ncpJobDetails.getDeviceName(), JobStatus.COMPLETED);
                        }
                    }
                }
            }
        }

        return deviceJobStatusMap;
    }
}
