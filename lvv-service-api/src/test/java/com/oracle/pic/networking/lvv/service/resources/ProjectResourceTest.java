package com.oracle.pic.networking.lvv.service.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.oracle.pic.commons.service.model.PaginatedCollectionResponse;
import com.oracle.pic.commons.service.model.TaggedResponse;
import com.oracle.pic.networking.lvv.service.auth.AuthHelper;
import com.oracle.pic.networking.lvv.service.model.CreateProjectDetails;
import com.oracle.pic.networking.lvv.service.model.Project;
import com.oracle.pic.networking.lvv.service.model.ProjectCollection;
import com.oracle.pic.networking.lvv.service.model.ProjectSummary;
import com.oracle.pic.networking.lvv.service.model.LifecycleState;
import com.oracle.pic.networking.lvv.service.model.SortOrders;
import com.oracle.pic.networking.lvv.service.model.UpdateProjectDetails;
import com.oracle.pic.networking.lvv.service.service.ProjectService;
import com.oracle.pic.identity.authentication.Principal;
import com.oracle.pic.identity.authorization.sdk.AuthorizationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class ProjectResourceTest {
    private final String compartmentId =
            "ocid1.compartment.oc1..aaaaaaaa26mceal7cypzsefhbm2l73xtb3yreplacemereplacemereplaceme";
    private final String displayName = "projectTest";
    private final String projectId = "projectId";
    private final String ifMatch = "projectId";
    private ProjectResource resource;

    @Mock LifecycleState lifecycleState;

    @Mock private AuthHelper mockAuthorizationHelper;

    @Mock private Principal mockPrincipal;

    @Mock private AuthorizationRequest mockAuthorizationRequest;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.initMocks(this);
        resource = new ProjectResource(mockAuthorizationHelper, new ProjectService());
        doNothing()
                .when(mockAuthorizationHelper)
                .authorize(any(AuthorizationRequest.class), anyString());
    }

    @Test
    public void createProjectTest() {
        CreateProjectDetails createProjectDetails =
                CreateProjectDetails.builder()
                        .compartmentId(compartmentId)
                        .displayName(displayName)
                        .build();
        TaggedResponse<Project> project =
                resource.createProject(
                        createProjectDetails,
                        "retryToken",
                        "requestId",
                        mockPrincipal,
                        mockAuthorizationRequest);
        assertEquals(project.getResult().getCompartmentId(), compartmentId);
        assertEquals(project.getResult().getDisplayName(), displayName);
    }

    @Test
    public void listProjectTest() {

        final PaginatedCollectionResponse<ProjectCollection> projectQueryResults =
                resource.listProjects(
                        compartmentId,
                        displayName,
                        10,
                        "1",
                        lifecycleState,
                        SortOrders.Desc,
                        "time",
                        "requestId",
                        mockPrincipal,
                        mockAuthorizationRequest);
        for (ProjectSummary summary : projectQueryResults.getCollection().getItems()) {
            assertEquals(summary.getCompartmentId(), compartmentId);
            assertEquals(summary.getDisplayName(), displayName);
        }
    }

    @Test
    public void getProject() {
        TaggedResponse<Project> project =
                resource.getProject(
                        projectId, "requestId", mockPrincipal, mockAuthorizationRequest);
        assertEquals(project.getResult().getId(), projectId);
    }

    @Test
    public void updateProject() {
        UpdateProjectDetails projectDetails =
                UpdateProjectDetails.builder().displayName(displayName).build();

        TaggedResponse<Project> project =
                resource.updateProject(
                        projectId,
                        projectDetails,
                        null,
                        "requestId",
                        mockPrincipal,
                        mockAuthorizationRequest);
        assertEquals(project.getResult().getId(), projectId);
        assertEquals(projectDetails.getDisplayName(), displayName);
    }

    @Test
    public void deleteProject() {
        resource.deleteProject(
                projectId, null, "requestId", mockPrincipal, mockAuthorizationRequest);
        verify(mockAuthorizationHelper, times(1)).authorize(any(), Mockito.anyString());
    }
}
