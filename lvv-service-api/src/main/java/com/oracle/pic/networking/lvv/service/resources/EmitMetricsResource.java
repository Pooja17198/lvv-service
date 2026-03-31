package com.oracle.pic.networking.lvv.service.resources;

import com.google.inject.Inject;
import com.oracle.pic.commons.exceptions.server.ErrorCode;
import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.identity.authentication.Principal;
import com.oracle.pic.identity.authorization.sdk.AuthorizationRequest;
import com.oracle.pic.networking.lvv.service.api.AbstractMetricsResource;
import com.oracle.pic.networking.lvv.service.model.EmitMetricsRequest;
import com.oracle.pic.networking.lvv.service.service.EmitMetricsService;
import java.util.ArrayList;
import java.util.List;
import lombok.ToString;

@ToString
public class EmitMetricsResource extends AbstractMetricsResource {
    private final EmitMetricsService emitMetricsService;

    @Inject
    protected EmitMetricsResource(EmitMetricsService emitMetricsService) {
        this.emitMetricsService = emitMetricsService;
    }

    @Override
    public void emitMetrics(
            EmitMetricsRequest value,
            String opcRequestId,
            Principal principal,
            AuthorizationRequest authorizationRequest) {
        if (value == null) {
            throw new RenderableException(
                    ErrorCode.MissingParameter, "Metric request body cannot be empty");
        }

        List<String> missing = new ArrayList<>();
        if (value.getMetricName() == null || value.getMetricName().isBlank()) {
            missing.add("metricName");
        }
        if (value.getTime() == null) {
            missing.add("time");
        }

        if (!missing.isEmpty()) {
            throw new RenderableException(
                    ErrorCode.MissingParameter,
                    "Missing or empty parameters: " + String.join(", ", missing));
        }

        emitMetricsService.emitMetrics(
                value.getMetricName(), value.getTime(), value.getDimensions());
    }
}
