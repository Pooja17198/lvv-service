package com.oracle.pic.networking.lvv.service.resources;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.identity.authentication.Principal;
import com.oracle.pic.identity.authorization.sdk.AuthorizationRequest;
import com.oracle.pic.networking.lvv.service.model.CablingTaskCollection;
import com.oracle.pic.networking.lvv.service.service.CablingTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class CablingTaskResourceTest {

    @Mock private CablingTaskService mockedCablingTaskService;
    @Mock private com.oracle.pic.networking.lvv.service.kiev.ProjectItemDao mockedProjectItemDao;

    private CablingTaskResource cablingTaskResource;
    private static final String PROJECT_ID = "PROJ123";
    private static final String BUILDING = "PHX1";
    private static final String BLOCK = "15";
    private static final String REGION_NAME = "mock-region";
    private static final String RACK_SERIAL_NUMBER = "1S7D9XCTO1WWJ102GBN7";
    private static final String OPC_REQUEST_ID = "opcRequestId";
    private static final String TASK_ID = "DO-1191815";
    private static final String RACK_LOCATION = "1403";

    @Mock private Principal principalMock;

    @Mock private AuthorizationRequest authorizationRequestMock;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        this.cablingTaskResource =
                new CablingTaskResource(mockedCablingTaskService, mockedProjectItemDao);
    }

    @Test
    void shouldGetCablingTasks_projectPath() {
        CablingTaskCollection cablingTaskCollection = mock(CablingTaskCollection.class);
        when(this.mockedCablingTaskService.getCablingTasksForProject(PROJECT_ID))
                .thenReturn(cablingTaskCollection);
        // mock for required dao
        com.oracle.pic.networking.lvv.service.kiev.ProjectItem projItem =
                mock(com.oracle.pic.networking.lvv.service.kiev.ProjectItem.class);
        when(projItem.getRegionName()).thenReturn(REGION_NAME);
        when(mockedProjectItemDao.getProjectItem(PROJECT_ID)).thenReturn(projItem);

        assertDoesNotThrow(
                () ->
                        this.cablingTaskResource.getCablingTasks(
                                PROJECT_ID,
                                null,
                                null,
                                null,
                                REGION_NAME,
                                OPC_REQUEST_ID,
                                principalMock,
                                authorizationRequestMock));
        verify(this.mockedCablingTaskService).getCablingTasksForProject(PROJECT_ID);
    }

    @Test
    void shouldGetCablingTasks_blockPath() {
        CablingTaskCollection cablingTaskCollection = mock(CablingTaskCollection.class);
        when(this.mockedCablingTaskService.getCablingTasks(BUILDING, BLOCK, RACK_SERIAL_NUMBER))
                .thenReturn(cablingTaskCollection);

        assertDoesNotThrow(
                () ->
                        this.cablingTaskResource.getCablingTasks(
                                null,
                                BUILDING,
                                BLOCK,
                                RACK_SERIAL_NUMBER,
                                REGION_NAME,
                                OPC_REQUEST_ID,
                                principalMock,
                                authorizationRequestMock));

        verify(this.mockedCablingTaskService).getCablingTasks(BUILDING, BLOCK, RACK_SERIAL_NUMBER);
    }

    @Test
    void shouldThrowIfNoProjectIdOrBlock() {
        RenderableException ex =
                assertThrows(
                        RenderableException.class,
                        () ->
                                this.cablingTaskResource.getCablingTasks(
                                        null,
                                        null,
                                        null,
                                        null,
                                        REGION_NAME,
                                        OPC_REQUEST_ID,
                                        principalMock,
                                        authorizationRequestMock));
        assertTrue(ex.getMessage().contains("Either Project ID should be present"));
        verify(this.mockedCablingTaskService, never()).getCablingTasksForProject(any());
        verify(this.mockedCablingTaskService, never()).getCablingTasks(any(), any(), any());
    }

    @Test
    void shouldGetClosedCablingTasks_success() {
        CablingTaskCollection cablingTaskCollection = mock(CablingTaskCollection.class);
        when(this.mockedCablingTaskService.getClosedCablingTasks(BUILDING, BLOCK, RACK_LOCATION))
                .thenReturn(cablingTaskCollection);

        assertDoesNotThrow(
                () ->
                        this.cablingTaskResource.getClosedCablingTasks(
                                BUILDING,
                                BLOCK,
                                RACK_LOCATION,
                                OPC_REQUEST_ID,
                                principalMock,
                                authorizationRequestMock));
        verify(this.mockedCablingTaskService).getClosedCablingTasks(BUILDING, BLOCK, RACK_LOCATION);
    }

    @Test
    void shouldHandleClosedCablingTasks_serviceReturnsNull() {
        when(this.mockedCablingTaskService.getClosedCablingTasks(BUILDING, BLOCK, RACK_LOCATION))
                .thenReturn(null);
        assertDoesNotThrow(
                () ->
                        this.cablingTaskResource.getClosedCablingTasks(
                                BUILDING,
                                BLOCK,
                                RACK_LOCATION,
                                OPC_REQUEST_ID,
                                principalMock,
                                authorizationRequestMock));
        verify(this.mockedCablingTaskService).getClosedCablingTasks(BUILDING, BLOCK, RACK_LOCATION);
    }

    @Test
    void shouldThrowWhenResolveValidationFailureTaskServiceThrows() {
        doThrow(new RuntimeException("fail"))
                .when(mockedCablingTaskService)
                .resolveValidationFailureTask(TASK_ID);

        assertThrows(
                RuntimeException.class,
                () ->
                        this.cablingTaskResource.resolveValidationFailureTask(
                                TASK_ID, REGION_NAME, principalMock, authorizationRequestMock));
        verify(this.mockedCablingTaskService).resolveValidationFailureTask(TASK_ID);
    }
}
