package com.oracle.pic.networking.lvv.service.dependencies.ncp;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.networking.lvv.service.config.LvvServiceApiConfiguration;
import com.oracle.pic.networking.lvv.service.kiev.JobStatus;
import com.oracle.pic.networking.lvv.service.kiev.NcpJobDetails;
import com.oracle.pic.networking.lvv.service.kiev.NcpJobDetailsDao;
import com.oracle.pic.networking.lvv.service.models.ncp.JobType;
import com.oracle.pic.networking.lvv.service.utils.RetryHelper;
import com.oracle.pic.networking.ncp.JobProgressClient;
import com.oracle.pic.networking.ncp.JobResultsClient;
import com.oracle.pic.networking.ncp.JobsClient;
import com.oracle.pic.networking.ncp.model.Job;
import com.oracle.pic.networking.ncp.model.JobRequest;
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
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
class NcpClientHelperTest {
    @Mock LvvServiceApiConfiguration mockConfig;
    @Mock NcpClientSetup mockClientSetup;
    @Mock JobsClient mockJobsClient;
    @Mock JobResultsClient mockJobResultsClient;
    @Mock JobProgressClient mockJobProgressClient;
    @Mock NcpJobDetailsDao mockNcpJobDetailsDao;

    NcpClientHelper helper;

    private static Method privateMethod(Object target, String name, Class<?>... params)
            throws Exception {
        Method m = target.getClass().getDeclaredMethod(name, params);
        m.setAccessible(true);
        return m;
    }

    // Helper to reduce duplicate stubbing for JobResultsClient
    private void stubJobResultsClient(String region, InputStream resultInputStream) {
        GetJobResultResponse mockJobResultResp = mock(GetJobResultResponse.class);
        when(mockJobResultResp.getInputStream()).thenReturn(resultInputStream);
        when(mockClientSetup.getNcpJobResultsClient(mockConfig, region))
                .thenReturn(mockJobResultsClient);
        when(mockJobResultsClient.getJobResult(any(GetJobResultRequest.class)))
                .thenReturn(mockJobResultResp);
    }

    @BeforeEach
    void setup() {
        helper = new NcpClientHelper(mockConfig, mockClientSetup, mockNcpJobDetailsDao);
    }

    @Test
    void testGetNcpJobOutput_success() {
        String jobId = "job1";
        String region = "us-phx";
        String rackSerial = "RSN123";
        String rackUnit = "RU01";

        InputStream resultInputStream =
                new ByteArrayInputStream("{\"testResults\":{}}".getBytes(StandardCharsets.UTF_8));
        stubJobResultsClient(region, resultInputStream);

        NcpJobResultProcessor mockResultProcessor = mock(NcpJobResultProcessor.class);
        doNothing().when(mockResultProcessor).processJobResult(any(MetricsScope.class));

        Map<String, Map<String, List<Map<String, String>>>> expected = new HashMap<>();
        expected.put("dev1", new HashMap<>());
        when(mockResultProcessor.getDeviceResults()).thenReturn(expected);

        helper.setJobResultProcessorFactory((is, dao) -> mockResultProcessor);

        Map<String, Map<String, List<Map<String, String>>>> results =
                helper.getNcpJobOutput(jobId, region, rackSerial, rackUnit);

        assertEquals(expected, results);
        verify(mockResultProcessor, times(1)).processJobResult(any(MetricsScope.class));
    }

    @Test
    void testGetNcpJobOutput_processorThrows() {
        String jobId = "job1";
        String region = "us-phx";
        String rackSerial = "RSN1";
        String rackUnit = "RU1";

        InputStream resultInputStream =
                new ByteArrayInputStream("{\"testResults\":{}}".getBytes(StandardCharsets.UTF_8));
        stubJobResultsClient(region, resultInputStream);

        NcpJobResultProcessor mockResultProcessor = mock(NcpJobResultProcessor.class);
        doThrow(new RuntimeException("processing fails"))
                .when(mockResultProcessor)
                .processJobResult(any(MetricsScope.class));

        helper.setJobResultProcessorFactory((is, dao) -> mockResultProcessor);
        assertThrows(
                RuntimeException.class,
                () -> helper.getNcpJobOutput(jobId, region, rackSerial, rackUnit));
    }

