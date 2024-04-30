package com.oracle.pic.networking.lvv.service.resources;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import com.oracle.pic.identity.authentication.Principal;
import com.oracle.pic.identity.authorization.sdk.AuthorizationRequest;
import com.oracle.pic.networking.lvv.service.auth.AuthHelper;
import com.oracle.pic.networking.lvv.service.kiev.KievManager;
import com.oracle.pic.networking.lvv.service.kiev.ProjectItem;
import com.oracle.pic.networking.lvv.service.model.Project;
import com.oracle.pic.networking.lvv.service.model.PutProjectRequest;
import com.oracle.pic.networking.lvv.service.service.ProjectService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ProjectResourceTest {
    private final String compartmentId =
            "ocid1.compartment.oc1..aaaaaaaa26mceal7cypzsefhbm2l73xtb3yreplacemereplacemereplaceme";
    private final String displayName = "projectTest";
    private final String projectId = "projectId";
    private final String ifMatch = "projectId";
    private ProjectResource resource;
    private ProjectService projectService;
    private String opcRequestId = "opcRequestId";

    @Mock private AuthorizationRequest authorizationRequest;
    @Mock private AuthHelper mockAuthorizationHelper;
    @Mock private Principal mockPrincipal;

    @Mock private AuthorizationRequest mockAuthorizationRequest;
    @Mock private KievManager mockKievManager;

    @BeforeEach
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        doNothing()
                .when(mockAuthorizationHelper)
                .authorize(any(AuthorizationRequest.class), anyString());
        this.projectService = new ProjectService(mockKievManager);
        this.resource = new ProjectResource(projectService);
        ProjectItem projectItem1 =
                ProjectItem.builder()
                        .projectId("project1")
                        .vendorName("vendor1")
                        .type("cabling")
                        .block("block1")
                        .building("building1")
                        .build();
        ProjectItem projectItem2 =
                ProjectItem.builder()
                        .projectId("project2")
                        .vendorName("vendor1")
                        .type("cabling")
                        .block("block2")
                        .building("building1")
                        .build();

        List<ProjectItem> mockList = new ArrayList<>();
        mockList.add(projectItem1);
        mockList.add(projectItem2);

        when(projectService.createUpdateProject(
                        "project1", "vendor1", "building1", "block1", "cabling"))
                .thenReturn(projectItem1);
        when(projectService.getProject("project1")).thenReturn(projectItem1);
        when(mockKievManager.getAllProjectItem(any(), eq("vendor1"))).thenReturn(mockList);
    }

    @Test
    public void createProjectTest() {
        Project project = new Project("vendor1", "building1", "block1", "cabling");
        Project result =
                resource.createProject(
                        "project1",
                        PutProjectRequest.builder()
                                .project(
                                        Project.builder()
                                                .vendorName("vendor1")
                                                .type("cabling")
                                                .block("block1")
                                                .building("building1")
                                                .build())
                                .build(),
                        this.opcRequestId,
                        this.mockPrincipal,
                        this.mockAuthorizationRequest);
        assertEquals(project, result);
    }

    @Test
    public void listProjectTest() {
        List<String> result =
                resource.getProjectList(
                        "vendor1",
                        this.opcRequestId,
                        this.mockPrincipal,
                        this.mockAuthorizationRequest);
        assertEquals(result.size(), 2);
    }

    @Test
    public void getProject() {
        Project project =
                resource.createProject(
                        "project1",
                        PutProjectRequest.builder()
                                .project(
                                        Project.builder()
                                                .vendorName("vendor1")
                                                .type("cabling")
                                                .block("block1")
                                                .building("building1")
                                                .build())
                                .build(),
                        this.opcRequestId,
                        this.mockPrincipal,
                        this.mockAuthorizationRequest);
        Project result =
                resource.getProject(
                        "project1",
                        this.opcRequestId,
                        this.mockPrincipal,
                        this.mockAuthorizationRequest);
        assertEquals(project, result);
    }
}
