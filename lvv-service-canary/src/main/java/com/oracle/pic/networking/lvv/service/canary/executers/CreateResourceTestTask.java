// package com.oracle.pic.networking.lvv.service.canary.executers;
//
// import com.oracle.bmc.model.BmcException;
// import com.oracle.pic.networking.lvv.service.ProjectClient;
// import com.oracle.pic.networking.lvv.service.canary.client.ExampleClientProvider;
// import com.oracle.pic.networking.lvv.service.canary.util.ProjectsUtils;
// import com.oracle.pic.networking.lvv.service.responses.CreateProjectResponse;
// import com.oracle.pic.telemetry.commons.metrics.Metrics;
// import lombok.extern.slf4j.Slf4j;
// import org.apache.commons.lang3.StringUtils;
//
// @Slf4j
// public class CreateResourceTestTask implements Runnable {
//    private static final String CREATE_PROJECT_CALL_METRIC_KEY =
//            "lvv-service-canary.createProjectCall";
//    private static final String TEST_DISPLAY_NAME = "projectTest";
//    private static final String INVALID_COMPARTMENT = "";
//
//    private final ExampleClientProvider exampleClientProvider;
//    private final String canaryTestCompartmentId;
//    private final ProjectsUtils projectsUtils;
//
//    public CreateResourceTestTask(
//            ExampleClientProvider provider, String canaryTestCompartmentId, String endpoint)
//            throws Exception {
//        this.exampleClientProvider = provider;
//        this.canaryTestCompartmentId = canaryTestCompartmentId;
//        this.projectsUtils = new ProjectsUtils(endpoint);
//    }
//
//    @Override
//    public void run() {
//        try {
//            executeCreateResourceTest();
//            log.info("Create project test succeeded.");
//        } catch (Exception e) {
//            log.error("Error occurred while executing create project test", e);
//        }
//    }
//
//    private void executeCreateResourceTest() {
//        ProjectClient client = exampleClientProvider.getClient();
//
//        // Create Project
//        CreateProjectResponse createProjectResponse =
//                projectsUtils.createProject(client, canaryTestCompartmentId, TEST_DISPLAY_NAME);
//        if (createProjectResponse != null
//                && StringUtils.isNotBlank(createProjectResponse.getOpcRequestId())
//                && createProjectResponse
//                        .getProject()
//                        .getCompartmentId()
//                        .equals(canaryTestCompartmentId)
//                && createProjectResponse.getProject().getDisplayName().equals(TEST_DISPLAY_NAME))
// {
//
//            log.info("CreateProject call succeeded.");
//        } else {
//            Metrics.emit(CREATE_PROJECT_CALL_METRIC_KEY, 0d);
//            log.error("CreateProject call failed.");
//            return;
//        }
//
//        // Create Project with invalid parameters
//        try {
//            // Expect to throw an error.
//            CreateProjectResponse response =
//                    projectsUtils.createProject(client, INVALID_COMPARTMENT, TEST_DISPLAY_NAME);
//            if (response != null) {
//                Metrics.emit(CREATE_PROJECT_CALL_METRIC_KEY, 0d);
//                log.error("CreateProject with invalid compartmentId test failed.");
//                return;
//            }
//        } catch (BmcException ex) {
//            if (ex.getStatusCode() != 400) {
//                Metrics.emit(CREATE_PROJECT_CALL_METRIC_KEY, 0d);
//                log.error(
//                        "CreateProject call with invalid compartmentId failed, get status code {},
// expected status code {}",
//                        ex.getStatusCode(),
//                        400,
//                        ex);
//                return;
//            }
//            Metrics.emit(CREATE_PROJECT_CALL_METRIC_KEY, 1d);
//            log.info("CreateProject call with invalid compartmentId succeeded.");
//        }
//        // Clean up
//        String projectId = createProjectResponse.getProject().getId();
//        projectsUtils.deleteProject(client, projectId, createProjectResponse.getOpcRequestId());
//    }
// }
