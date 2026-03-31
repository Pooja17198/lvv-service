package com.oracle.pic.networking.lvv.service.config;

import com.oracle.bmc.http.ClientConfigurator;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** CommonClientConfigurator will register metrics filter and LoggingFeature on client */
@AllArgsConstructor
@Slf4j
public class CommonClientConfigurator implements ClientConfigurator {

    private String serviceProvider;

    @Override
    public void customizeBuilder(ClientBuilder clientBuilder) {}

    @Override
    public void customizeClient(Client client) {
        ServiceProviderMetricsFilter serviceProviderMetricsFilter =
                new ServiceProviderMetricsFilter(serviceProvider);
        log.info("register serviceProviderMetricsFilter to service provider: {}", serviceProvider);
        client.register(serviceProviderMetricsFilter);
        log.info(
                "Completed register serviceProviderMetricsFilter to service provider: {}",
                serviceProvider);
    }
}
