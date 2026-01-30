package com.oracle.pic.networking.lvv.service.dependencies.jira;

import lombok.Getter;

@Getter
public enum JiraTicketCategory {
    FINAL_RACK_VALIDATION("Final Rack Validation", JiraQueries.FINAL_VALIDATION),
    GPU_CABLE_VALIDATION("GPU Cable Validation", JiraQueries.GPU_VALIDATION),
    RACK_DEPLOYMENT("Rack Deployment", JiraQueries.RACK_DEPLOYMENT);

    private final String ticketType;
    private final String jqlSuffix;

    JiraTicketCategory(String ticketType, String jqlSuffix) {
        this.ticketType = ticketType;
        this.jqlSuffix = jqlSuffix;
    }
}
