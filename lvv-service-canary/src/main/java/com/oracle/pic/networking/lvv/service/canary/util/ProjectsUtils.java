package com.oracle.pic.networking.lvv.service.canary.util;

import com.oracle.pic.networking.lvv.service.ProjectClient;
import com.oracle.pic.networking.lvv.service.model.CreateProjectDetails;
import com.oracle.pic.networking.lvv.service.model.UpdateProjectDetails;
import com.oracle.pic.networking.lvv.service.requests.CreateProjectRequest;
import com.oracle.pic.networking.lvv.service.requests.DeleteProjectRequest;
import com.oracle.pic.networking.lvv.service.requests.GetProjectRequest;
import com.oracle.pic.networking.lvv.service.requests.ListProjectsRequest;
import com.oracle.pic.networking.lvv.service.requests.UpdateProjectRequest;
import com.oracle.pic.networking.lvv.service.responses.CreateProjectResponse;
import com.oracle.pic.networking.lvv.service.responses.DeleteProjectResponse;
import com.oracle.pic.networking.lvv.service.responses.GetProjectResponse;
import com.oracle.pic.networking.lvv.service.responses.ListProjectsResponse;
import com.oracle.pic.networking.lvv.service.responses.UpdateProjectResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProjectsUtils {
    private static final String TEST_DISPLAY_NAME = "projectTest";

    public ProjectsUtils(String endpoint) {
        log.debug("Endpoint: {}", endpoint);
    }

    public CreateProjectResponse createProject(
            ProjectClient client, String compartmentId, String name) {

        CreateProjectDetails createProjectDetails =
                CreateProjectDetails.builder()
                        .compartmentId(compartmentId)
                        .displayName(name)
                        .build();

        CreateProjectRequest createProjectRequest =
                CreateProjectRequest.builder().createProjectDetails(createProjectDetails).build();
        return client.createProject(createProjectRequest);
    }

    public GetProjectResponse getProject(
            ProjectClient client, String projectId, String opcRequestId) {
        GetProjectRequest getProjectRequest =
                GetProjectRequest.builder().projectId(projectId).opcRequestId(opcRequestId).build();
        return client.getProject(getProjectRequest);
    }

    public UpdateProjectResponse updateProject(
            ProjectClient client, String projectId, String name) {
        UpdateProjectDetails updateProjectDetails =
                UpdateProjectDetails.builder().displayName(name).build();

        UpdateProjectRequest updateProjectRequest =
                UpdateProjectRequest.builder()
                        .projectId(projectId)
                        .updateProjectDetails(updateProjectDetails)
                        .build();
        return client.updateProject(updateProjectRequest);
    }

    public ListProjectsResponse listProject(ProjectClient client, String compartmentId) {

        ListProjectsRequest listProjectsRequest =
                ListProjectsRequest.builder()
                        .compartmentId(compartmentId)
                        .displayName(TEST_DISPLAY_NAME)
                        .build();
        return client.listProjects(listProjectsRequest);
    }

    public DeleteProjectResponse deleteProject(
            ProjectClient client, String projectId, String opcRequestId) {

        DeleteProjectRequest deleteProjectRequest =
                DeleteProjectRequest.builder()
                        .projectId(projectId)
                        .opcRequestId(opcRequestId)
                        .build();
        return client.deleteProject(deleteProjectRequest);
    }
}
