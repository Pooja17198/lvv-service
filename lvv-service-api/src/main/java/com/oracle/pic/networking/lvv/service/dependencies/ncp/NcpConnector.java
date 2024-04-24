package com.oracle.pic.networking.lvv.service.dependencies.ncp;

import com.google.inject.name.Named;
import com.oracle.bmc.model.BmcException;
import com.oracle.pic.networking.ncp.JobsClient;
import com.oracle.pic.networking.ncp.model.Job;
import com.oracle.pic.networking.ncp.requests.GetJobRequest;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.PrintWriter;
import java.io.StringWriter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SuppressFBWarnings(value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
public class NcpConnector {
    JobsClient jobsClient;

    public NcpConnector(@Named("NcpJobsClient") JobsClient jobsClient) {
        this.jobsClient = jobsClient;
    }

    public Job getNcpSyncJob(String jobId) throws BmcException, IllegalArgumentException {
        if (jobId == null) {
            throw new IllegalArgumentException("Job ID cannot be null");
        }

        log.info("getNcpSyncJob :: jobId: {}", jobId);

        try {
            Job ncpJob = jobsClient.getJob(GetJobRequest.builder().jobId(jobId).build()).getJob();
            log.info("Get NCP Job={}", ncpJob.toString());
            return ncpJob;
        } catch (BmcException e) {
            e.printStackTrace(new PrintWriter(new StringWriter()));
            log.error(
                    "Error fetching NCP device sync job for jobId: {} \n:{}",
                    jobId,
                    e.getMessage());
            throw (e);
        }
    }
}
