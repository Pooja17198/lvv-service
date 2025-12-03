package com.oracle.pic.networking.lvv.service.resources;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.oracle.pic.commons.exceptions.server.ErrorCode;
import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.identity.authentication.Principal;
import com.oracle.pic.identity.authorization.sdk.AuthorizationRequest;
import com.oracle.pic.networking.lvv.service.model.Project;
import com.oracle.pic.networking.lvv.service.model.PutProjectRequest;
import com.oracle.pic.networking.lvv.service.service.ProjectService;
import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class ProjectResourceTest {

    @Mock ProjectService projectService;
    @Mock HttpServletResponse httpServletResponse;
    @Mock MetricsScope metricsScope;
    @Mock Principal principal;
    @Mock AuthorizationRequest authorizationRequest;

    ProjectResource resource;

    final String projectId = "pid-123";
    final String vendorName = "vname";
    final String building = "bldg";
    final List<String> blocks = List.of("B1", "B2");
    final String region = "us-phoenix-1";
    final String type = "TYPE";
    final String opcRequestId = "req-1";

    Project project;

    @BeforeEach
    void setup() {
        resource = new ProjectResource(projectService);
        // Sample project
        project =
                Project.builder()
                        .projectId(projectId)
                        .vendorName(vendorName)
                        .building(building)
                        .region(region)
                        .blocks(blocks)
                        .build();
    }

    @Test
    void testCreateProject_success() {
        PutProjectRequest req = mock(PutProjectRequest.class);
        when(req.getProject()).thenReturn(project);

        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);

            doNothing()
                    .when(projectService)
                    .createProject(any(), any(), any(), any(), any(), any());
            when(metricsScope.withDimension(anyString(), anyString())).thenReturn(metricsScope);
            when(metricsScope.recordSuccess()).thenReturn(metricsScope);

            Boolean result =
                    resource.createProject(req, opcRequestId, principal, authorizationRequest);

            assertTrue(result);
            verify(projectService)
                    .createProject(
                            eq(projectId),
                            eq(vendorName),
                            eq(region),
                            eq(building),
                            eq(blocks),
                            eq(metricsScope));

            verify(metricsScope).recordSuccess();
        }
    }

    @Test
    void testUpdateProject_success() {
        PutProjectRequest req = mock(PutProjectRequest.class);
        when(req.getProject()).thenReturn(project);

        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);

            doNothing()
                    .when(projectService)
                    .updateProject(any(), any(), any(), any(), any(), any());
            when(metricsScope.withDimension(anyString(), anyString())).thenReturn(metricsScope);
            when(metricsScope.recordSuccess()).thenReturn(metricsScope);

            resource.updateProject(req, opcRequestId, principal, authorizationRequest);

            verify(projectService)
                    .updateProject(
                            eq(projectId),
                            eq(vendorName),
                            eq(region),
                            eq(building),
                            eq(blocks),
                            eq(metricsScope));

            verify(metricsScope).recordSuccess();
        }
    }

    @Test
    void testCreateProject_withNoBlocks_returnsFalse() {
        Project emptyBlocksProject =
                Project.builder()
                        .projectId(projectId)
                        .vendorName(vendorName)
                        .region(region)
                        .building(building)
                        .blocks(List.of())
                        .build();
        PutProjectRequest req = mock(PutProjectRequest.class);
        when(req.getProject()).thenReturn(emptyBlocksProject);

        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            when(metricsScope.withDimension(anyString(), anyString())).thenReturn(metricsScope);
            when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);
            Boolean result =
                    resource.createProject(req, opcRequestId, principal, authorizationRequest);
            assertFalse(result);
            verify(metricsScope, never()).recordSuccess();
            verify(projectService, never()).createProject(any(), any(), any(), any(), any(), any());
        }
    }

    @Test
    void testUpdateProject_withNoBlocks_returnsFalse() {
        Project emptyBlocksProject =
                Project.builder()
                        .projectId(projectId)
                        .vendorName(vendorName)
                        .region(region)
                        .building(building)
                        .blocks(List.of())
                        .build();
        PutProjectRequest req = mock(PutProjectRequest.class);
        when(req.getProject()).thenReturn(emptyBlocksProject);

        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);
            when(metricsScope.withDimension(anyString(), anyString())).thenReturn(metricsScope);

            RenderableException ex =
                    assertThrows(
                            RenderableException.class,
                            () ->
                                    resource.updateProject(
                                            req, opcRequestId, principal, authorizationRequest));
            assertEquals(ErrorCode.InvalidParameter, ex.getErrorCode());
            verify(metricsScope, never()).recordSuccess();
            verify(projectService, never()).updateProject(any(), any(), any(), any(), any(), any());
        }
    }

    @Test
    void testDeleteProject_success() {
        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            when(metricsScope.withDimension(anyString(), anyString())).thenReturn(metricsScope);
            when(metricsScope.recordSuccess()).thenReturn(metricsScope);

            doNothing().when(projectService).deleteProject(anyString(), any());
            resource.deleteProject(projectId, opcRequestId, principal, authorizationRequest);

            verify(projectService).deleteProject(projectId, metricsScope);
            verify(metricsScope).recordSuccess();
        }
    }

    @Test
    void testGetProject_success() {
        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            when(metricsScope.recordSuccess()).thenReturn(metricsScope);
            when(projectService.getProject(projectId, metricsScope)).thenReturn(project);

            Project result =
                    resource.getProject(projectId, opcRequestId, principal, authorizationRequest);
            assertEquals(project, result);

            verify(projectService).getProject(projectId, metricsScope);
            verify(metricsScope).recordSuccess();
        }
    }

    @Test
    void testGetProjectList_success() {
        List<Project> projects = List.of(project);
        when(projectService.getProjectListByVendor(vendorName)).thenReturn(projects);
        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            when(metricsScope.withDimension(anyString(), anyString())).thenReturn(metricsScope);
            when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);
            when(metricsScope.recordSuccess()).thenReturn(metricsScope);

            List<Project> result =
                    resource.getProjectList(
                            vendorName, null, opcRequestId, principal, authorizationRequest);
            assertEquals(projects, result);

            verify(projectService).getProjectListByVendor(vendorName);
            verify(metricsScope).recordSuccess();
        }
    }

    @Test
    void testGetProjectList_withRegionOnly_success() {
        List<Project> projects = List.of(project);
        when(projectService.getProjectListByRegion(region)).thenReturn(projects);
        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            when(metricsScope.withDimension(anyString(), anyString())).thenReturn(metricsScope);
            when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);
            when(metricsScope.recordSuccess()).thenReturn(metricsScope);

            List<Project> result =
                    resource.getProjectList(
                            null, region, opcRequestId, principal, authorizationRequest);
            assertEquals(projects, result);

            verify(projectService).getProjectListByRegion(region);
            verify(metricsScope).recordSuccess();
        }
    }

    @Test
    void testGetProjectList_withVendorAndRegion_success() {
        List<Project> projects = List.of(project);
        when(projectService.getProjectListByVendorAndRegion(vendorName, region))
                .thenReturn(projects);
        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            when(metricsScope.withDimension(anyString(), anyString())).thenReturn(metricsScope);
            when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);
            when(metricsScope.recordSuccess()).thenReturn(metricsScope);

            List<Project> result =
                    resource.getProjectList(
                            vendorName, region, opcRequestId, principal, authorizationRequest);
            assertEquals(projects, result);

            verify(projectService).getProjectListByVendorAndRegion(vendorName, region);
            verify(metricsScope).recordSuccess();
        }
    }

    @Test
    void testGetAllProjectList_all_and_regionOnly_success() {
        List<Project> projects = List.of(project);
        when(projectService.getProjectList()).thenReturn(projects);
        when(projectService.getProjectListByRegion(region)).thenReturn(projects);
        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);

            when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);
            when(metricsScope.withDimension(anyString(), anyString())).thenReturn(metricsScope);
            when(metricsScope.recordSuccess()).thenReturn(metricsScope);

            // No region (should call getProjectList)
            List<Project> resultAll =
                    resource.getAllProjectList(null, opcRequestId, principal, authorizationRequest);
            assertEquals(projects, resultAll);
            verify(projectService).getProjectList();

            // Region provided (should call getProjectListByRegion)
            List<Project> resultRegion =
                    resource.getAllProjectList(
                            region, opcRequestId, principal, authorizationRequest);
            assertEquals(projects, resultRegion);
            verify(projectService).getProjectListByRegion(region);

            verify(metricsScope, times(2)).recordSuccess();
        }
    }

    @Test
    void testDeleteProject_emptyProjectId() {
        assertThrows(
                RenderableException.class,
                () -> resource.deleteProject("", opcRequestId, principal, authorizationRequest));
    }
}
