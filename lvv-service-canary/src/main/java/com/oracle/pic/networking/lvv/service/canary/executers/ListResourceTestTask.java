package com.oracle.pic.networking.lvv.service.canary.executers;

import com.oracle.bmc.model.BmcException;
import com.oracle.pic.networking.lvv.service.ProjectClient;
import com.oracle.pic.networking.lvv.service.canary.client.ExampleClientProvider;
import com.oracle.pic.networking.lvv.service.canary.util.ProjectsUtils;
import com.oracle.pic.networking.lvv.service.model.ProjectSummary;
import com.oracle.pic.networking.lvv.service.responses.CreateProjectResponse;
import com.oracle.pic.networking.lvv.service.responses.ListProjectsResponse;
import com.oracle.pic.telemetry.commons.metrics.Metrics;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ListResourceTestTask implements Runnable {
    private static final String LIST_PROJECT_CALL_METRIC_KEY =
            "lvv-service-canary.listProjectCall";
    private static final String TEST_DISPLAY_NAME = "projectTest";
    private static final String INVALID_COMPARTMENT = "";

    private final ExampleClientProvider exampleClientProvider;
    private final String canaryTestCompartmentId;
    private final ProjectsUtils projectsUtils;

    public ListResourceTestTask(
            ExampleClientProvider provider, String canaryTestCompartmentId, String endpoint)
            throws Exception {
        this.exampleClientProvider = provider;
        this.canaryTestCompartmentId = canaryTestCompartmentId;
        this.projectsUtils = new ProjectsUtils(endpoint);
    }

    @Override
    public void run() {
        try {
            executeListProject();
            log.info("List project test succeeded.");
        } catch (Exception e) {
            log.error("Error occurred while executing ListProject test", e);
        }
    }

    private void executeListProject() {
        ProjectClient client = exampleClientProvider.getClient();

        CreateProjectResponse createProjectResponse =
                projectsUtils.createProject(
                        client, canaryTestCompartmentId, TEST_DISPLAY_NAME);
        if (createProjectResponse == null) {
            log.error("Create Project call failed");
            return;
        }

        // List Project
        ListProjectsResponse listProjectsResponse =
                projectsUtils.listProject(client, canaryTestCompartmentId);
        List<ProjectSummary> projectSummaries =
                listProjectsResponse.getProjectCollection().getItems();
        boolean listProjectSuccess = true;
        for (ProjectSummary summary : projectSummaries) {
            if (!summary.getCompartmentId().equals(canaryTestCompartmentId)
                    || !summary.getDisplayName().equals(TEST_DISPLAY_NAME)) {
                listProjectSuccess = false;
                break;
            }
        }
        log.info("ListProject call {}", listProjectSuccess ? "succeeded." : "failed.");
        if (!listProjectSuccess) {
            Metrics.emit(LIST_PROJECT_CALL_METRIC_KEY, 0d);
            return;
        }

        // list Project with invalid parameters.
        try {
            ListProjectsResponse response =
                    projectsUtils.listProject(client, INVALID_COMPARTMENT);
            if (response != null) {
                Metrics.emit(LIST_PROJECT_CALL_METRIC_KEY, 0d);
                log.error("ListProject with invalid compartmentId test failed.");
                return;
            }
        } catch (BmcException ex) {
            if (ex.getStatusCode() != 400) {
                Metrics.emit(LIST_PROJECT_CALL_METRIC_KEY, 0d);
                log.error(
                        "ListProject call with invalid compartmentId failed, get status code {}, expected status code {}",
                        ex.getStatusCode(),
                        400,
                        ex);
                return;
            }
            Metrics.emit(LIST_PROJECT_CALL_METRIC_KEY, 1d);
            log.info("ListProject call with invalid compartmentId succeeded.");
        }

        // Clean up
        String projectId = createProjectResponse.getProject().getId();
        projectsUtils.deleteProject(
                client, projectId, createProjectResponse.getOpcRequestId());
    }
}
