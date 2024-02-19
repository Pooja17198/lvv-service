package com.oracle.pic.networking.lvv.service.canary.executers;

import com.oracle.bmc.model.BmcException;
import com.oracle.pic.networking.lvv.service.ProjectClient;
import com.oracle.pic.networking.lvv.service.canary.client.ExampleClientProvider;
import com.oracle.pic.networking.lvv.service.canary.util.ProjectsUtils;
import com.oracle.pic.networking.lvv.service.responses.CreateProjectResponse;
import com.oracle.pic.networking.lvv.service.responses.DeleteProjectResponse;
import com.oracle.pic.telemetry.commons.metrics.Metrics;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class DeleteResourceTestTask implements Runnable {
    private static final String DELETE_PROJECT_CALL_METRIC_KEY =
            "lvv-service-canary.deleteProjectCall";
    private static final String INVALID_OPC_REQUEST_ID = "";
    private static final String TEST_DISPLAY_NAME = "projectTest";

    private final ExampleClientProvider exampleClientProvider;
    private final String canaryTestCompartmentId;
    private final ProjectsUtils projectsUtils;

    public DeleteResourceTestTask(
            ExampleClientProvider provider, String canaryTestCompartmentId, String endpoint)
            throws Exception {
        this.exampleClientProvider = provider;
        this.canaryTestCompartmentId = canaryTestCompartmentId;
        this.projectsUtils = new ProjectsUtils(endpoint);
    }

    @Override
    public void run() {
        try {
            executeDeleteResourceTest();
            log.info("Delete project test succeeded.");
        } catch (Exception e) {
            log.error("Error occurred while executing delete project test", e);
        }
    }

    private void executeDeleteResourceTest() {
        ProjectClient client = exampleClientProvider.getClient();

        CreateProjectResponse createProjectResponse =
                projectsUtils.createProject(
                        client, canaryTestCompartmentId, TEST_DISPLAY_NAME);
        if (createProjectResponse == null) {
            log.error("Create Project call failed");
            return;
        }

        // Delete Project
        String projectId = createProjectResponse.getProject().getId();
        DeleteProjectResponse deleteProjectResponse =
                projectsUtils.deleteProject(
                        client, projectId, createProjectResponse.getOpcRequestId());
        if (deleteProjectResponse != null
                && StringUtils.isNotBlank(deleteProjectResponse.getOpcRequestId())) {
            log.info("DeleteProject call succeeded.");
        } else {
            Metrics.emit(DELETE_PROJECT_CALL_METRIC_KEY, 0d);
            log.info("DeleteProject call failed.");
            return;
        }

        // delete Project with invalid parameters.
        try {
            DeleteProjectResponse response =
                    projectsUtils.deleteProject(client, projectId, INVALID_OPC_REQUEST_ID);
            if (response != null) {
                Metrics.emit(DELETE_PROJECT_CALL_METRIC_KEY, 0d);
                log.error("DeleteProject with invalid compartmentId test failed.");
                return;
            }
        } catch (BmcException ex) {
            if (ex.getStatusCode() != 400) {
                Metrics.emit(DELETE_PROJECT_CALL_METRIC_KEY, 0d);
                log.error(
                        "DeleteProject call with invalid opcRequestId failed, get status code {}, expected status code {}",
                        ex.getStatusCode(),
                        400,
                        ex);
                return;
            }
            Metrics.emit(DELETE_PROJECT_CALL_METRIC_KEY, 1d);
            log.info("DeleteProject call with invalid opcRequestId succeeded.");
        }
    }
}
