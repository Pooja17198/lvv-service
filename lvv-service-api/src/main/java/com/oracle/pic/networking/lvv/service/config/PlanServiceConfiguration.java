package com.oracle.pic.networking.lvv.service.config;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

/**
 * Configuration used by the AdSynchronizer to push configuration updates to the devices in an ad.
 */
@Value
@JsonDeserialize(builder = PlanServiceConfiguration.Builder.class)
@Builder(builderClassName = "Builder", toBuilder = true)
public class PlanServiceConfiguration {

    /** PlanService endpoint */
    @NotNull private String endpoint;

    /** PlanService Client connect timeout in msec */
    @NotNull private int connectTimeoutInMs;

    /** PlanService Client read timeout in msec */
    @NotNull private int readTimeoutInMs;

    /** PlanService maxRetries */
    @NotNull private int maxRetries;

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {}
}
