package com.oracle.pic.networking.lvv.service.dependencies.ncp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.oracle.pic.networking.ncp.JobsClient;
import com.oracle.pic.networking.ncp.model.Job;
import com.oracle.pic.networking.ncp.requests.GetJobRequest;
import com.oracle.pic.networking.ncp.responses.GetJobResponse;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
class NcpServiceTest {

    @Test
    void getNcpJob_ValidJobId_ReturnsJob() throws Exception {
        String jobId = "jobId123";
        Job expectedJob =
                Job.builder().id(jobId).endDate(new Date()).state(Job.State.Succeeded).build();
        JobsClient mockJobsClient = Mockito.mock(JobsClient.class);
        GetJobResponse mockGetJobResponse = mock(GetJobResponse.class);
        when(mockGetJobResponse.getJob()).thenReturn(expectedJob);

        GetJobRequest getJobRequest = new GetJobRequest();
        when(mockJobsClient.getJob(getJobRequest)).thenReturn(mockGetJobResponse);
        when(mockJobsClient.getJob(GetJobRequest.builder().jobId(jobId).build()))
                .thenReturn(mockGetJobResponse);

        NcpConnector mockNcpConnector = Mockito.mock(NcpConnector.class);
        PowerMockito.whenNew(NcpConnector.class)
                .withArguments(mockJobsClient)
                .thenReturn(mockNcpConnector);

        NcpService ncpService = new NcpService(mockJobsClient);
        Job resultJob = ncpService.getNcpJob("jobId123");

        assertNotNull(resultJob);
        assertEquals(expectedJob, resultJob);
    }

    @Test
    void getNcpJob_NullJobId_ThrowsException() {
        String jobId = "jobId123";
        Job expectedJob =
                Job.builder().id(jobId).endDate(new Date()).state(Job.State.Succeeded).build();
        NcpConnector mockNcpConnector = mock(NcpConnector.class);
        when(mockNcpConnector.getNcpSyncJob(null)).thenThrow(IllegalArgumentException.class);
        JobsClient mockJobsClient = Mockito.mock(JobsClient.class);
        GetJobResponse mockGetJobResponse = mock(GetJobResponse.class);
        when(mockGetJobResponse.getJob()).thenReturn(expectedJob);

        GetJobRequest getJobRequest = new GetJobRequest();
        when(mockJobsClient.getJob(getJobRequest)).thenReturn(mockGetJobResponse);
        when(mockJobsClient.getJob(GetJobRequest.builder().jobId(jobId).build()))
                .thenReturn(mockGetJobResponse);

        NcpService ncpService = new NcpService(mockJobsClient);

        assertThrows(IllegalArgumentException.class, () -> ncpService.getNcpJob(null));
    }
}
