package com.oracle.pic.networking.lvv.service.resources;

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

    private String building = "PHX1";
    private String block = "15";
    private String rackSerialNumber = "1S7D9XCTO1WWJ102GBN7";
    private String opcRequestId = "opcRequestId";
    @Mock private Principal principal;

    @Mock private AuthorizationRequest authorizationRequest;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.initMocks(this);
        this.cablingTaskResource = new CablingTaskResource(mockedCablingTaskService);
    }

    @Test
    public void getCablingTasksTest() {
        CablingTaskCollection cablingTaskCollection = mock();
        when(this.mockedCablingTaskService.getCablingTasks(
                        this.building, this.block, this.rackSerialNumber))
                .thenReturn(cablingTaskCollection);
        this.cablingTaskResource.getCablingTasks(
                this.building,
                this.block,
                this.rackSerialNumber,
                this.opcRequestId,
                this.principal,
                this.authorizationRequest);
        verify(this.mockedCablingTaskService)
                .getCablingTasks(this.building, this.block, this.rackSerialNumber);
    }
}
