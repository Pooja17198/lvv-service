package com.oracle.pic.networking.lvv.service.resources;

import com.google.inject.Inject;
import com.oracle.pic.commons.exceptions.server.ErrorCode;
import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.identity.authentication.Principal;
import com.oracle.pic.identity.authorization.sdk.AuthorizationRequest;
import com.oracle.pic.networking.lvv.service.api.AbstractCablingTasksResource;
import com.oracle.pic.networking.lvv.service.config.LvvServiceApiConfiguration;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import com.oracle.pic.networking.lvv.service.kiev.ProjectItem;
import com.oracle.pic.networking.lvv.service.kiev.ProjectItemDao;
import com.oracle.pic.networking.lvv.service.model.CablingTaskCollection;
import com.oracle.pic.networking.lvv.service.model.ResolveValidationFailureTaskResponse;
import com.oracle.pic.networking.lvv.service.service.CablingTaskService;
import com.oracle.pic.networking.lvv.service.utils.GeneralUtils;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Context;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ToString
public class CablingTaskResource extends AbstractCablingTasksResource {

    private CablingTaskService cablingTaskService;
    private ProjectItemDao projectItemDao;

    @Inject(optional = true)
    private LvvServiceApiConfiguration config;

    @Context
    @Getter(AccessLevel.PRIVATE)
    private HttpServletResponse httpServletResponse;

    @Inject
    protected CablingTaskResource(
            CablingTaskService cablingTaskService, ProjectItemDao projectItemDao) {
        this.cablingTaskService = cablingTaskService;
        this.projectItemDao = projectItemDao;
    }

    @Override
    public CablingTaskCollection getCablingTasks(
            String projectId,
            String building,
            String block,
            String rackSerialNumber,
            String regionName,
            String opcRequestId,
            Principal principal,
            AuthorizationRequest authorizationRequest) {
        try (MetricsScope scope =
                MetricsScope.create(MetricNames.MetricScopeNames.CABLING_TASKS.name())) {
            CablingTaskCollection collection;
            if (projectId != null && !projectId.isEmpty()) {
                ProjectItem projectItem = projectItemDao.getProjectItem(projectId);

                String region = projectItem.getRegionName();
                scope.withDimension("region", GeneralUtils.getRegionInternalName(region));
                scope.emit(MetricNames.CablingTasks.GetCablingTasksForProject.name(), 1.0);

                collection = this.cablingTaskService.getCablingTasksForProject(projectId);
            } else if (building != null
                    && !building.isEmpty()
                    && block != null
                    && !block.isEmpty()) {

                scope.withDimension("region", GeneralUtils.getRegionInternalName(regionName));
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

            String region = GeneralUtils.getRegionFromBuilding(building);
            scope.withDimension("region", GeneralUtils.getRegionInternalName(region));

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
            String cablingTaskId,
            String regionName,
            Principal principal,
            AuthorizationRequest authorizationRequest) {
        try (MetricsScope scope =
                MetricsScope.create(MetricNames.MetricScopeNames.CABLING_TASKS.name())) {
            // Validate region early to avoid NPE in withDimension
            if (regionName == null || regionName.isBlank()) {
                scope.emit(MetricNames.CablingTasks.RegionMissing.name(), 1.0);
                throw new RenderableException(
                        ErrorCode.MissingParameter, "regionName cannot be empty");
            }

            scope.withDimension("region", GeneralUtils.getRegionInternalName(regionName));
            scope.emit(MetricNames.CablingTasks.ResolveCablingTask.name(), 1.0);

            if (isResolveDisabledForRegion(regionName, scope)) {
                scope.emit(MetricNames.CablingTasks.ResolveDisabledRegion.name(), 1.0);
                throw new RenderableException(
                        ErrorCode.InvalidParameter,
                        String.format("Resolve is disabled for region %s", regionName));
            }

            this.cablingTaskService.resolveValidationFailureTask(cablingTaskId);
            scope.recordSuccess();
            return ResolveValidationFailureTaskResponse.builder()
                    .cablingTaskId(cablingTaskId)
                    .build();
        }
    }

    private boolean isResolveDisabledForRegion(@NonNull String regionName, MetricsScope scope) {
        if (config == null || config.getResolveDisabledRegions() == null) {
            return false;
        }
        for (String disabled : config.getResolveDisabledRegions()) {
            if (disabled != null && disabled.equalsIgnoreCase(regionName)) {
                return true;
            }
        }
        return false;
    }

    void setConfigForTest(LvvServiceApiConfiguration cfg) {
        this.config = cfg;
    }
}
