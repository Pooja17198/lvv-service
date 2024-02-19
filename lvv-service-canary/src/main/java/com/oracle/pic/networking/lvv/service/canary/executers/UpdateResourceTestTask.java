package com.oracle.pic.networking.lvv.service.canary.executers;

import com.oracle.bmc.model.BmcException;
import com.oracle.pic.networking.lvv.service.ProjectClient;
import com.oracle.pic.networking.lvv.service.canary.client.ExampleClientProvider;
import com.oracle.pic.networking.lvv.service.canary.util.ProjectsUtils;
import com.oracle.pic.networking.lvv.service.responses.CreateProjectResponse;
import com.oracle.pic.networking.lvv.service.responses.UpdateProjectResponse;
import com.oracle.pic.telemetry.commons.metrics.Metrics;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class UpdateResourceTestTask implements Runnable {
    private static final String UPDATE_PROJECT_CALL_METRIC_KEY =
            "lvv-service-canary.updateProjectCall";
    private static final String TEST_DISPLAY_NAME_1 = "projectTest";
    private static final String TEST_DISPLAY_NAME_2 = "projectTest2";
    private static final String INVALID_NAME = "";

    private final ExampleClientProvider exampleClientProvider;
    private final String canaryTestCompartmentId;
    private final ProjectsUtils projectsUtils;

    public UpdateResourceTestTask(
            ExampleClientProvider provider, String canaryTestCompartmentId, String endpoint)
            throws Exception {
        this.exampleClientProvider = provider;
        this.canaryTestCompartmentId = canaryTestCompartmentId;
        this.projectsUtils = new ProjectsUtils(endpoint);
    }

    @Override
    public void run() {
        try {
            executeUpdateProject();
            log.info("Update project test succeeded.");
        } catch (Exception e) {
            log.error("Error occurred while executing UpdateProject test", e);
        }
    }

    private void executeUpdateProject() {
        ProjectClient client = exampleClientProvider.getClient();

        CreateProjectResponse createProjectResponse =
                projectsUtils.createProject(
                        client, canaryTestCompartmentId, TEST_DISPLAY_NAME_1);
        if (createProjectResponse == null) {
            log.error("Create Project call failed");
            return;
        }

        // Update Project
        String projectId = createProjectResponse.getProject().getId();
        UpdateProjectResponse updateProjectResponse =
                projectsUtils.updateProject(client, projectId, TEST_DISPLAY_NAME_2);
        if (updateProjectResponse != null
                && StringUtils.isNotBlank(updateProjectResponse.getOpcRequestId())
                && updateProjectResponse.getProject().getId().equals(projectId)
                && updateProjectResponse
                        .getProject()
                        .getDisplayName()
                        .equals(TEST_DISPLAY_NAME_2)) {
            log.info("UpdateProject call succeeded.");
        } else {
            Metrics.emit(UPDATE_PROJECT_CALL_METRIC_KEY, 0d);
            log.info("UpdateProject call failed.");
            return;
        }
        // update Project with invalid parameters.
        try {
            UpdateProjectResponse response =
                    projectsUtils.updateProject(client, projectId, INVALID_NAME);
            if (response != null) {
                Metrics.emit(UPDATE_PROJECT_CALL_METRIC_KEY, 0d);
                log.error("UpdateProject with invalid compartmentId test failed.");
                return;
            }
        } catch (BmcException ex) {
            if (ex.getStatusCode() != 400) {
                Metrics.emit(UPDATE_PROJECT_CALL_METRIC_KEY, 0d);
                log.error(
                        "UpdateProject call with invalid displayName failed, get status code {}, expected status code {}",
                        ex.getStatusCode(),
                        400,
                        ex);
                return;
            }
            Metrics.emit(UPDATE_PROJECT_CALL_METRIC_KEY, 1d);
            log.info("UpdateProject call with invalid displayName succeeded.");
        }

        // Clean up
        projectsUtils.deleteProject(
                client, projectId, createProjectResponse.getOpcRequestId());
    }
}
