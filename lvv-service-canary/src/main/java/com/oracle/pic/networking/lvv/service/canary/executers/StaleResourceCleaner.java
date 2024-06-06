// package com.oracle.pic.networking.lvv.service.canary.executers;
//
// import com.oracle.pic.networking.lvv.service.ProjectClient;
// import com.oracle.pic.networking.lvv.service.canary.client.ExampleClientProvider;
// import com.oracle.pic.networking.lvv.service.canary.util.ProjectsUtils;
// import com.oracle.pic.networking.lvv.service.model.ProjectSummary;
// import com.oracle.pic.networking.lvv.service.responses.ListProjectsResponse;
// import java.util.Date;
// import java.util.List;
// import lombok.extern.slf4j.Slf4j;
// import org.apache.commons.lang3.time.DateUtils;
//
// @Slf4j
// public class StaleResourceCleaner implements Runnable {
//    private final ExampleClientProvider exampleClientProvider;
//    private final String canaryTestCompartmentId;
//    private final ProjectsUtils projectsUtils;
//
//    private static final int ELIGIBLE_AGE_IN_HOURS = 1; // The eligible age of project, 1h.
//
//    public StaleResourceCleaner(
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
//            cleanupStaledResources();
//            log.info("Cleaned all staled resources.");
//        } catch (Exception e) {
//            log.error("Error occurred while executing clean stale resource", e);
//        }
//    }
//
//    private void cleanupStaledResources() {
//        ProjectClient client = exampleClientProvider.getClient();
//
//        ListProjectsResponse listProjectsResponse =
//                projectsUtils.listProject(client, canaryTestCompartmentId);
//        List<ProjectSummary> projectSummaries =
//                listProjectsResponse.getProjectCollection().getItems();
//
//        for (ProjectSummary summary : projectSummaries) {
//            Date creationDate = summary.getTimeCreated();
//            if (DateUtils.addHours(creationDate, ELIGIBLE_AGE_IN_HOURS).before(new Date())) {
//                try {
//                    projectsUtils.deleteProject(client, summary.getId(), null);
//                } catch (final Exception ex) {
//                    log.error("Failed to delete the resource.", ex);
//                }
//            }
//        }
//    }
// }
