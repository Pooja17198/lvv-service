package com.oracle.pic.networking.lvv.service.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.oracle.pic.commons.metrics.MetricsScope;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class EmitMetricsServiceTest {

    private final EmitMetricsService service = new EmitMetricsService();

    @Test
    void emitMetrics_createsScope_addsDimensions_emitsMetric_andRecordsSuccess() {
        MetricsScope metricsScope = mock(MetricsScope.class);
        Map<String, String> dimensions = new LinkedHashMap<>();
        dimensions.put("region", "r1");
        dimensions.put("rack", "rack-1");

        try (MockedStatic<MetricsScope> metricsScopeMocked =
                Mockito.mockStatic(MetricsScope.class)) {
            metricsScopeMocked
                    .when(() -> MetricsScope.create("UIMetrics"))
                    .thenReturn(metricsScope);
            doReturn(metricsScope).when(metricsScope).withDimension(anyString(), any());
            doReturn(metricsScope).when(metricsScope).emit(anyString(), anyDouble());
            doReturn(metricsScope).when(metricsScope).recordSuccess();

            service.emitMetrics("UiLatency", 123L, dimensions);

            verify(metricsScope).withDimension("region", "r1");
            verify(metricsScope).withDimension("rack", "rack-1");
            verify(metricsScope).emit("UiLatency", 123.0);
            verify(metricsScope).recordSuccess();
        }
    }
}
