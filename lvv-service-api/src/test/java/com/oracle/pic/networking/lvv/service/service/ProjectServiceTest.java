package com.oracle.pic.networking.lvv.service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.oracle.pic.commons.exceptions.server.ErrorCode;
import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.networking.lvv.service.kiev.BlockDetails;
import com.oracle.pic.networking.lvv.service.kiev.BlockDetailsDao;
import com.oracle.pic.networking.lvv.service.kiev.ProjectItem;
import com.oracle.pic.networking.lvv.service.kiev.ProjectItemDao;
import com.oracle.pic.networking.lvv.service.model.Project;
import com.oracle.pic.networking.lvv.service.resources.ResourceModelTransformer;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class ProjectServiceTest {

    @Mock ProjectItemDao projectItemDao;
    @Mock BlockDetailsDao blockDetailsDao;
    @Mock ResourceModelTransformer resourceModelTransformer;
    @Mock MetricsScope metricsScope;

    @InjectMocks ProjectService projectService;

    // Helper objects
    private final String projectId = "pid-123";
    private final String vendorName = "Vendor";
    private final String region = "us-region";
    private final String building = "Build";
    private final String block = "B1";
    private final List<String> blocks = List.of(block);
    private final String type = "test-type";

    @BeforeEach
    void setUp() {}

    @Test
    void testCheckBlockIsUnassigned_whenBlockDetailsNull() {
        when(blockDetailsDao.getBlockDetails(block, building)).thenReturn(null);
        assertTrue(projectService.checkBlockIsUnassigned(block, building));
    }

    @Test
    void testCheckBlockIsUnassigned_whenBlockDetailsExists() {
        BlockDetails blockDetails = mock(BlockDetails.class);
        when(blockDetailsDao.getBlockDetails(block, building)).thenReturn(blockDetails);
        when(blockDetails.getProjectId()).thenReturn("other-pid");
        assertFalse(projectService.checkBlockIsUnassigned(block, building));
    }

    @Test
    void testCreateProject_missingParameters() {
        List<Runnable> cases =
                List.of(
                        () ->
                                assertThrows(
                                        RenderableException.class,
                                        () ->
                                                projectService.createProject(
                                                        null,
                                                        vendorName,
                                                        region,
                                                        building,
                                                        blocks,
                                                        metricsScope)),
                        () ->
                                assertThrows(
                                        RenderableException.class,
                                        () ->
                                                projectService.createProject(
                                                        "",
                                                        vendorName,
                                                        region,
                                                        building,
                                                        blocks,
                                                        metricsScope)),
                        () ->
                                assertThrows(
                                        RenderableException.class,
                                        () ->
                                                projectService.createProject(
                                                        projectId,
                                                        null,
                                                        region,
                                                        building,
                                                        blocks,
                                                        metricsScope)),
                        () ->
                                assertThrows(
                                        RenderableException.class,
                                        () ->
                                                projectService.createProject(
                                                        projectId,
                                                        "",
                                                        region,
                                                        building,
                                                        blocks,
                                                        metricsScope)),
                        () ->
                                assertThrows(
                                        RenderableException.class,
                                        () ->
                                                projectService.createProject(
                                                        projectId,
                                                        vendorName,
                                                        null,
                                                        building,
                                                        blocks,
                                                        metricsScope)),
                        () ->
                                assertThrows(
                                        RenderableException.class,
                                        () ->
                                                projectService.createProject(
                                                        projectId,
                                                        vendorName,
                                                        region,
                                                        "",
                                                        blocks,
                                                        metricsScope)),
                        () ->
                                assertThrows(
                                        RenderableException.class,
                                        () ->
                                                projectService.createProject(
                                                        projectId,
                                                        vendorName,
                                                        region,
                                                        building,
                                                        null,
                                                        metricsScope)),
                        () ->
                                assertThrows(
                                        RenderableException.class,
                                        () ->
                                                projectService.createProject(
                                                        projectId,
                                                        vendorName,
                                                        region,
                                                        building,
                                                        Collections.emptyList(),
                                                        metricsScope)));
        cases.forEach(Runnable::run);
    }

    @Test
    void testUpdateProject_missingParameters() {
        List<Runnable> cases =
                List.of(
                        () ->
                                assertThrows(
                                        RenderableException.class,
                                        () ->
                                                projectService.updateProject(
                                                        null,
                                                        vendorName,
                                                        region,
                                                        building,
                                                        blocks,
                                                        metricsScope)),
                        () ->
                                assertThrows(
                                        RenderableException.class,
                                        () ->
                                                projectService.updateProject(
                                                        "",
                                                        vendorName,
                                                        region,
                                                        building,
                                                        blocks,
                                                        metricsScope)),
                        () ->
                                assertThrows(
                                        RenderableException.class,
                                        () ->
                                                projectService.updateProject(
                                                        projectId,
                                                        null,
                                                        region,
                                                        building,
                                                        blocks,
                                                        metricsScope)),
                        () ->
                                assertThrows(
                                        RenderableException.class,
                                        () ->
                                                projectService.updateProject(
                                                        projectId,
                                                        "",
                                                        region,
                                                        building,
                                                        blocks,
                                                        metricsScope)),
                        () ->
                                assertThrows(
                                        RenderableException.class,
                                        () ->
                                                projectService.updateProject(
                                                        projectId,
                                                        vendorName,
                                                        null,
                                                        building,
                                                        blocks,
                                                        metricsScope)),
                        () ->
                                assertThrows(
                                        RenderableException.class,
                                        () ->
                                                projectService.updateProject(
                                                        projectId,
                                                        vendorName,
                                                        region,
                                                        "",
                                                        blocks,
                                                        metricsScope)),
                        () ->
                                assertThrows(
                                        RenderableException.class,
                                        () ->
                                                projectService.updateProject(
                                                        projectId,
                                                        vendorName,
                                                        region,
                                                        building,
                                                        null,
                                                        metricsScope)),
                        () ->
                                assertThrows(
                                        RenderableException.class,
                                        () ->
                                                projectService.updateProject(
                                                        projectId,
                                                        vendorName,
                                                        region,
                                                        building,
                                                        Collections.emptyList(),
                                                        metricsScope)));
        cases.forEach(Runnable::run);
    }

    @Test
    void testCreateProject_success() {
        doNothing().when(projectItemDao).addProjectItem(any(ProjectItem.class), anyList(), any());
        projectService.createProject(projectId, vendorName, region, building, blocks, metricsScope);
        verify(projectItemDao, times(1)).addProjectItem(any(), any(), any());
    }

    @Test
    void testUpdateProject_success() {
        doNothing()
                .when(projectItemDao)
                .updateProjectItem(any(ProjectItem.class), anyList(), any());
        projectService.updateProject(projectId, vendorName, region, building, blocks, metricsScope);
        verify(projectItemDao, times(1)).updateProjectItem(any(), any(), any());
    }

    @Test
    void testGetProject_emptyProjectId() {
        assertThrows(RenderableException.class, () -> projectService.getProject("", metricsScope));
        assertThrows(
                RenderableException.class, () -> projectService.getProject(null, metricsScope));
    }

    @Test
    void testGetProject_noBlocks() {
        when(projectItemDao.getProjectItem(projectId)).thenReturn(mock(ProjectItem.class));
        when(blockDetailsDao.getBlockDetailsForProject(projectId))
                .thenReturn(Collections.emptyList());
        RenderableException ex =
                assertThrows(
                        RenderableException.class,
                        () -> projectService.getProject(projectId, metricsScope));
        assertEquals(ErrorCode.IncorrectState, ex.getErrorCode());
    }

    @Test
    void testGetProject_success() {
        ProjectItem item = mock(ProjectItem.class);
        List<BlockDetails> blockDetails = List.of(mock(BlockDetails.class));
        Project project = mock(Project.class);
        when(projectItemDao.getProjectItem(projectId)).thenReturn(item);
        when(blockDetailsDao.getBlockDetailsForProject(projectId)).thenReturn(blockDetails);
        when(resourceModelTransformer.toModel(item, blockDetails)).thenReturn(project);
        assertEquals(project, projectService.getProject(projectId, metricsScope));
    }

    @Test
    void testDeleteProject_success() {
        doNothing().when(projectItemDao).deleteProjectItem(projectId, metricsScope);
        projectService.deleteProject(projectId, metricsScope);
        verify(projectItemDao, times(1)).deleteProjectItem(projectId, metricsScope);
    }

    @Test
    void testGetProjectListByVendor_noProjects() {
        when(projectItemDao.getProjectItemsForVendor(vendorName))
                .thenReturn(Collections.emptyList());
        List<Project> result = projectService.getProjectListByVendor(vendorName);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetProjectListByVendor_success() {
        ProjectItem projItem =
                ProjectItem.builder().projectId(projectId).vendorName(vendorName).build();
        List<BlockDetails> blockDetailsList = List.of(mock(BlockDetails.class));
        Project project = mock(Project.class);

        when(projectItemDao.getProjectItemsForVendor(vendorName)).thenReturn(List.of(projItem));
        when(blockDetailsDao.getBlockDetailsForProject(projectId)).thenReturn(blockDetailsList);
        when(resourceModelTransformer.toModel(projItem, blockDetailsList)).thenReturn(project);

        List<Project> result = projectService.getProjectListByVendor(vendorName);
        assertEquals(1, result.size());
        assertEquals(project, result.get(0));
    }
}
