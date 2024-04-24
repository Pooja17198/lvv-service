package com.oracle.pic.networking.lvv.service.dependencies.ncp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.oracle.bmc.model.BmcException;
import com.oracle.pic.networking.ncp.JobsClient;
import com.oracle.pic.networking.ncp.model.Job;
import com.oracle.pic.networking.ncp.requests.GetJobRequest;
import com.oracle.pic.networking.ncp.responses.GetJobResponse;
import java.util.Date;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * This class will perform tests on the NcpConnector class, mainly focusing on the getNcpSyncJob
 * method. The getNcpSyncJob method is used to retrieve a NCP sync job by job Id.
 */
public class NcpConnectorTest {

    /** Test the scenario where the getNcpSyncJob method successfully retrieves a job. */
    @Test
    public void testGetNcpSyncJobSuccess() throws BmcException {
        String jobId = "jobId123";
        Job job = Job.builder().id(jobId).endDate(new Date()).state(Job.State.Succeeded).build();

        JobsClient mockedJobsClient = Mockito.mock(JobsClient.class);
        when(mockedJobsClient.getJob(any(GetJobRequest.class)))
                .thenAnswer(
                        invocation -> {
                            GetJobRequest request = invocation.getArgument(0);
                            if (request.getJobId().equals(jobId)) {
                                // wrap the job in a GetJobResponse
                                return GetJobResponse.builder().job(job).build();
                            }
                            return null;
                        });

        NcpConnector connector = new NcpConnector(mockedJobsClient);
        Job result = connector.getNcpSyncJob(jobId);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(jobId, result.getId());
    }

    /** Test the scenario where the getNcpSyncJob method fails to retrieve a job. */
    @Test
    public void testGetNcpSyncJobFail() throws BmcException {
        String jobId = "jobId123";
        JobsClient mockedJobsClient = Mockito.mock(JobsClient.class);
        when(mockedJobsClient.getJob(any(GetJobRequest.class))).thenThrow(BmcException.class);

        NcpConnector connector = new NcpConnector(mockedJobsClient);

        Assertions.assertThrows(
                BmcException.class,
                () -> {
                    connector.getNcpSyncJob(jobId);
                });
    }
}