    @Test
    void testGetNcpJobOutput_clientThrows() {
        String jobId = "job1";
        String region = "us-phx";
        String rackSerial = "RSN1";
        String rackUnit = "RU1";
        when(mockClientSetup.getNcpJobResultsClient(mockConfig, region))
                .thenReturn(mockJobResultsClient);
        when(mockJobResultsClient.getJobResult(any(GetJobResultRequest.class)))
                .thenThrow(new RuntimeException("client error"));
        helper.setJobResultProcessorFactory((is, dao) -> mock(NcpJobResultProcessor.class));
        assertThrows(
                RuntimeException.class,
                () -> helper.getNcpJobOutput(jobId, region, rackSerial, rackUnit));
    }

    @Test
    void testCreateJobs_healthCheck_batching() throws Exception {
        int numDevices = 44; // More than MAX_BATCHES to trigger multiple batches
        List<String> deviceNames = new ArrayList<>();
        for (int i = 0; i < numDevices; i++) deviceNames.add("dev" + i);
        String payload = "{}";
        String region = "us-phx";
        MetricsScope mockScope = mock(MetricsScope.class);

        when(mockClientSetup.getNcpClient(mockConfig, region)).thenReturn(mockJobsClient);

        CreateJobResponse mockCreateResp = mock(CreateJobResponse.class);
        Job mockJob = mock(Job.class);
        when(mockCreateResp.getJob()).thenReturn(mockJob);

        try (MockedStatic<RetryHelper> mockedRetryHelper = Mockito.mockStatic(RetryHelper.class)) {
            RetryHelper<CreateJobResponse> rh = mock(RetryHelper.class);
            when(rh.run()).thenReturn(mockCreateResp);
            mockedRetryHelper
                    .when(() -> RetryHelper.newRetryHelper(any(), anyInt(), any()))
                    .thenReturn(rh);

            Map<String, String> jobList =
                    helper.createJobs(
                            JobType.HEALTH_CHECK, "RSN1", payload, deviceNames, mockScope, region);

            assertNotNull(jobList);
            assertEquals(numDevices, jobList.size());
        }
    }

    @Test
    void testCreateJobs_healthCheck_emptyDeviceList_returnsEmpty() throws Exception {
        String payload = "{}";
        String region = "us-phx";
        MetricsScope mockScope = mock(MetricsScope.class);

        when(mockClientSetup.getNcpClient(mockConfig, region)).thenReturn(mockJobsClient);

        // No RetryHelper should be invoked, and no createJob call should be made
        Map<String, String> jobList =
                helper.createJobs(
                        JobType.HEALTH_CHECK, "RSN1", payload, List.of(), mockScope, region);

        assertNotNull(jobList);
        assertTrue(jobList.isEmpty());
        verify(mockJobsClient, never()).createJob(any(CreateJobRequest.class));
    }

    @Test
    void testCreateJobs_perRackValidation() throws Exception {
        String payload = "{}";
        String region = "us-foo";
        MetricsScope mockScope = mock(MetricsScope.class);

        when(mockClientSetup.getNcpClient(mockConfig, region)).thenReturn(mockJobsClient);

        CreateJobResponse mockCreateResp = mock(CreateJobResponse.class);
        Job mockJob = mock(Job.class);
        when(mockJob.getId()).thenReturn("job1");
        when(mockCreateResp.getJob()).thenReturn(mockJob);

        try (MockedStatic<RetryHelper> mockedRetryHelper = Mockito.mockStatic(RetryHelper.class)) {
            RetryHelper<CreateJobResponse> rh = mock(RetryHelper.class);
            when(rh.run()).thenReturn(mockCreateResp);
            mockedRetryHelper
                    .when(() -> RetryHelper.newRetryHelper(any(), anyInt(), any()))
                    .thenReturn(rh);

            Map<String, String> jobList =
                    helper.createJobs(
                            JobType.PER_RACK_VALIDATION_JOB,
                            "RSN1",
                            payload,
                            List.of(),
                            mockScope,
                            region);

            assertNotNull(jobList);
            assertEquals(1, jobList.size());
            assertTrue(jobList.containsKey("RSN1"));
        }
    }

