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
@JsonDeserialize(builder = NcpServiceConfiguration.Builder.class)
@Builder(builderClassName = "Builder", toBuilder = true)
public class NcpServiceConfiguration {

    /** NCP service endpoint */
    @NotNull private String endpoint;

    /** NCP Client connect timeout in msec */
    @NotNull private int connectTimeoutInMs;

    /** NCP Client read timeout in msec */
    @NotNull private int readTimeoutInMs;

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {}
}
