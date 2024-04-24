package com.oracle.pic.networking.lvv.service.dependencies.ncp;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.oracle.pic.networking.ncp.JobsClient;
import com.oracle.pic.networking.ncp.model.Job;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
public class NcpService {
    private final JobsClient jobsClient;

    @Inject
    public NcpService(@Named("NcpJobsClient") JobsClient jobsClient) {
        this.jobsClient = jobsClient;
    }

    public Job getNcpJob(String ncpJobId) {
        if (ncpJobId == null) {
            throw new IllegalArgumentException("Job ID cannot be null");
        }
        NcpConnector ncpConnector = new NcpConnector(this.jobsClient);
        Job job = ncpConnector.getNcpSyncJob(ncpJobId);
        return job;
    }
}
