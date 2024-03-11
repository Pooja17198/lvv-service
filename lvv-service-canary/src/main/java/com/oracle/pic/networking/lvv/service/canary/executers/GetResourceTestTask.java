package com.oracle.pic.networking.lvv.service.canary.executers;

import com.oracle.bmc.model.BmcException;
import com.oracle.pic.networking.lvv.service.ProjectClient;
import com.oracle.pic.networking.lvv.service.canary.client.ExampleClientProvider;
import com.oracle.pic.networking.lvv.service.canary.util.ProjectsUtils;
import com.oracle.pic.networking.lvv.service.responses.CreateProjectResponse;
import com.oracle.pic.networking.lvv.service.responses.GetProjectResponse;
import com.oracle.pic.telemetry.commons.metrics.Metrics;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GetResourceTestTask implements Runnable {
    private static final String GET_PROJECT_CALL_METRIC_KEY = "lvv-service-canary.getProjectCall";
    private static final String INVALID_OPC_REQUEST_ID = "";
    private static final String TEST_DISPLAY_NAME = "projectTest";

    private final ExampleClientProvider exampleClientProvider;
    private final String canaryTestCompartmentId;
    private final ProjectsUtils projectsUtils;

    public GetResourceTestTask(
            ExampleClientProvider provider, String canaryTestCompartmentId, String endpoint)
            throws Exception {
        this.exampleClientProvider = provider;
        this.canaryTestCompartmentId = canaryTestCompartmentId;
        this.projectsUtils = new ProjectsUtils(endpoint);
    }

    @Override
    public void run() {
        try {
            executeGetProject();
            log.info("Get project test succeeded.");
        } catch (Exception e) {
            log.error("Error occurred while executing GetProject test", e);
        }
    }

    private void executeGetProject() {
        ProjectClient client = exampleClientProvider.getClient();

        CreateProjectResponse createProjectResponse =
                projectsUtils.createProject(client, canaryTestCompartmentId, TEST_DISPLAY_NAME);
        if (createProjectResponse == null) {
            log.error("Create Project call failed");
            return;
        }

        // Get Project
        String projectId = createProjectResponse.getProject().getId();
        GetProjectResponse getProjectResponse =
                projectsUtils.getProject(
                        client, projectId, createProjectResponse.getOpcRequestId());
        if (getProjectResponse != null
                && getProjectResponse.getProject().getId().equals(projectId)) {
            log.info("GetProject call succeeded.");
        } else {
            Metrics.emit(GET_PROJECT_CALL_METRIC_KEY, 0d);
            log.error("GetProject call failed.");
            return;
        }

        // Get Project with invalid parameters.
        try {
            GetProjectResponse response =
                    projectsUtils.getProject(client, projectId, INVALID_OPC_REQUEST_ID);
            if (response != null) {
                Metrics.emit(GET_PROJECT_CALL_METRIC_KEY, 0d);
                log.error("GetProject with invalid compartmentId test failed.");
                return;
            }
        } catch (BmcException ex) {
            if (ex.getStatusCode() != 400) {
                Metrics.emit(GET_PROJECT_CALL_METRIC_KEY, 0d);
                log.error(
                        "GetProject call with invalid opcRequestID failed, get status code {}, expected status code {}",
                        ex.getStatusCode(),
                        400,
                        ex);
                return;
            }
            Metrics.emit(GET_PROJECT_CALL_METRIC_KEY, 1d);
            log.info("GetProject call with invalid opcRequestID succeeded.");
        }

        // Clean up
        projectsUtils.deleteProject(client, projectId, createProjectResponse.getOpcRequestId());
    }
}
