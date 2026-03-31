package com.oracle.pic.networking.lvv.service.service;

import com.google.inject.Singleton;
import com.oracle.pic.commons.metrics.MetricsScope;
import java.util.Map;

@Singleton
public class EmitMetricsService {
    private static final String UI_METRICS_SCOPE = "UIMetrics";

    public void emitMetrics(String metricName, Long time, Map<String, String> dimensions) {
        try (MetricsScope scope = MetricsScope.create(UI_METRICS_SCOPE)) {
            if (dimensions != null) {
                dimensions.forEach(
                        (dimensionName, dimensionValue) -> {
                            if (dimensionName != null && !dimensionName.isBlank()) {
                                scope.withDimension(dimensionName, dimensionValue);
                            }
                        });
            }

            scope.emit(metricName, time.doubleValue());
            scope.recordSuccess();
        }
    }
}
