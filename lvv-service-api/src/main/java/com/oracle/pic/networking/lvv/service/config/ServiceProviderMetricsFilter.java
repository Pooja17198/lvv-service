package com.oracle.pic.networking.lvv.service.config;

import com.google.common.collect.ImmutableMap;
import com.oracle.pic.telemetry.commons.metrics.Metrics;
import com.oracle.pic.telemetry.commons.metrics.model.MetricName;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.ext.Provider;
import lombok.Generated;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
public class ServiceProviderMetricsFilter implements ClientRequestFilter, ClientResponseFilter {
    @Generated
    private static final Logger log = LoggerFactory.getLogger(ServiceProviderMetricsFilter.class);

    private final String serviceProvider;
    private static final ImmutableMap<String, String> API_METHODS =
            ImmutableMap.<String, String>builder()
                    .put("get", "List")
                    .put("get.id", "Get")
                    .put("post", "Create")
                    .put("post.id", "InstanceAction")
                    .put("put.id", "Update")
                    .put("delete.id", "Terminate")
                    .build();

    public ServiceProviderMetricsFilter(String serviceProvider) {
        log.debug("Create {} ServiceProviderMetricsFilter.", serviceProvider);
        this.serviceProvider = serviceProvider;
    }

    public void filter(ClientRequestContext requestContext) throws IOException {
        log.debug("Entering ServiceProviderMetricsFilter in {} request.", this.serviceProvider);
        requestContext.setProperty("startTime", new Timestamp(System.currentTimeMillis()));
    }

    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext)
            throws IOException {
        log.debug("Entering ServiceProviderMetricsFilter in {} response.", this.serviceProvider);
        MetricName baseName = Metrics.name("Dependencies");
        Object startTimeObject = requestContext.getProperty("startTime");
        if (!(startTimeObject instanceof Timestamp)) {
            log.debug(
                    "Missing or invalid startTime for {} response filter; skipping latency metrics.",
                    this.serviceProvider);
            return;
        }
        Timestamp startTime = (Timestamp) startTimeObject;
        Instant startAt = Instant.ofEpochMilli(startTime.getTime());
        double durationInMs = (double) startAt.until(OffsetDateTime.now(), ChronoUnit.MILLIS);
        String apiMethod = this.getApiMethod(requestContext);
        Metrics.emit(baseName.child("ActualTime"), durationInMs);
        if (durationInMs > 1000) {
            log.debug(
                    "Found slow API response: Duration of {} ms for API call {} with response header {}.",
                    durationInMs,
                    requestContext.getUri(),
                    responseContext.getHeaders());
        }
        Metrics.emit(baseName.child(String.format("%s.Time", this.serviceProvider)), durationInMs);
        Metrics.emit(
                baseName.child(String.format("%s.%s.Time", this.serviceProvider, apiMethod)),
                durationInMs);
        int retCode = responseContext.getStatus();
        emitStatusMetrics(baseName, "", retCode);
        emitStatusMetrics(baseName, String.format("%s.", this.serviceProvider), retCode);
        emitStatusMetrics(
                baseName, String.format("%s.%s.", this.serviceProvider, apiMethod), retCode);
    }

    private static void emitStatusMetrics(MetricName baseName, String prefix, int statusCode) {
        double success = statusCode >= 500 && statusCode < 600 ? (double) 0.0F : (double) 1.0F;
        Metrics.emit(baseName.child(prefix + "Success"), success);
        if (success == (double) 1.0F && "".equals(prefix)) {
            Metrics.emit(baseName, (double) 1.0F);
        }

        Metrics.emit(baseName.child(prefix + "Status.2xx"), statusCodeToMetric(statusCode, 200));
        Metrics.emit(baseName.child(prefix + "Status.3xx"), statusCodeToMetric(statusCode, 300));
        Metrics.emit(baseName.child(prefix + "Status.4xx"), statusCodeToMetric(statusCode, 400));
        Metrics.emit(baseName.child(prefix + "Status.5xx"), statusCodeToMetric(statusCode, 500));
    }

    private static double statusCodeToMetric(int statusCode, int baseStatusCode) {
        return statusCode >= baseStatusCode && statusCode < baseStatusCode + 100
                ? (double) 1.0F
                : (double) 0.0F;
    }

    private String getApiMethod(ClientRequestContext requestContext) {
        String method = StringUtils.lowerCase(requestContext.getMethod());
        String uriPath = requestContext.getUri().getPath().toLowerCase();
        boolean hasOcid = uriPath.contains("ocid");
        String key = hasOcid ? method.concat(".id") : method;
        String value = API_METHODS.get(key);
        return value == null ? "Unknown" : value;
    }
}
