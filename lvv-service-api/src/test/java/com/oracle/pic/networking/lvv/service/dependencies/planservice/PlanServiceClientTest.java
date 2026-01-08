package com.oracle.pic.networking.lvv.service.dependencies.planservice;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.pic.networking.autonet.plan.service.PlanServiceVClient;
import com.oracle.pic.networking.lvv.service.config.PlanServiceConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PlanServiceClientTest {
    private BasicAuthenticationDetailsProvider authProvider;
    private PlanServiceConfiguration config;
    private PlanServiceClient client;

    @BeforeEach
    void setup() {
        authProvider = mock(BasicAuthenticationDetailsProvider.class);
        config = mock(PlanServiceConfiguration.class);
    }

    @Test
    void getPlanServiceVClient_setsCorrectEndpoint() {
        String fakeRegion = "my-region";
        String endpointPattern = "https://plan.%s.example.com";
        String expectedEndpoint = "https://plan.my-region.example.com";

        // Mock config to provide correct substitutions
        when(config.getEndpoint()).thenReturn(endpointPattern);
        when(config.getMaxRetries()).thenReturn(3);
        when(config.getConnectTimeoutInMs()).thenReturn(1000);
        when(config.getReadTimeoutInMs()).thenReturn(2000);

        client = new PlanServiceClient(config, authProvider);

        // Use Mockito's mockConstruction to intercept PlanServiceVClient construction.
        try (var mocked = mockConstruction(PlanServiceVClient.class)) {
            PlanServiceVClient result = client.getPlanServiceVClient(fakeRegion);

            // Capture and assert the endpoint passed to setEndpoint
            var captor = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(result).setEndpoint(captor.capture());
            assertEquals(expectedEndpoint, captor.getValue());
        }
    }

    @Test
    void getPlanServiceVClient_returnsNonNull() {

        when(config.getEndpoint()).thenReturn("https://plan.%s.example.com");
        when(config.getMaxRetries()).thenReturn(2);
        when(config.getConnectTimeoutInMs()).thenReturn(1000);
        when(config.getReadTimeoutInMs()).thenReturn(2000);
        client = new PlanServiceClient(config, authProvider);

        // Use mockConstruction for PlanServiceVClient so no real API calls or unexpected NPEs
        try (var mocked = mockConstruction(PlanServiceVClient.class)) {
            PlanServiceVClient clientResult = client.getPlanServiceVClient("us-phoenix-1");
            assertNotNull(clientResult);
        }
    }
}
