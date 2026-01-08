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
import com.oracle.pic.networking.lvv.service.kiev.ValidationFailureResult;
import com.oracle.pic.networking.lvv.service.utils.RetryHelper;
import com.oracle.pic.networking.ncp.*;
import com.oracle.pic.networking.ncp.model.*;
import com.oracle.pic.networking.ncp.requests.*;
import com.oracle.pic.networking.ncp.responses.*;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
        when(mockJobResultsClient.getJobResult(any())).thenReturn(mockJobResultResp);
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

        InputStream resultInputStream = mock(InputStream.class);
        stubJobResultsClient(region, resultInputStream);

        NcpJobResultProcessor mockResultProcessor = mock(NcpJobResultProcessor.class);
        doNothing().when(mockResultProcessor).processJobResult(any());
        when(mockResultProcessor.buildValidationFailureResults(rackSerial, rackUnit))
                .thenReturn(List.of());

        helper.setJobResultProcessorFactory((is, dao) -> mockResultProcessor);

        List<ValidationFailureResult> results =
                helper.getNcpJobOutput(jobId, region, rackSerial, rackUnit);
        assertNotNull(results);
    }

    @Test
    void testGetNcpJobOutput_processorThrows() {
        String jobId = "job1";
        String region = "us-phx";
        String rackSerial = "RSN1";
        String rackUnit = "RU1";

        InputStream resultInputStream = mock(InputStream.class);
        stubJobResultsClient(region, resultInputStream);

        NcpJobResultProcessor mockResultProcessor = mock(NcpJobResultProcessor.class);
        doThrow(new RuntimeException("processing fails"))
                .when(mockResultProcessor)
                .processJobResult(any());

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
        when(mockJobResultsClient.getJobResult(any()))
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
                            "HEALTH_CHECK", "RSN1", payload, deviceNames, mockScope, region);

            assertNotNull(jobList);
            assertEquals(numDevices, jobList.size());
        }
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
                            "PER_RACK_VALIDATION_JOB",
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
                                            "PER_RACK_VALIDATION_JOB",
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
    void testFetchJobStatus_allStates() {
        String rackSerial = "RSN1";
        String region = "us-phx";
        NcpJobDetails inProgressDetail = mock(NcpJobDetails.class);
        when(inProgressDetail.getJobStatus()).thenReturn(JobStatus.IN_PROGRESS);
        when(inProgressDetail.getDeviceName()).thenReturn("DeviceA");

        List<NcpJobDetails> jobDetails = List.of(inProgressDetail);

        when(mockNcpJobDetailsDao.getNcpJobDetailsForRack(rackSerial)).thenReturn(jobDetails);

        JobRequest jobRequest = mock(JobRequest.class);
        when(jobRequest.getJobType()).thenReturn("HEALTH_CHECK");
        Job mockJob = mock(Job.class);
        when(mockJob.getRequest()).thenReturn(jobRequest);
        when(mockJob.getState()).thenReturn(Job.State.Failed);

        when(mockClientSetup.getNcpClient(mockConfig, region)).thenReturn(mockJobsClient);
        when(mockJobsClient.getJob(any()))
                .thenReturn(GetJobResponse.builder().job(mockJob).build());

        Map<String, JobStatus> jobStatusMap = helper.fetchJobStatus(rackSerial, region);
        assertEquals(1, jobStatusMap.size());
        assertEquals(JobStatus.FAILED, jobStatusMap.get("DeviceA"));
    }

    @Test
    void testFetchJobStatus_perRackValidation_triggersHealthCheck() {
        // Setup
        String rackSerial = "RSN1";
        String region = "us-phx";

        // Mocked job details for the device (IN_PROGRESS state, jobId 'prvJob')
        NcpJobDetails inProgressDetail = mock(NcpJobDetails.class);
        when(inProgressDetail.getJobStatus()).thenReturn(JobStatus.IN_PROGRESS);
        when(inProgressDetail.getDeviceName()).thenReturn("DeviceA");
        when(inProgressDetail.getJobId()).thenReturn("prvJob");
        List<NcpJobDetails> jobDetails = List.of(inProgressDetail);

        // Use class-level mocks
        when(mockNcpJobDetailsDao.getNcpJobDetailsForRack(rackSerial)).thenReturn(jobDetails);
        when(mockClientSetup.getNcpClient(any(), eq(region))).thenReturn(mockJobsClient);
        when(mockClientSetup.getNcpJobProgressClient(any(), eq(region)))
                .thenReturn(mockJobProgressClient);

        // Mock getJob to return a PER_RACK_VALIDATION_JOB in Succeeded state
        JobRequest jobRequest = mock(JobRequest.class);
        when(jobRequest.getJobType()).thenReturn("PER_RACK_VALIDATION_JOB");

        Job mockJob = mock(Job.class);
        when(mockJob.getRequest()).thenReturn(jobRequest);
        when(mockJob.getState()).thenReturn(Job.State.Succeeded);
        when(mockJob.getId()).thenReturn("prvJob");
        when(mockJobsClient.getJob(any(GetJobRequest.class)))
                .thenReturn(GetJobResponse.builder().job(mockJob).build());

        // CRITICAL PART: Mock job units response to empty, so getValidationJobId() will return null
        ListJobUnitsResponse jobUnitsResponse =
                ListJobUnitsResponse.builder()
                        .items(Collections.emptyList())
                        .opcNextPage(null) // No pagination
                        .build();
        when(mockJobProgressClient.listJobUnits(any(ListJobUnitsRequest.class)))
                .thenReturn(jobUnitsResponse);

        // Execute
        Map<String, JobStatus> jobStatusMap = helper.fetchJobStatus(rackSerial, region);

        // Verify
        assertEquals(JobStatus.FAILED, jobStatusMap.get("DeviceA"));
    }

    @MockitoSettings(strictness = Strictness.LENIENT)
    @Test
    void testFetchJobStatus_updatesJobIdIfTriggered() throws Exception {
        String rackSerial = "RSN1";
        String region = "us-phx";

        // Mock details
        NcpJobDetails inProgressDetail = mock(NcpJobDetails.class);
        when(inProgressDetail.getJobStatus()).thenReturn(JobStatus.IN_PROGRESS);
        when(inProgressDetail.getJobId()).thenReturn("prvJob");
        List<NcpJobDetails> jobDetails = List.of(inProgressDetail);

        // Use class-level mocks
        when(mockNcpJobDetailsDao.getNcpJobDetailsForRack(rackSerial)).thenReturn(jobDetails);

        // Mock JobsClient
        when(mockClientSetup.getNcpClient(eq(mockConfig), eq(region))).thenReturn(mockJobsClient);

        JobRequest jobRequest = mock(JobRequest.class);
        when(jobRequest.getJobType()).thenReturn("PER_RACK_VALIDATION_JOB");

        Job mockJob = mock(Job.class);
        when(mockJob.getRequest()).thenReturn(jobRequest);
        when(mockJob.getState()).thenReturn(Job.State.Pending);
        when(mockJob.getId()).thenReturn("prvJob");
        when(mockJobsClient.getJob(any(GetJobRequest.class)))
                .thenReturn(GetJobResponse.builder().job(mockJob).build());

        // ====== Mocks for getValidationJobId ======

        // Mock JobProgressClient for listJobUnits and listJobUnitProgress
        when(mockClientSetup.getNcpJobProgressClient(eq(mockConfig), eq(region)))
                .thenReturn(mockJobProgressClient);

        // 1. listJobUnits returns a StartValidation unit
        UnitProgress startValidationUnit = mock(UnitProgress.class);
        when(startValidationUnit.getUnitName()).thenReturn("StartValidation");
        ListJobUnitsResponse jobUnitsResponse =
                ListJobUnitsResponse.builder()
                        .items(singletonList(startValidationUnit))
                        .opcNextPage(null)
                        .build();
        when(mockJobProgressClient.listJobUnits(any(ListJobUnitsRequest.class)))
                .thenReturn(jobUnitsResponse);

        // 2. listJobUnitProgress returns a UnitProgressStatus with "Validation Started: ..."
        // message
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode jobIdNode = objectMapper.createObjectNode();
        jobIdNode.put("validationJobId", "hJobId");
        String verboseMessage = "Validation Started: " + jobIdNode.toString();

        // Use RETURNS_DEEP_STUBS to mock getMessage().getVerboseMessage()
        UnitProgressStatus status = mock(UnitProgressStatus.class, RETURNS_DEEP_STUBS);
        when(status.getUnitName()).thenReturn("StartValidation");
        when(status.getMessage().getVerboseMessage()).thenReturn(verboseMessage);

        ListJobUnitProgressResponse unitProgressResponse =
                ListJobUnitProgressResponse.builder().items(singletonList(status)).build();
        when(mockJobProgressClient.listJobUnitProgress(any(ListJobUnitProgressRequest.class)))
                .thenReturn(unitProgressResponse);

        // ====== Execute ======
        helper.fetchJobStatus(rackSerial, region);

        // ====== Verify ======
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

        Map<String, JobStatus> jobStatusMap = helper.fetchJobStatus(rackSerial, region);
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
        when(mockJobsClient.getJob(any())).thenThrow(new RuntimeException("client error"));

        assertThrows(RenderableException.class, () -> helper.fetchJobStatus(rackSerial, region));
    }

    // Edge tests for getValidationJobId error branches (simulate JSON error, missing jobId,
    // specific verboseMessage content)
    @MockitoSettings(strictness = Strictness.LENIENT)
    @Test
    void testGetValidationJobId_prefixParsingAndMissingJobId() throws Exception {
        String jobId = "prvJob";
        String region = "us-phx";

        // Real helper so we can invoke the private method via reflection
        LvvServiceApiConfiguration mockConfig = mock(LvvServiceApiConfiguration.class);
        NcpClientSetup mockClientSetup = mock(NcpClientSetup.class);
        NcpJobDetailsDao mockDao = mock(NcpJobDetailsDao.class);
        NcpClientHelper localHelper = new NcpClientHelper(mockConfig, mockClientSetup, mockDao);

        // Mocks for ProgressClient and RetryHelper flows
        JobProgressClient mockJobProgressClient = mock(JobProgressClient.class);
        when(mockClientSetup.getNcpJobProgressClient(eq(mockConfig), eq(region)))
                .thenReturn(mockJobProgressClient);

        // Setup unit progresses: StartValidation and PreChecks
        UnitProgress up1 = mock(UnitProgress.class);
        when(up1.getUnitName()).thenReturn("StartValidation");
        UnitProgress up2 = mock(UnitProgress.class);
        when(up2.getUnitName()).thenReturn("PreChecks");
        List<UnitProgress> unitProgresses = List.of(up1, up2);

        ListJobUnitsResponse listJobUnitsResponse = mock(ListJobUnitsResponse.class);
        when(listJobUnitsResponse.getItems()).thenReturn(unitProgresses);
        when(listJobUnitsResponse.getOpcNextPage()).thenReturn(null);

        // PreChecks: with maintenance message (should trigger the specific RenderableException)
        UnitProgressStatus upsPrechecks = mock(UnitProgressStatus.class, RETURNS_DEEP_STUBS);
        when(upsPrechecks.getUnitName()).thenReturn("PreChecks");
        when(upsPrechecks.getMessage().getVerboseMessage())
                .thenReturn("device(s) are in maintenance stateABC");

        List<UnitProgressStatus> precheckStatus = List.of(upsPrechecks);

        ListJobUnitProgressResponse ljpr = mock(ListJobUnitProgressResponse.class);
        when(ljpr.getItems()).thenReturn(precheckStatus);

        // Mocks for RetryHelper
        RetryHelper<ListJobUnitsResponse> unitRh = mock(RetryHelper.class);
        when(unitRh.run()).thenReturn(listJobUnitsResponse);

        RetryHelper<ListJobUnitProgressResponse> unitProgRh = mock(RetryHelper.class);
        when(unitProgRh.run()).thenReturn(ljpr);

        try (MockedStatic<RetryHelper> mockedHelper = Mockito.mockStatic(RetryHelper.class)) {
            mockedHelper
                    .when(() -> RetryHelper.newRetryHelper(any(), anyInt(), any()))
                    .thenReturn(unitRh, unitProgRh);

            // Private method reflectively called; Should throw on prechecks/maintenance
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

        // Setup
        LvvServiceApiConfiguration mockConfig = mock(LvvServiceApiConfiguration.class);
        NcpClientSetup mockClientSetup = mock(NcpClientSetup.class);
        NcpJobDetailsDao mockDao = mock(NcpJobDetailsDao.class);

        NcpClientHelper localHelper = new NcpClientHelper(mockConfig, mockClientSetup, mockDao);

        JobProgressClient mockJobProgressClient = mock(JobProgressClient.class);
        when(mockClientSetup.getNcpJobProgressClient(eq(mockConfig), eq(region)))
                .thenReturn(mockJobProgressClient);

        // StartValidation unit
        UnitProgress up1 = mock(UnitProgress.class);
        when(up1.getUnitName()).thenReturn("StartValidation");
        List<UnitProgress> unitProgresses = List.of(up1);

        ListJobUnitsResponse listJobUnitsResponse = mock(ListJobUnitsResponse.class);
        when(listJobUnitsResponse.getItems()).thenReturn(unitProgresses);
        when(listJobUnitsResponse.getOpcNextPage()).thenReturn(null);

        // StartValidation status with malformed JSON message
        UnitProgressStatus upsStartValidation = mock(UnitProgressStatus.class, RETURNS_DEEP_STUBS);
        when(upsStartValidation.getUnitName()).thenReturn("StartValidation");
        when(upsStartValidation.getMessage().getVerboseMessage())
                .thenReturn("Validation Started: {invalidJson");
        List<UnitProgressStatus> statusList = List.of(upsStartValidation);

        ListJobUnitProgressResponse ljpr = mock(ListJobUnitProgressResponse.class);
        when(ljpr.getItems()).thenReturn(statusList);

        // Mocks for RetryHelper
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
            // exception cause is RenderableException
            Throwable cause = ex.getCause();
            assertTrue(cause.getMessage().contains("Error while fetching validation job ID"));
        }
    }

    // If allowed by test utils, test getTargetsForDevices (public for testability) with empty and
    // filled device lists
    @Test
    void testGetTargetsForDevices() throws Exception {
        var localHelper = new NcpClientHelper(mockConfig, mockClientSetup, mockNcpJobDetailsDao);
        var devices = List.of("devA", "devB");
        var method1 = privateMethod(localHelper, "getTargetsForDevices", List.class, String.class);
        var result = method1.invoke(localHelper, devices, "region1");
        assertNotNull(result);
        assertEquals(2, ((List<?>) result).size());
        // Empty list returns one region type
        var method2 = privateMethod(localHelper, "getTargetsForDevices", List.class, String.class);
        var result2 = method2.invoke(localHelper, List.of(), "region2");
        assertNotNull(result2);
        assertEquals(1, ((List<?>) result2).size());
    }
}
