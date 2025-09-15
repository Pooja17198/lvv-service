package com.oracle.pic.networking.lvv.service.dependencies.ncp;

import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;
import com.oracle.bmc.auth.S2SAuthenticationDetailsProvider;
import com.oracle.pic.networking.lvv.service.config.LvvServiceApiConfiguration;
import com.oracle.pic.networking.ncp.JobProgressClient;
import com.oracle.pic.networking.ncp.JobResultsClient;
import com.oracle.pic.networking.ncp.JobsClient;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NcpClientSetup {

    String endpoint;
    AbstractAuthenticationDetailsProvider authProvider;

    public NcpClientSetup() {
        this.authProvider =
                S2SAuthenticationDetailsProvider.builder().useInstancePrincipals().build();
    }

    public JobsClient getNcpClient(LvvServiceApiConfiguration config, String region) {
        log.info("Creating NCP Jobs Client...");

        JobsClient jobsClient;
        String endpoint = String.format(config.getNcpServiceConfiguration().getEndpoint(), region);

        jobsClient = JobsClient.builder().endpoint(endpoint).build(authProvider);
        return jobsClient;
    }

    public JobResultsClient getNcpJobResultsClient(
            LvvServiceApiConfiguration config, String region) {
        log.info("Creating NCP Job results Client...");

        JobResultsClient jobResultsClient;
        String endpoint = String.format(config.getNcpServiceConfiguration().getEndpoint(), region);

        jobResultsClient = JobResultsClient.builder().endpoint(endpoint).build(authProvider);
        return jobResultsClient;
    }

    public JobProgressClient getNcpJobProgressClient(
            LvvServiceApiConfiguration config, String region) {
        log.info("Creating NCP Job progress Client...");

        JobProgressClient jobProgressClient;
        String endpoint = String.format(config.getNcpServiceConfiguration().getEndpoint(), region);

        jobProgressClient = JobProgressClient.builder().endpoint(endpoint).build(authProvider);
        return jobProgressClient;
    }
}