    @Test
    void testCreateJobs_exception() {
        String payload = "{}";
        String region = "us-foo";
        MetricsScope mockScope = mock(MetricsScope.class);

        when(mockClientSetup.getNcpClient(mockConfig, region)).thenReturn(mockJobsClient);

        try (MockedStatic<RetryHelper> mockedRetryHelper = Mockito.mockStatic(RetryHelper.class)) {
            RetryHelper<CreateJobResponse> rh = mock(RetryHelper.class);
            when(rh.run()).thenThrow(new RuntimeException("Boom"));
            mockedRetryHelper
                    .when(() -> RetryHelper.newRetryHelper(any(), anyInt(), any()))
                    .thenReturn(rh);

            RenderableException ex =
                    assertThrows(
                            RenderableException.class,
                            () ->
                                    helper.createJobs(
                                            JobType.PER_RACK_VALIDATION_JOB,
                                            "RSN1",
                                            payload,
                                            List.of(),
                                            mockScope,
                                            region));
            assertTrue(ex.getMessage().contains("Failed to create NCP job unexpectedly"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testFetchJobStatus_allStates_failed() {
        String rackSerial = "RSN1";
        String region = "us-phx";
        NcpJobDetails inProgressDetail = mock(NcpJobDetails.class);
        when(inProgressDetail.getJobStatus()).thenReturn(JobStatus.IN_PROGRESS);
        when(inProgressDetail.getDeviceName()).thenReturn("DeviceA");
        when(inProgressDetail.getJobId()).thenReturn("j1");

        List<NcpJobDetails> jobDetails = List.of(inProgressDetail);

        when(mockNcpJobDetailsDao.getNcpJobDetailsForRack(rackSerial)).thenReturn(jobDetails);

        JobRequest jobRequest = mock(JobRequest.class);
        when(jobRequest.getJobType()).thenReturn(JobType.HEALTH_CHECK);
        Job mockJob = mock(Job.class);
        when(mockJob.getRequest()).thenReturn(jobRequest);
        when(mockJob.getState()).thenReturn(Job.State.Failed);
        Date mockStartDate = Date.from(Instant.parse("2026-03-26T20:00:00Z"));
        Date mockEndDate = Date.from(Instant.parse("2026-03-26T20:00:05Z"));
        when(mockJob.getStartDate()).thenReturn(mockStartDate);
        when(mockJob.getEndDate()).thenReturn(mockEndDate);

        when(mockClientSetup.getNcpClient(mockConfig, region)).thenReturn(mockJobsClient);
        when(mockJobsClient.getJob(any(GetJobRequest.class)))
                .thenReturn(GetJobResponse.builder().job(mockJob).build());

        Map<String, NcpClientHelper.ValidationJobRuntimeInfo> jobStatusMap =
                helper.fetchJobStatus(rackSerial, region);
        assertEquals(1, jobStatusMap.size());
        assertEquals(JobStatus.FAILED, jobStatusMap.get("DeviceA").getJobStatus());
        assertEquals("2026-03-26T20:00:00Z", jobStatusMap.get("DeviceA").getStartDate());
        assertEquals("2026-03-26T20:00:05Z", jobStatusMap.get("DeviceA").getEndDate());
        assertEquals(JobType.HEALTH_CHECK, jobStatusMap.get("DeviceA").getJobType());
    }

    @Test
    void testFetchJobStatus_skipsMetadataUpdateWhenUnchanged() {
        String rackSerial = "RSN-UNCHANGED";
        String region = "us-phx";

        NcpJobDetails inProgressDetail = mock(NcpJobDetails.class);
        when(inProgressDetail.getJobStatus()).thenReturn(JobStatus.IN_PROGRESS);
        when(inProgressDetail.getDeviceName()).thenReturn("DeviceU");
        when(inProgressDetail.getJobId()).thenReturn("job-u1");

        when(mockNcpJobDetailsDao.getNcpJobDetailsForRack(rackSerial))
                .thenReturn(List.of(inProgressDetail));

        NcpJobDetails existing =
                NcpJobDetails.builder()
                        .deviceName("DeviceU")
                        .rackSerial(rackSerial)
                        .jobId("job-u1")
                        .jobStatus(JobStatus.IN_PROGRESS)
                        .build();
        JobRequest jobRequest = mock(JobRequest.class);
        when(jobRequest.getJobType()).thenReturn(JobType.HEALTH_CHECK);
        Job mockJob = mock(Job.class);
        when(mockJob.getRequest()).thenReturn(jobRequest);
        when(mockJob.getState()).thenReturn(Job.State.Succeeded);
        when(mockJob.getStartDate()).thenReturn(Date.from(Instant.parse("2026-03-26T20:00:00Z")));
        when(mockJob.getEndDate()).thenReturn(Date.from(Instant.parse("2026-03-26T20:00:05Z")));

        when(mockClientSetup.getNcpClient(mockConfig, region)).thenReturn(mockJobsClient);
        when(mockJobsClient.getJob(any(GetJobRequest.class)))
                .thenReturn(GetJobResponse.builder().job(mockJob).build());

        Map<String, NcpClientHelper.ValidationJobRuntimeInfo> result =
                helper.fetchJobStatus(rackSerial, region);

        assertEquals(JobStatus.COMPLETED, result.get("DeviceU").getJobStatus());
        assertEquals("2026-03-26T20:00:00Z", result.get("DeviceU").getStartDate());
        assertEquals("2026-03-26T20:00:05Z", result.get("DeviceU").getEndDate());
        assertEquals(JobType.HEALTH_CHECK, result.get("DeviceU").getJobType());
    }

    @Test
    void testFetchJobStatus_healthCheck_pending_and_succeeded() {
        String rackSerial = "RSN2";
        String region = "us-phx";

        NcpJobDetails detailPending = mock(NcpJobDetails.class);
        when(detailPending.getJobStatus()).thenReturn(JobStatus.IN_PROGRESS);
        when(detailPending.getJobId()).thenReturn("jobPending");
        when(detailPending.getDeviceName()).thenReturn("DevP");

        NcpJobDetails detailSucceeded = mock(NcpJobDetails.class);
        when(detailSucceeded.getJobStatus()).thenReturn(JobStatus.IN_PROGRESS);
        when(detailSucceeded.getJobId()).thenReturn("jobSuccess");
        when(detailSucceeded.getDeviceName()).thenReturn("DevS");

        when(mockNcpJobDetailsDao.getNcpJobDetailsForRack(rackSerial))
                .thenReturn(List.of(detailPending, detailSucceeded));

        when(mockClientSetup.getNcpClient(mockConfig, region)).thenReturn(mockJobsClient);

        JobRequest jobRequest = mock(JobRequest.class);
        when(jobRequest.getJobType()).thenReturn(JobType.HEALTH_CHECK);

        Job jobPending = mock(Job.class);
        when(jobPending.getRequest()).thenReturn(jobRequest);
        when(jobPending.getState()).thenReturn(Job.State.Pending);

        Job jobSucceeded = mock(Job.class);
        when(jobSucceeded.getRequest()).thenReturn(jobRequest);
        when(jobSucceeded.getState()).thenReturn(Job.State.Succeeded);

        // First call for jobPending, second for jobSuccess
        when(mockJobsClient.getJob(any(GetJobRequest.class)))
                .thenReturn(GetJobResponse.builder().job(jobPending).build())
                .thenReturn(GetJobResponse.builder().job(jobSucceeded).build());

        Map<String, NcpClientHelper.ValidationJobRuntimeInfo> status =
                helper.fetchJobStatus(rackSerial, region);
        assertEquals(JobStatus.IN_PROGRESS, status.get("DevP").getJobStatus());
        assertEquals(JobStatus.COMPLETED, status.get("DevS").getJobStatus());
    }

    @Test
    void testFetchJobStatus_perRackValidation_triggersHealthCheck() {
        String rackSerial = "RSN1";
        String region = "us-phx";

        NcpJobDetails inProgressDetail = mock(NcpJobDetails.class);
        when(inProgressDetail.getJobStatus()).thenReturn(JobStatus.IN_PROGRESS);
        when(inProgressDetail.getDeviceName()).thenReturn("DeviceA");
        when(inProgressDetail.getJobId()).thenReturn("prvJob");
        List<NcpJobDetails> jobDetails = List.of(inProgressDetail);

        when(mockNcpJobDetailsDao.getNcpJobDetailsForRack(rackSerial)).thenReturn(jobDetails);
        when(mockClientSetup.getNcpClient(any(), eq(region))).thenReturn(mockJobsClient);
        when(mockClientSetup.getNcpJobProgressClient(any(), eq(region)))
                .thenReturn(mockJobProgressClient);

        JobRequest jobRequest = mock(JobRequest.class);
        when(jobRequest.getJobType()).thenReturn(JobType.PER_RACK_VALIDATION_JOB);

        Job mockJob = mock(Job.class);
        when(mockJob.getRequest()).thenReturn(jobRequest);
        when(mockJob.getState()).thenReturn(Job.State.Succeeded);
        when(mockJob.getId()).thenReturn("prvJob");
        Date mockStartDate = Date.from(Instant.parse("2026-03-26T20:00:00Z"));
        Date mockEndDate = Date.from(Instant.parse("2026-03-26T20:00:10Z"));
        when(mockJob.getStartDate()).thenReturn(mockStartDate);
        when(mockJob.getEndDate()).thenReturn(mockEndDate);
        when(mockJobsClient.getJob(any(GetJobRequest.class)))
                .thenReturn(GetJobResponse.builder().job(mockJob).build());

        // No JobUnits - so getValidationJobId() returns null
        ListJobUnitsResponse jobUnitsResponse =
                ListJobUnitsResponse.builder()
                        .items(Collections.emptyList())
                        .opcNextPage(null)
                        .build();
        when(mockJobProgressClient.listJobUnits(any(ListJobUnitsRequest.class)))
                .thenReturn(jobUnitsResponse);

        Map<String, NcpClientHelper.ValidationJobRuntimeInfo> jobStatusMap =
                helper.fetchJobStatus(rackSerial, region);

        assertEquals(JobStatus.FAILED, jobStatusMap.get("DeviceA").getJobStatus());
        assertEquals("2026-03-26T20:00:00Z", jobStatusMap.get("DeviceA").getStartDate());
        assertEquals("2026-03-26T20:00:10Z", jobStatusMap.get("DeviceA").getEndDate());
        assertEquals(JobType.PER_RACK_VALIDATION_JOB, jobStatusMap.get("DeviceA").getJobType());
    }

    @MockitoSettings(strictness = Strictness.LENIENT)
    @Test
    void testFetchJobStatus_updatesJobIdIfTriggered() throws Exception {
        String rackSerial = "RSN1";
        String region = "us-phx";

        NcpJobDetails inProgressDetail = mock(NcpJobDetails.class);
        when(inProgressDetail.getJobStatus()).thenReturn(JobStatus.IN_PROGRESS);
        when(inProgressDetail.getJobId()).thenReturn("prvJob");
        List<NcpJobDetails> jobDetails = List.of(inProgressDetail);

        when(mockNcpJobDetailsDao.getNcpJobDetailsForRack(rackSerial)).thenReturn(jobDetails);

        when(mockClientSetup.getNcpClient(eq(mockConfig), eq(region))).thenReturn(mockJobsClient);

        JobRequest jobRequest = mock(JobRequest.class);
        when(jobRequest.getJobType()).thenReturn(JobType.PER_RACK_VALIDATION_JOB);

        Job mockJob = mock(Job.class);
        when(mockJob.getRequest()).thenReturn(jobRequest);
        when(mockJob.getState()).thenReturn(Job.State.Pending);
        when(mockJob.getId()).thenReturn("prvJob");
        when(mockJobsClient.getJob(any(GetJobRequest.class)))
                .thenReturn(GetJobResponse.builder().job(mockJob).build());

        when(mockClientSetup.getNcpJobProgressClient(eq(mockConfig), eq(region)))
                .thenReturn(mockJobProgressClient);

        UnitProgress startValidationUnit = mock(UnitProgress.class);
        when(startValidationUnit.getUnitName()).thenReturn("StartValidation");
        ListJobUnitsResponse jobUnitsResponse =
                ListJobUnitsResponse.builder()
                        .items(singletonList(startValidationUnit))
                        .opcNextPage(null)
                        .build();
        when(mockJobProgressClient.listJobUnits(any(ListJobUnitsRequest.class)))
                .thenReturn(jobUnitsResponse);

        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode jobIdNode = objectMapper.createObjectNode();
        jobIdNode.put("validationJobId", "hJobId");
        String verboseMessage = "Validation Started: " + jobIdNode.toString();

        UnitProgressStatus status = mock(UnitProgressStatus.class, RETURNS_DEEP_STUBS);
        when(status.getUnitName()).thenReturn("StartValidation");
        when(status.getMessage().getVerboseMessage()).thenReturn(verboseMessage);

        ListJobUnitProgressResponse unitProgressResponse =
                ListJobUnitProgressResponse.builder().items(singletonList(status)).build();
        when(mockJobProgressClient.listJobUnitProgress(any(ListJobUnitProgressRequest.class)))
                .thenReturn(unitProgressResponse);

        helper.fetchJobStatus(rackSerial, region);

        verify(mockNcpJobDetailsDao, times(1)).updateNcpJobDetails(inProgressDetail);
        verify(inProgressDetail, times(1)).setJobId("hJobId");
    }

    @Test
    void testFetchJobStatus_noJobsInProgress() {
        String rackSerial = "RSN1";
        String region = "us-phx";
        NcpJobDetails detail = mock(NcpJobDetails.class);
        when(detail.getJobStatus()).thenReturn(JobStatus.COMPLETED);

        List<NcpJobDetails> jobDetails = List.of(detail);
        when(mockNcpJobDetailsDao.getNcpJobDetailsForRack(rackSerial)).thenReturn(jobDetails);

        Map<String, NcpClientHelper.ValidationJobRuntimeInfo> jobStatusMap =
                helper.fetchJobStatus(rackSerial, region);
        assertTrue(jobStatusMap.isEmpty());
    }

    @MockitoSettings(strictness = Strictness.LENIENT)
    @Test
    void testFetchJobStatus_errorOnJobClient() {
        String rackSerial = "RSN1";
        String region = "us-phx";
        NcpJobDetails inProgressDetail = mock(NcpJobDetails.class);
        when(inProgressDetail.getJobStatus()).thenReturn(JobStatus.IN_PROGRESS);
        when(inProgressDetail.getDeviceName()).thenReturn("DeviceA");
        when(inProgressDetail.getJobId()).thenReturn("jobA");
        List<NcpJobDetails> jobDetails = List.of(inProgressDetail);

        when(mockNcpJobDetailsDao.getNcpJobDetailsForRack(rackSerial)).thenReturn(jobDetails);

        when(mockClientSetup.getNcpClient(mockConfig, region)).thenReturn(mockJobsClient);
        when(mockJobsClient.getJob(any(GetJobRequest.class)))
                .thenThrow(new RuntimeException("err"));

        assertThrows(RenderableException.class, () -> helper.fetchJobStatus(rackSerial, region));
    }

    @Test
    void testFetchJobStatus_jobMemoization_singleGetCallForSameJob() {
        String rackSerial = "RSN3";
        String region = "us-phx";

        NcpJobDetails d1 = mock(NcpJobDetails.class);
        when(d1.getJobStatus()).thenReturn(JobStatus.IN_PROGRESS);
        when(d1.getJobId()).thenReturn("sameJob");
        when(d1.getDeviceName()).thenReturn("Dev1");

        NcpJobDetails d2 = mock(NcpJobDetails.class);
        when(d2.getJobStatus()).thenReturn(JobStatus.IN_PROGRESS);
        when(d2.getJobId()).thenReturn("sameJob");
        when(d2.getDeviceName()).thenReturn("Dev2");

        when(mockNcpJobDetailsDao.getNcpJobDetailsForRack(rackSerial)).thenReturn(List.of(d1, d2));
        when(mockClientSetup.getNcpClient(mockConfig, region)).thenReturn(mockJobsClient);

        JobRequest jobRequest = mock(JobRequest.class);
        when(jobRequest.getJobType()).thenReturn(JobType.HEALTH_CHECK);

        Job jobPending = mock(Job.class);
        when(jobPending.getRequest()).thenReturn(jobRequest);
        when(jobPending.getState()).thenReturn(Job.State.Pending);

        when(mockJobsClient.getJob(any(GetJobRequest.class)))
                .thenReturn(GetJobResponse.builder().job(jobPending).build());

        helper.fetchJobStatus(rackSerial, region);

        // Should only call client once due to memoization
        verify(mockJobsClient, times(1)).getJob(any(GetJobRequest.class));
    }

    // Edge tests for getValidationJobId error branches (simulate JSON error, missing jobId, etc.)
    @MockitoSettings(strictness = Strictness.LENIENT)
    @Test
    void testGetValidationJobId_prefixParsingAndMissingJobId_orMaintenancePrecheck()
            throws Exception {
        String jobId = "prvJob";
        String region = "us-phx";

        // Real helper so we can invoke the private method via reflection
        LvvServiceApiConfiguration mockConfig = mock(LvvServiceApiConfiguration.class);
        NcpClientSetup mockClientSetup = mock(NcpClientSetup.class);
        NcpJobDetailsDao mockDao = mock(NcpJobDetailsDao.class);
        NcpClientHelper localHelper = new NcpClientHelper(mockConfig, mockClientSetup, mockDao);

        JobProgressClient mockJobProgressClient = mock(JobProgressClient.class);
        when(mockClientSetup.getNcpJobProgressClient(eq(mockConfig), eq(region)))
                .thenReturn(mockJobProgressClient);

        UnitProgress up1 = mock(UnitProgress.class);
        when(up1.getUnitName()).thenReturn("StartValidation");
        UnitProgress up2 = mock(UnitProgress.class);
        when(up2.getUnitName()).thenReturn("PreChecks");
        List<UnitProgress> unitProgresses = List.of(up1, up2);

        ListJobUnitsResponse listJobUnitsResponse = mock(ListJobUnitsResponse.class);
        when(listJobUnitsResponse.getItems()).thenReturn(unitProgresses);
        when(listJobUnitsResponse.getOpcNextPage()).thenReturn(null);

        UnitProgressStatus upsPrechecks = mock(UnitProgressStatus.class, RETURNS_DEEP_STUBS);
        when(upsPrechecks.getUnitName()).thenReturn("PreChecks");
        when(upsPrechecks.getMessage().getVerboseMessage())
                .thenReturn("device(s) are in maintenance stateABC");

        List<UnitProgressStatus> precheckStatus = List.of(upsPrechecks);

        ListJobUnitProgressResponse ljpr = mock(ListJobUnitProgressResponse.class);
        when(ljpr.getItems()).thenReturn(precheckStatus);

        RetryHelper<ListJobUnitsResponse> unitRh = mock(RetryHelper.class);
        when(unitRh.run()).thenReturn(listJobUnitsResponse);

        RetryHelper<ListJobUnitProgressResponse> unitProgRh = mock(RetryHelper.class);
        when(unitProgRh.run()).thenReturn(ljpr);

        try (MockedStatic<RetryHelper> mockedHelper = Mockito.mockStatic(RetryHelper.class)) {
            mockedHelper
                    .when(() -> RetryHelper.newRetryHelper(any(), anyInt(), any()))
                    .thenReturn(unitRh, unitProgRh);

            Method m = privateMethod(localHelper, "getValidationJobId", String.class, String.class);
            InvocationTargetException thrown =
                    assertThrows(
                            InvocationTargetException.class,
                            () -> m.invoke(localHelper, jobId, region));

            Throwable cause = thrown.getCause();
            assertTrue(cause instanceof RenderableException);
            assertTrue(cause.getMessage().contains("maintenance state"));
        }
    }

    @MockitoSettings(strictness = Strictness.LENIENT)
    @Test
    void testGetValidationJobId_jsonParsingError() throws Exception {
        String jobId = "prvJob";
        String region = "us-phx";

        LvvServiceApiConfiguration mockConfig = mock(LvvServiceApiConfiguration.class);
        NcpClientSetup mockClientSetup = mock(NcpClientSetup.class);
        NcpJobDetailsDao mockDao = mock(NcpJobDetailsDao.class);

        NcpClientHelper localHelper = new NcpClientHelper(mockConfig, mockClientSetup, mockDao);

        JobProgressClient mockJobProgressClient = mock(JobProgressClient.class);
        when(mockClientSetup.getNcpJobProgressClient(eq(mockConfig), eq(region)))
                .thenReturn(mockJobProgressClient);

        UnitProgress up1 = mock(UnitProgress.class);
        when(up1.getUnitName()).thenReturn("StartValidation");
        List<UnitProgress> unitProgresses = List.of(up1);

        ListJobUnitsResponse listJobUnitsResponse = mock(ListJobUnitsResponse.class);
        when(listJobUnitsResponse.getItems()).thenReturn(unitProgresses);
        when(listJobUnitsResponse.getOpcNextPage()).thenReturn(null);

        UnitProgressStatus upsStartValidation = mock(UnitProgressStatus.class, RETURNS_DEEP_STUBS);
        when(upsStartValidation.getUnitName()).thenReturn("StartValidation");
        when(upsStartValidation.getMessage().getVerboseMessage())
                .thenReturn("Validation Started: {invalidJson");
        List<UnitProgressStatus> statusList = List.of(upsStartValidation);

        ListJobUnitProgressResponse ljpr = mock(ListJobUnitProgressResponse.class);
        when(ljpr.getItems()).thenReturn(statusList);

        RetryHelper<ListJobUnitsResponse> unitRh = mock(RetryHelper.class);
        when(unitRh.run()).thenReturn(listJobUnitsResponse);

        RetryHelper<ListJobUnitProgressResponse> unitProgRh = mock(RetryHelper.class);
        when(unitProgRh.run()).thenReturn(ljpr);

        try (MockedStatic<RetryHelper> mockedHelper = Mockito.mockStatic(RetryHelper.class)) {
            mockedHelper
                    .when(() -> RetryHelper.newRetryHelper(any(), anyInt(), any()))
                    .thenReturn(unitRh, unitProgRh);

            Method m = privateMethod(localHelper, "getValidationJobId", String.class, String.class);
            InvocationTargetException ex =
                    assertThrows(
                            InvocationTargetException.class,
                            () -> m.invoke(localHelper, jobId, region));
            Throwable cause = ex.getCause();
            assertTrue(cause.getMessage().contains("Error while fetching validation job ID"));
        }
    }

    @MockitoSettings(strictness = Strictness.LENIENT)
    @Test
    void testGetValidationJobId_fetchUnitsFailure() throws Exception {
        String jobId = "prvJob";
        String region = "us-phx";

        LvvServiceApiConfiguration mockConfig = mock(LvvServiceApiConfiguration.class);
        NcpClientSetup mockClientSetup = mock(NcpClientSetup.class);
        NcpJobDetailsDao mockDao = mock(NcpJobDetailsDao.class);

        NcpClientHelper localHelper = new NcpClientHelper(mockConfig, mockClientSetup, mockDao);

        JobProgressClient mockJobProgressClient = mock(JobProgressClient.class);
        when(mockClientSetup.getNcpJobProgressClient(eq(mockConfig), eq(region)))
                .thenReturn(mockJobProgressClient);

        RetryHelper<ListJobUnitsResponse> unitRh = mock(RetryHelper.class);
        when(unitRh.run()).thenThrow(new RuntimeException("fail units"));

        try (MockedStatic<RetryHelper> mockedHelper = Mockito.mockStatic(RetryHelper.class)) {
            mockedHelper
                    .when(() -> RetryHelper.newRetryHelper(any(), anyInt(), any()))
                    .thenReturn(unitRh);

            Method m = privateMethod(localHelper, "getValidationJobId", String.class, String.class);
            InvocationTargetException ex =
                    assertThrows(
                            InvocationTargetException.class,
                            () -> m.invoke(localHelper, jobId, region));
            Throwable cause = ex.getCause();
            assertTrue(cause.getMessage().contains("Failed to fetch job units"));
        }
    }

    @Test
    void testGetTargetsForDevices() throws Exception {
        var localHelper = new NcpClientHelper(mockConfig, mockClientSetup, mockNcpJobDetailsDao);
        var devices = List.of("devA", "devB");
        var method1 = privateMethod(localHelper, "getTargetsForDevices", List.class, String.class);
        var result = method1.invoke(localHelper, devices, "region1");
        assertNotNull(result);
        assertEquals(2, ((List<?>) result).size());
        var method2 = privateMethod(localHelper, "getTargetsForDevices", List.class, String.class);
        var result2 = method2.invoke(localHelper, List.of(), "region2");
        assertNotNull(result2);
        assertEquals(1, ((List<?>) result2).size());
    }
}
