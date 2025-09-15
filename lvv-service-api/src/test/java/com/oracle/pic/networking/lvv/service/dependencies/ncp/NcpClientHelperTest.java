// package com.oracle.pic.networking.lvv.service.dependencies.ncp;
//
// import static org.junit.jupiter.api.Assertions.*;
// import static org.mockito.ArgumentMatchers.any;
// import static org.mockito.Mockito.*;
//
// import com.oracle.pic.commons.metrics.MetricsScope;
// import com.oracle.pic.networking.lvv.service.kiev.LinkStatus;
// import com.oracle.pic.networking.lvv.service.kiev.LldpStatus;
// import com.oracle.pic.networking.lvv.service.kiev.ValidationFailureResult;
// import com.oracle.pic.networking.lvv.service.models.ncp.JobType;
// import com.oracle.pic.networking.lvv.service.utils.GeneralUtils;
// import com.oracle.pic.networking.ncp.JobsClient;
// import com.oracle.pic.networking.ncp.model.Job;
// import com.oracle.pic.networking.ncp.requests.CreateJobRequest;
// import com.oracle.pic.networking.ncp.requests.GetJobRequest;
// import com.oracle.pic.networking.ncp.responses.CreateJobResponse;
// import com.oracle.pic.networking.ncp.responses.GetJobResponse;
// import java.util.List;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.ExtendWith;
// import org.mockito.InjectMocks;
// import org.mockito.Mock;
// import org.mockito.MockedStatic;
// import org.mockito.junit.jupiter.MockitoExtension;
//
// @ExtendWith(MockitoExtension.class)
// public class NcpClientHelperTest {
//
//     @Mock private JobsClient ncpApiJobsClient;
//
//     @InjectMocks private NcpClientHelper ncpClientHelper;
//
//     private MetricsScope metrics;
//
//     @BeforeEach
//     void setup() {
//         metrics = mock(MetricsScope.class);
//     }
//
//     @Test
//     void testGetNcpJobOutput() throws Exception {
//         List<ValidationFailureResult> result = ncpClientHelper.getNcpJobOutput("jobId");
//         assertNotNull(result);
//         assertEquals(1, result.size());
//         ValidationFailureResult validationFailureResult = result.get(0);
//         assertEquals("proj1234", validationFailureResult.getProjectId());
//         assertEquals(LinkStatus.UP, validationFailureResult.getLinkStatus());
//         assertEquals(LldpStatus.MISMATCH, validationFailureResult.getLldpStatus());
//     }
//
//     @Test
//     void testCreateJobSuccess() {
//         // Arrange
//         CreateJobResponse createJobResponse =
//                 CreateJobResponse.builder().job(Job.builder().id("jobId").build()).build();
//
//
// when(ncpApiJobsClient.createJob(any(CreateJobRequest.class))).thenReturn(createJobResponse);
//
//         // Act
//         Job job =
//                 ncpClientHelper.createJob(
//                         JobType.HEALTH_CHECK.toString(), "jobArguments", List.of("device1"));
//
//         // Assert
//         assertNotNull(job);
//         assertEquals("jobId", job.getId());
//         verify(ncpApiJobsClient, times(1)).createJob(any(CreateJobRequest.class));
//     }
//
//     @Test
//     void testCreateJobFailure() {
//         // Arrange
//         when(ncpApiJobsClient.createJob(any(CreateJobRequest.class)))
//                 .thenThrow(new RuntimeException("Mocked exception"));
//
//         // Act
//         Job job =
//                 ncpClientHelper.createJob(
//                         JobType.HEALTH_CHECK.toString(), "jobArguments", List.of("device1"));
//
//         // Assert
//         assertNotNull(job);
//         assertNull(job.getId());
//         verify(ncpApiJobsClient, times(3)).createJob(any(CreateJobRequest.class));
//     }
//
//     @Test
//     void testPollJobToFetchResultSuccess() {
//         // Arrange
//         Job job = Job.builder().id("jobId").state(Job.State.Pending).build();
//         GetJobResponse getJobResponse =
//                 GetJobResponse.builder()
//                         .job(Job.builder().id("jobId").state(Job.State.Succeeded).build())
//                         .build();
//         when(ncpApiJobsClient.getJob(any(GetJobRequest.class))).thenReturn(getJobResponse);
//
//         // Act
//         boolean result = ncpClientHelper.pollJobToFetchResult(job, "rackSerialNumber", metrics);
//
//         // Assert
//         assertTrue(result);
//         verify(ncpApiJobsClient, times(1)).getJob(any(GetJobRequest.class));
//         verify(metrics, never()).emit(anyString(), anyInt());
//     }
//
//     @Test
//     void testPollJobToFetchResultTimeout() {
//         try (MockedStatic<GeneralUtils> mocked = mockStatic(GeneralUtils.class)) {
//             // Make jitterSleep() do nothing instead of sleeping
//             mocked.when(() -> GeneralUtils.jitterSleep(any(), anyDouble())).thenAnswer(inv ->
// null);
//
//             // Arrange
//             Job job = Job.builder().id("jobId").state(Job.State.Pending).build();
//             GetJobResponse getJobResponse = GetJobResponse.builder().job(job).build();
//             when(ncpApiJobsClient.getJob(any(GetJobRequest.class))).thenReturn(getJobResponse);
//
//             // Act
//             boolean result = ncpClientHelper.pollJobToFetchResult(job, "rackSerialNumber",
// metrics);
//
//             // Assert
//             assertFalse(result);
//             verify(ncpApiJobsClient, times(10)).getJob(any(GetJobRequest.class));
//             verify(metrics, times(1)).withDimension("failureReason", "Timeout");
//             verify(metrics, times(1)).emit("Failure", 1);
//         }
//     }
//
//     @Test
//     void testPollJobToFetchResultFailure() {
//         // Arrange
//         Job job = Job.builder().id("jobId").state(Job.State.Pending).build();
//         GetJobResponse getJobResponse =
//                 GetJobResponse.builder()
//                         .job(Job.builder().id("jobId").state(Job.State.Failed).build())
//                         .build();
//         when(ncpApiJobsClient.getJob(any(GetJobRequest.class))).thenReturn(getJobResponse);
//
//         // Act
//         boolean result = ncpClientHelper.pollJobToFetchResult(job, "rackSerialNumber", metrics);
//
//         // Assert
//         assertTrue(result);
//         verify(ncpApiJobsClient, times(1)).getJob(any(GetJobRequest.class));
//         verify(metrics, never()).emit(anyString(), anyInt());
//     }
//
//     @Test
//     void testPollJobToFetchResultError() {
//         // Arrange
//         Job job = Job.builder().id("jobId").state(Job.State.Pending).build();
//         GetJobResponse getJobResponse =
//                 GetJobResponse.builder()
//                         .job(Job.builder().id("jobId").state(Job.State.Error).build())
//                         .build();
//         when(ncpApiJobsClient.getJob(any(GetJobRequest.class))).thenReturn(getJobResponse);
//
//         // Act
//         boolean result = ncpClientHelper.pollJobToFetchResult(job, "rackSerialNumber", metrics);
//
//         // Assert
//         assertFalse(result);
//         verify(ncpApiJobsClient, times(1)).getJob(any(GetJobRequest.class));
//         verify(metrics, times(1)).withDimension("failureReason", "FailToExecute");
//         verify(metrics, times(1)).emit("Failure", 1);
//     }
//
//     @Test
//     void testPollJobToFetchResultException() {
//         // Arrange
//         Job job = Job.builder().id("jobId").state(Job.State.Pending).build();
//         when(ncpApiJobsClient.getJob(any(GetJobRequest.class)))
//                 .thenThrow(new RuntimeException("Mocked exception"));
//
//         // Act
//         boolean result = ncpClientHelper.pollJobToFetchResult(job, "rackSerialNumber", metrics);
//
//         // Assert
//         assertFalse(result);
//         verify(ncpApiJobsClient, times(3)).getJob(any(GetJobRequest.class));
//     }
// }
