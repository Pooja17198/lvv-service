package com.oracle.pic.networking.lvv.service.dependencies.jira;

public class JiraQueries {

    public static final String JQL_PROJECT = "project = \"DO\"";
    public static final String JQL_OPEN_STATUS =
            " AND status in (Open, \"In Progress\", Reopened, Pending, \"Pending Engineering\")";
    public static final String JQL_BUILDING_BLOCK = " AND Building = %s AND Block ~ %s";
    public static final String JQL = JQL_PROJECT + JQL_OPEN_STATUS + JQL_BUILDING_BLOCK;
    public static final String JQL_CLOSED =
            "project = \"DO\" AND status in (Closed, Resolved) AND Building = %s AND Block ~ %s";
    public static final String FINAL_VALIDATION = " AND summary ~ FinalRackValidation";
    public static final String GPU_VALIDATION = " AND summary ~ \"NA Cable Validation Failure\"";
    public static final String RACK_DEPLOYMENT = " AND summary ~ \"Rack Deployment\"";
    public static final String SERIAL_NUMBER = " AND \"Serial Number\" ~ %s";
}
