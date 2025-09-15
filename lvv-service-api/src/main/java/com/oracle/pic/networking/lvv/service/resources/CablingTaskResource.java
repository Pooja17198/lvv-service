package com.oracle.pic.networking.lvv.service.resources;

import com.google.inject.Inject;
import com.oracle.pic.commons.exceptions.server.ErrorCode;
import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.identity.authentication.Principal;
import com.oracle.pic.identity.authorization.sdk.AuthorizationRequest;
import com.oracle.pic.networking.lvv.service.api.AbstractCablingTasksResource;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import com.oracle.pic.networking.lvv.service.model.CableValidationFailureTasks;
import com.oracle.pic.networking.lvv.service.model.CablingTaskCollection;
import com.oracle.pic.networking.lvv.service.model.ResolveValidationFailureTaskResponse;
import com.oracle.pic.networking.lvv.service.service.CablingTaskService;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Context;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ToString
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
            String projectId,
            String building,
            String block,
            String rackSerialNumber,
            String opcRequestId,
            Principal principal,
            AuthorizationRequest authorizationRequest) {
        try (MetricsScope scope =
                MetricsScope.create(MetricNames.MetricScopeNames.CABLING_TASKS.name())) {
            CablingTaskCollection collection;
            if (projectId != null && !projectId.isEmpty()) {
                scope.emit(MetricNames.CablingTasks.GetCablingTasksForProject.name(), 1.0);
                collection = this.cablingTaskService.getCablingTasksForProject(projectId);
            } else if (building != null
                    && !building.isEmpty()
                    && block != null
                    && !block.isEmpty()) {
                scope.emit(MetricNames.CablingTasks.GetCablingTasksForBlock.name(), 1.0);
                collection =
                        this.cablingTaskService.getCablingTasks(building, block, rackSerialNumber);
            } else {
                throw new RenderableException(
                        ErrorCode.InvalidParameter,
                        "Either Project ID should be present or Building/Block should be provided");
            }
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
        try (MetricsScope scope =
                MetricsScope.create(MetricNames.MetricScopeNames.CABLING_TASKS.name())) {
            scope.emit(MetricNames.CablingTasks.GetClosedCablingTasks.name(), 1.0);
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
        try (MetricsScope scope =
                MetricsScope.create(MetricNames.MetricScopeNames.CABLING_TASKS.name())) {
            scope.emit(MetricNames.CablingTasks.ResolveCablingTask.name(), 1.0);

            this.cablingTaskService.resolveValidationFailureTask(cablingTaskId);
            scope.recordSuccess();
            return ResolveValidationFailureTaskResponse.builder()
                    .cablingTaskId(cablingTaskId)
                    .build();
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
