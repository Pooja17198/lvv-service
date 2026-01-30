package com.oracle.pic.networking.lvv.service.dependencies.jira;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder(builderClassName = "Builder")
@ToString
public class JiraTicket {
    private String ticketId;
    private String ticketCategory;
    private boolean resolveEnabled;
    private String resolveDisabledReason;
}
