package com.oracle.pic.networking.lvv.service.config;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@Value
@JsonDeserialize(builder = StoreKeeperClientConfig.Builder.class)
@Builder(builderClassName = "Builder", toBuilder = true)
public class StoreKeeperClientConfig {

    /** NCP service endpoint */
    @NotNull String endpoint;

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {}
}
