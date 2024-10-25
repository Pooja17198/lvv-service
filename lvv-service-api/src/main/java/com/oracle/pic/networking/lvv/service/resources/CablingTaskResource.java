package com.oracle.pic.networking.lvv.service.resources;

import com.google.inject.Inject;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.identity.authentication.Principal;
import com.oracle.pic.identity.authorization.sdk.AuthorizationRequest;
import com.oracle.pic.networking.lvv.service.api.AbstractCablingTasksResource;
import com.oracle.pic.networking.lvv.service.model.CableValidationFailureTasks;
import com.oracle.pic.networking.lvv.service.model.CablingTaskCollection;
import com.oracle.pic.networking.lvv.service.model.ResolveValidationFailureTaskResponse;
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
        try (MetricsScope scope = MetricsScope.create("getCablingTasks")) {
            scope.emit("volume", 1.0);
            CablingTaskCollection collection =
                    this.cablingTaskService.getCablingTasks(building, block, rackSerialNumber);
            scope.recordSuccess();
            return collection;
        }
    }

    @Override
    public CablingTaskCollection getClosedCablingTasks(
            String building,
            String block,
            String rackSerialNumber,
            String opcRequestId,
            Principal principal,
            AuthorizationRequest authorizationRequest) {
        try (MetricsScope scope = MetricsScope.create("getClosedCablingTasks")) {
            scope.emit("volume", 1.0);
            CablingTaskCollection collection =
                    this.cablingTaskService.getClosedCablingTasks(
                            building, block, rackSerialNumber);
            scope.recordSuccess();
            return collection;
        }
    }

    @Override
    public ResolveValidationFailureTaskResponse resolveValidationFailureTask(
            String cablingTaskId, Principal principal, AuthorizationRequest authorizationRequest) {
        try (MetricsScope scope = MetricsScope.create("resolveValidationFailureTask")) {
            scope.emit("volume", 1.0);

            this.cablingTaskService.resolveValidationFailureTask(cablingTaskId);
            scope.recordSuccess();
            return new ResolveValidationFailureTaskResponse(cablingTaskId);
        }
    }

    @Override
    public CableValidationFailureTasks getCableValidationFailureTask(
            String cablingTaskId, Principal principal, AuthorizationRequest authorizationRequest) {
        try (MetricsScope scope = MetricsScope.create("getCableValidationFailureTask")) {
            scope.emit("volume", 1.0);

            CableValidationFailureTasks result =
                    this.cablingTaskService.getCableValidationFailureTask(cablingTaskId);
            scope.recordSuccess();
            return result;
        }
    }
}
