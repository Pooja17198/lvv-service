package com.oracle.pic.networking.lvv.service.resources;

import static com.oracle.pic.networking.lvv.service.resources.CablingTaskResource.Metrics.GetCableValidationFailureTask;
import static com.oracle.pic.networking.lvv.service.resources.CablingTaskResource.Metrics.GetCableValidationFailureTaskFailure;
import static com.oracle.pic.networking.lvv.service.resources.CablingTaskResource.Metrics.GetCablingTasks;
import static com.oracle.pic.networking.lvv.service.resources.CablingTaskResource.Metrics.GetCablingTasksFailure;
import static com.oracle.pic.networking.lvv.service.resources.CablingTaskResource.Metrics.ResolveValidationFailureTask;
import static com.oracle.pic.networking.lvv.service.resources.CablingTaskResource.Metrics.ResolveValidationFailureTaskFailure;

import com.google.inject.Inject;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.commons.metrics.metrictypes.Timer;
import com.oracle.pic.identity.authentication.Principal;
import com.oracle.pic.identity.authorization.sdk.AuthorizationRequest;
import com.oracle.pic.networking.lvv.service.api.AbstractCablingTasksResource;
import com.oracle.pic.networking.lvv.service.model.CablingTaskCollection;
import com.oracle.pic.networking.lvv.service.model.ResolveValidationFailureTaskResponse;
import com.oracle.pic.networking.lvv.service.service.CablingTaskService;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Context;
import lombok.AccessLevel;
import lombok.Getter;

public class CablingTaskResource extends AbstractCablingTasksResource {

    private static final String METRIC_SCOPE_NAME = "CablingTaskApi";

    private CablingTaskService cablingTaskService;

    enum Metrics {
        GetCablingTasks,
        GetCablingTasksFailure,
        ResolveValidationFailureTask,
        ResolveValidationFailureTaskFailure,
        GetCableValidationFailureTask,
        GetCableValidationFailureTaskFailure
    }

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
        try (MetricsScope scope = MetricsScope.create(METRIC_SCOPE_NAME)) {
            Timer timer = scope.timerStart("createProjectStartMillis");
            try {
                CablingTaskCollection collection =
                        this.cablingTaskService.getCablingTasks(building, block, rackSerialNumber);
                scope.emit(GetCablingTasks, 1);
                scope.recordSuccess();
                return collection;
            } catch (Exception ex) {
                scope.emit(GetCablingTasksFailure, 1);
                throw ex;
            } finally {
                scope.timerStop(timer);
            }
        }
    }

    @Override
    public ResolveValidationFailureTaskResponse resolveValidationFailureTask(
            String cablingTaskId, Principal principal, AuthorizationRequest authorizationRequest) {
        try (MetricsScope scope = MetricsScope.create(METRIC_SCOPE_NAME)) {
            Timer timer = scope.timerStart("resolveValidationFailureTaskStartMillis");
            try {
                this.cablingTaskService.resolveValidationFailureTask(cablingTaskId);
                scope.emit(ResolveValidationFailureTask, 1);
                scope.recordSuccess();
                return new ResolveValidationFailureTaskResponse(cablingTaskId);
            } catch (Exception ex) {
                scope.emit(ResolveValidationFailureTaskFailure, 1);
                throw ex;
            } finally {
                scope.timerStop(timer);
            }
        }
    }

    @Override
    public String getCableValidationFailureTask(
            String cablingTaskId, Principal principal, AuthorizationRequest authorizationRequest) {
        try (MetricsScope scope = MetricsScope.create(METRIC_SCOPE_NAME)) {
            Timer timer = scope.timerStart("createProjectStartMillis");
            try {
                // TODO: Need to return CableValidationFailureTasks instead of String. API specs are
                // already
                // prepared
                String result =
                        this.cablingTaskService.getCableValidationFailureTask(cablingTaskId);
                scope.emit(GetCableValidationFailureTask, 1);
                scope.recordSuccess();
                return result;
            } catch (Exception ex) {
                scope.emit(GetCableValidationFailureTaskFailure, 1);
                throw ex;
            } finally {
                scope.timerStop(timer);
            }
        }
    }
}
