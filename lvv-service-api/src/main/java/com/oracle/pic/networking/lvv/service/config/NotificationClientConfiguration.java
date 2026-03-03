package com.oracle.pic.networking.lvv.service.config;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@Value
@JsonDeserialize(builder = NotificationClientConfiguration.Builder.class)
@Builder(builderClassName = "Builder", toBuilder = true)
public class NotificationClientConfiguration {
    @NotNull private String compartmentOcid;

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {}
}
