package com.oracle.pic.networking.lvv.service.dependencies.planservice;

import com.google.inject.Inject;
import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.retrier.RetryConfiguration;
import com.oracle.bmc.waiter.ExponentialBackoffDelayStrategy;
import com.oracle.bmc.waiter.MaxAttemptsTerminationStrategy;
import com.oracle.pic.networking.autonet.plan.service.PlanServiceVClient;
import com.oracle.pic.networking.lvv.service.config.PlanServiceConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PlanServiceClient {

    BasicAuthenticationDetailsProvider authProvider;
    PlanServiceConfiguration config;

    public PlanServiceClient(
            PlanServiceConfiguration config, BasicAuthenticationDetailsProvider authProvider) {
        this.config = config;
        this.authProvider = authProvider;
    }

    private ClientConfiguration getClientConfiguration() {
        RetryConfiguration retryConfig =
                RetryConfiguration.builder()
                        .terminationStrategy(
                                new MaxAttemptsTerminationStrategy(config.getMaxRetries()))
                        .delayStrategy(new ExponentialBackoffDelayStrategy(5000L))
                        .retryCondition(
                                (BmcException e) ->
                                        e.getStatusCode() == 429 || e.getStatusCode() == 500)
                        .build();
        return ClientConfiguration.builder()
                .connectionTimeoutMillis(config.getConnectTimeoutInMs())
                .retryConfiguration(retryConfig)
                .readTimeoutMillis(config.getReadTimeoutInMs())
                .maxAsyncThreads(2)
                .build();
    }

    public PlanServiceVClient getPlanServiceVClient(String region) {
        ClientConfiguration clientConfiguration = getClientConfiguration();
        PlanServiceVClient psPublicClient =
                new PlanServiceVClient(authProvider, clientConfiguration);
        String endpoint = String.format(config.getEndpoint(), region);
        log.info("Initialize plan service with endpoint {}", endpoint);
        psPublicClient.setEndpoint(endpoint);
        return psPublicClient;
    }
}
