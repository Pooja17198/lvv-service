package com.oracle.pic.networking.lvv.service.resources;

import com.google.inject.Inject;
import com.oracle.pic.identity.authentication.Principal;
import com.oracle.pic.identity.authorization.sdk.AuthorizationRequest;
import com.oracle.pic.networking.lvv.service.api.AbstractCablingTasksResource;
import com.oracle.pic.networking.lvv.service.model.CablingTaskCollection;
import com.oracle.pic.networking.lvv.service.service.CablingTaskService;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Context;
import lombok.AccessLevel;
import lombok.Getter;

public class CablingTaskResource extends AbstractCablingTasksResource {

    private CablingTaskService cablingTaskService;

    @Context
    @Getter(AccessLevel.PRIVATE)
    private HttpServletResponse httpServletResponse;

    @Inject
    protected CablingTaskResource(CablingTaskService cablingTaskService) {
        this.cablingTaskService = cablingTaskService;
    }

    @Override
    public CablingTaskCollection getCablingTasks(
            String building,
            String block,
            String rackSerialNumber,
            String opcRequestId,
            Principal principal,
            AuthorizationRequest authorizationRequest) {
        return this.cablingTaskService.getCablingTasks(building, block, rackSerialNumber);
    }

    @Override
    public void resolveValidationFailureTask(
            String cablingTaskId, Principal principal, AuthorizationRequest authorizationRequest) {
        this.cablingTaskService.resolveValidationFailureTask(cablingTaskId);
    }

    @Override
    public String getCableValidationFailureTask(
            String cablingTaskId, Principal principal, AuthorizationRequest authorizationRequest) {
        return this.cablingTaskService.getCableValidationFailureTask(cablingTaskId);
    }
}
