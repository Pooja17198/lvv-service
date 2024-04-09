package com.oracle.pic.networking.lvv.service.resources;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oracle.pic.identity.authentication.Principal;
import com.oracle.pic.identity.authorization.sdk.AuthorizationRequest;
import com.oracle.pic.networking.lvv.service.model.CablingTaskCollection;
import com.oracle.pic.networking.lvv.service.service.CablingTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CablingTaskResourceTest {

    @Mock private CablingTaskService mockedCablingTaskService;

    private CablingTaskResource cablingTaskResource;

    private static final String BUILDING = "PHX1";
    private static final String BLOCK = "15";
    private static final String RACK_SERIAL_NUMBER = "1S7D9XCTO1WWJ102GBN7";
    private static final String OPC_REQUEST_ID = "opcRequestId";
    private static final String TASK_ID = "DO-1191815";

    @Mock private Principal principalMock;

    @Mock private AuthorizationRequest authorizationRequestMock;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.initMocks(this);
        this.cablingTaskResource = new CablingTaskResource(mockedCablingTaskService);
    }

    @Test
    public void shouldGetCablingTasks() {
        CablingTaskCollection cablingTaskCollection = mock();
        when(this.mockedCablingTaskService.getCablingTasks(BUILDING, BLOCK, RACK_SERIAL_NUMBER))
                .thenReturn(cablingTaskCollection);
        this.cablingTaskResource.getCablingTasks(
                BUILDING,
                BLOCK,
                RACK_SERIAL_NUMBER,
                OPC_REQUEST_ID,
                this.principalMock,
                this.authorizationRequestMock);
        verify(this.mockedCablingTaskService).getCablingTasks(BUILDING, BLOCK, RACK_SERIAL_NUMBER);
    }

    @Test
    public void shouldResolveValidationFailureTask() {
        doNothing().when(this.mockedCablingTaskService).resolveValidationFailureTask(TASK_ID);
        this.cablingTaskResource.resolveValidationFailureTask(
                TASK_ID, this.principalMock, this.authorizationRequestMock);
        verify(this.mockedCablingTaskService).resolveValidationFailureTask(TASK_ID);
    }
}
