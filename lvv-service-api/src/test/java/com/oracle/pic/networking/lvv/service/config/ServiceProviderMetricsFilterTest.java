package com.oracle.pic.networking.lvv.service.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.sql.Timestamp;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.core.MultivaluedHashMap;
import org.junit.jupiter.api.Test;

class ServiceProviderMetricsFilterTest {

    private final ServiceProviderMetricsFilter filter =
            new ServiceProviderMetricsFilter("PlanService");

    @Test
    void responseFilter_missingStartTime_doesNotThrow() {
        ClientRequestContext requestContext = mock(ClientRequestContext.class);
        ClientResponseContext responseContext = mock(ClientResponseContext.class);

        when(requestContext.getProperty("startTime")).thenReturn(null);

        assertDoesNotThrow(() -> filter.filter(requestContext, responseContext));
        verifyNoInteractions(responseContext);
    }

    @Test
    void responseFilter_invalidStartTimeType_doesNotThrow() {
        ClientRequestContext requestContext = mock(ClientRequestContext.class);
        ClientResponseContext responseContext = mock(ClientResponseContext.class);

        when(requestContext.getProperty("startTime")).thenReturn("bad-value");

        assertDoesNotThrow(() -> filter.filter(requestContext, responseContext));
        verifyNoInteractions(responseContext);
    }

    @Test
    void responseFilter_validStartTime_continuesNormally() {
        ClientRequestContext requestContext = mock(ClientRequestContext.class);
        ClientResponseContext responseContext = mock(ClientResponseContext.class);

        when(requestContext.getProperty("startTime"))
                .thenReturn(new Timestamp(System.currentTimeMillis() - 100));
        when(requestContext.getMethod()).thenReturn("GET");
        when(requestContext.getUri())
                .thenReturn(URI.create("https://example.test/racks/ocid1.rack"));
        when(responseContext.getStatus()).thenReturn(200);
        when(responseContext.getHeaders()).thenReturn(new MultivaluedHashMap<>());

        assertDoesNotThrow(() -> filter.filter(requestContext, responseContext));
    }
}
