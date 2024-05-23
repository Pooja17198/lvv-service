package com.oracle.pic.networking.lvv.service.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.networking.lvv.service.kiev.KievManager;
import com.oracle.pic.networking.lvv.service.utils.PaginationToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ProjectServiceTest {

    @Mock private KievManager mockedKievManager;

    private ProjectService projectService;

    private static final String PROJECT_ID = "DCIB-101";
    private static final String VENDOR_NAME = "vendor name";
    private static final String BUILDING = "PHX1";
    private static final String BLOCK = "15";
    private static final String TYPE = "compute";

    @BeforeEach
    public void setup() {
        MockitoAnnotations.initMocks(this);
        this.projectService = new ProjectService(this.mockedKievManager);
    }

    @Test
    public void createUpdateProjectShouldThrowException() throws Exception {
        when(this.mockedKievManager.addProjectItem(any())).thenThrow(new Exception());
        assertThrows(
                RenderableException.class,
                () ->
                        this.projectService.createUpdateProject(
                                PROJECT_ID, VENDOR_NAME, BUILDING, BLOCK, TYPE));
    }

    @Test
    public void getProjectShouldThrowException() throws Exception {
        when(this.mockedKievManager.getProjectItem(any())).thenThrow(new Exception());
        assertThrows(RenderableException.class, () -> this.projectService.getProject(PROJECT_ID));
    }

    @Test
    public void getProjectListByVendorShouldThrowException() throws Exception {
        when(this.mockedKievManager.getAllProjectItem(any(), any())).thenThrow(new Exception());
        PaginationToken paginationToken = new PaginationToken();
        assertThrows(
                RenderableException.class,
                () -> this.projectService.getProjectListByVendor(paginationToken, VENDOR_NAME));
    }
}
