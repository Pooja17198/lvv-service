package com.oracle.pic.networking.lvv.service.resources;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.identity.authentication.Principal;
import com.oracle.pic.identity.authorization.sdk.AuthorizationRequest;
import com.oracle.pic.networking.lvv.service.model.EmitMetricsRequest;
import com.oracle.pic.networking.lvv.service.service.EmitMetricsService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmitMetricsResourceTest {

    @Mock EmitMetricsService emitMetricsService;
    @Mock Principal principal;
    @Mock AuthorizationRequest authorizationRequest;

    EmitMetricsResource resource;

    final String opcRequestId = "req-abc";

    @BeforeEach
    void setup() {
        resource = new EmitMetricsResource(emitMetricsService);
    }

    @Test
    void testEmitMetrics_success_invokesService() {
        EmitMetricsRequest request =
                EmitMetricsRequest.builder()
                        .metricName("UiLatency")
                        .time(123L)
                        .dimensions(Map.of("region", "r1", "rack", "rack-1"))
                        .build();

        resource.emitMetrics(request, opcRequestId, principal, authorizationRequest);

        verify(emitMetricsService)
                .emitMetrics(
                        eq("UiLatency"), eq(123L), eq(Map.of("region", "r1", "rack", "rack-1")));
    }

    @Test
    void testEmitMetrics_missingMetricName_throws() {
        EmitMetricsRequest request = EmitMetricsRequest.builder().time(123L).build();

        RenderableException ex =
                assertThrows(
                        RenderableException.class,
                        () ->
                                resource.emitMetrics(
                                        request, opcRequestId, principal, authorizationRequest));

        assertTrue(ex.getMessage().contains("metricName"));
        verifyNoInteractions(emitMetricsService);
    }
}
