package com.oracle.pic.networking.lvv.service.dependencies.ncp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.oracle.pic.commons.exceptions.server.*;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.networking.lvv.service.config.LvvServiceApiConfiguration;
import com.oracle.pic.networking.lvv.service.kiev.ValidationFailureResult;
import com.oracle.pic.networking.lvv.service.utils.RetryHelper;
import com.oracle.pic.networking.ncp.*;
import com.oracle.pic.networking.ncp.model.*;
import com.oracle.pic.networking.ncp.requests.*;
import com.oracle.pic.networking.ncp.responses.*;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.*;
import org.mockito.*;

class NcpClientHelperTest {
    @Mock LvvServiceApiConfiguration mockConfig;
    @Mock NcpClientSetup mockClientSetup;
    @Mock JobsClient mockJobsClient;
    @Mock JobResultsClient mockJobResultsClient;
    @Mock JobProgressClient mockJobProgressClient;

    NcpClientHelper helper;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        helper = new NcpClientHelper(mockConfig, mockClientSetup);
    }

    @Test
    void testGetNcpJobOutput_success() throws Exception {
        String jobId = "job1";
        String region = "us-phx";
        String rackSerial = "RSN123";
        String rackUnit = "RU01";

        // Mock job, job request and state
        JobRequest jobRequest = mock(JobRequest.class);
        when(jobRequest.getJobType()).thenReturn("SomeOtherType");
        Job mockJob = mock(Job.class);
        when(mockJob.getRequest()).thenReturn(jobRequest);

        when(mockClientSetup.getNcpClient(mockConfig, region)).thenReturn(mockJobsClient);
        when(mockJobsClient.getJob(any()))
                .thenReturn(GetJobResponse.builder().job(mockJob).build());

        InputStream resultInputStream = mock(InputStream.class);
        GetJobResultResponse mockJobResultResp = mock(GetJobResultResponse.class);
        when(mockJobResultResp.getInputStream()).thenReturn(resultInputStream);
        when(mockClientSetup.getNcpJobResultsClient(mockConfig, region))
                .thenReturn(mockJobResultsClient);
        when(mockJobResultsClient.getJobResult(any())).thenReturn(mockJobResultResp);

        // Setup a factory for NcpJobResultProcessor
        NcpJobResultProcessor mockResultProcessor = mock(NcpJobResultProcessor.class);
        doNothing().when(mockResultProcessor).processJobResult(any());
        when(mockResultProcessor.buildValidationFailureResults(rackSerial, rackUnit))
                .thenReturn(List.of());

        helper.setJobResultProcessorFactory(is -> mockResultProcessor);

        List<ValidationFailureResult> results =
                helper.getNcpJobOutput(jobId, region, rackSerial, rackUnit);
        assertNotNull(results);
    }

    @Test
    void testCreateJob_success() throws Exception {
        String jobType = "SomeType";
        String payload = "{}";
        List<String> deviceNames = Collections.singletonList("device1");
        MetricsScope mockScope = mock(MetricsScope.class);
        String region = "us-phx";
        JobRequest jobRequest = mock(JobRequest.class);
        Job mockJob = mock(Job.class);
        CreateJobRequest createJobRequest =
                CreateJobRequest.builder().jobRequest(jobRequest).build();
        CreateJobResponse createJobResponse = CreateJobResponse.builder().job(mockJob).build();

        when(mockClientSetup.getNcpClient(mockConfig, region)).thenReturn(mockJobsClient);

        try (MockedStatic<RetryHelper> mockedRetryHelper = Mockito.mockStatic(RetryHelper.class)) {
            RetryHelper<CreateJobResponse> rh = mock(RetryHelper.class);
            when(rh.run()).thenReturn(createJobResponse);
            mockedRetryHelper
                    .when(() -> RetryHelper.newRetryHelper(any(), anyInt(), any()))
                    .thenReturn(rh);
            when(mockJobsClient.createJob(any())).thenReturn(createJobResponse);

            Job resultJob = helper.createJob(jobType, payload, deviceNames, mockScope, region);
            assertEquals(mockJob, resultJob);
        }
    }

    @Test
    void testGetJobType_success() {
        String jobId = "jobId";
        String region = "us-ashburn";
        String expectedType = "MY_TYPE";

        JobRequest jobRequest = mock(JobRequest.class);
        when(jobRequest.getJobType()).thenReturn(expectedType);
        Job job = mock(Job.class);
        when(job.getRequest()).thenReturn(jobRequest);

        when(mockClientSetup.getNcpClient(mockConfig, region)).thenReturn(mockJobsClient);
        when(mockJobsClient.getJob(any())).thenReturn(GetJobResponse.builder().job(job).build());

        String actual = helper.getJobType(jobId, region);
        assertEquals(expectedType, actual);
    }

    @Test
    void testFetchJobStatus_success_forPendingJob() {
        String jobId = "job1";
        String region = "us-phx";
        MetricsScope mockScope = mock(MetricsScope.class);
        JobRequest req = mock(JobRequest.class);
        when(req.getJobType()).thenReturn("MY_TYPE");
        Job mockJob = mock(Job.class);
        when(mockJob.getRequest()).thenReturn(req);
        when(mockJob.getState()).thenReturn(Job.State.Pending);

        when(mockClientSetup.getNcpClient(mockConfig, region)).thenReturn(mockJobsClient);
        when(mockJobsClient.getJob(any()))
                .thenReturn(GetJobResponse.builder().job(mockJob).build());

        String status = helper.fetchJobStatus(jobId, mockScope, region);
        assertEquals(Job.State.Pending.name(), status);
    }

    @Test
    void testGetJobType_throwsOnClientException() {
        String jobId = "bad-job";
        String region = "us-chicago";
        when(mockClientSetup.getNcpClient(mockConfig, region)).thenReturn(mockJobsClient);
        when(mockJobsClient.getJob(any())).thenThrow(new RuntimeException("Client Down"));

        RenderableException ex =
                assertThrows(RenderableException.class, () -> helper.getJobType(jobId, region));
        assertTrue(ex.getMessage().contains("Failed to fetch Job type unexpectedly"));
    }

    @Test
    void testFetchJobStatus_failureJob() {
        String jobId = "failjob";
        String region = "us-xyz";
        MetricsScope mockScope = mock(MetricsScope.class);

        JobRequest req = mock(JobRequest.class);
        when(req.getJobType()).thenReturn("MY_TYPE");
        Job mockJob = mock(Job.class);
        when(mockJob.getRequest()).thenReturn(req);
        when(mockJob.getState()).thenReturn(Job.State.Failed);

        when(mockClientSetup.getNcpClient(mockConfig, region)).thenReturn(mockJobsClient);
        when(mockJobsClient.getJob(any()))
                .thenReturn(GetJobResponse.builder().job(mockJob).build());

        String status = helper.fetchJobStatus(jobId, mockScope, region);
        assertEquals(Job.State.Failed.name(), status);
    }

    @Test
    void testFetchJobStatus_unknownState() {
        String jobId = "unknown-job";
        String region = "us-foo";
        MetricsScope mockScope = mock(MetricsScope.class);

        JobRequest req = mock(JobRequest.class);
        when(req.getJobType()).thenReturn("MY_TYPE");
        Job mockJob = mock(Job.class);
        when(mockJob.getRequest()).thenReturn(req);
        when(mockJob.getState()).thenReturn(null);

        when(mockClientSetup.getNcpClient(mockConfig, region)).thenReturn(mockJobsClient);
        when(mockJobsClient.getJob(any()))
                .thenReturn(GetJobResponse.builder().job(mockJob).build());

        String status = helper.fetchJobStatus(jobId, mockScope, region);
        assertEquals("UNKNOWN", status);
    }

    @Test
    void testCreateJob_throwsOnClientException() {
        String jobType = "BadType";
        String payload = "{}";
        List<String> deviceNames = Collections.singletonList("dev123");
        MetricsScope mockScope = mock(MetricsScope.class);
        String region = "eu-frankfurt-1";
        when(mockClientSetup.getNcpClient(any(), any())).thenReturn(mockJobsClient);
        when(mockJobsClient.createJob(any())).thenThrow(new RuntimeException("Boom"));

        // Mock RetryHelper to invoke lambda and throw
        try (MockedStatic<RetryHelper> mocked = Mockito.mockStatic(RetryHelper.class)) {
            RetryHelper<CreateJobResponse> rh = mock(RetryHelper.class);
            when(rh.run()).thenThrow(new RuntimeException("Boom"));
            mocked.when(() -> RetryHelper.newRetryHelper(any(), anyInt(), any())).thenReturn(rh);

            RenderableException ex =
                    assertThrows(
                            RenderableException.class,
                            () ->
                                    helper.createJob(
                                            jobType, payload, deviceNames, mockScope, region));
            assertTrue(ex.getMessage().contains("Failed to create NCP job unexpectedly"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
