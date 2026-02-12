package com.oracle.pic.networking.lvv.service.resources;

import com.oracle.pic.commons.exceptions.server.ErrorCode;
import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.identity.authentication.Principal;
import com.oracle.pic.identity.authorization.sdk.AuthorizationRequest;
import com.oracle.pic.networking.lvv.service.api.AbstractRacksResource;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import com.oracle.pic.networking.lvv.service.model.DeviceDetails;
import com.oracle.pic.networking.lvv.service.model.ProjectRack;
import com.oracle.pic.networking.lvv.service.service.RacksService;
import com.oracle.pic.networking.lvv.service.utils.GeneralUtils;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

public class RacksResource extends AbstractRacksResource {

    RacksService racksService;
    ResourceModelTransformer resourceModelTransformer;

    @Inject
    protected RacksResource(
            RacksService racksService, ResourceModelTransformer resourceModelTransformer) {
        this.racksService = racksService;
        this.resourceModelTransformer = resourceModelTransformer;
    }

    @Override
    public List<DeviceDetails> listDevicesInRack(
            String rackSerialNumber,
            String rackNumber,
            String building,
            String regionName,
            String opcRequestId,
            Principal principal,
            AuthorizationRequest authorizationRequest) {
        try (MetricsScope scope =
                MetricsScope.create(MetricNames.MetricScopeNames.RACK_DETAILS.name())) {

            // NULL Checks
            List<String> missing = new ArrayList<>();

            if (rackSerialNumber == null || rackSerialNumber.isBlank()) {
                missing.add("rackSerialNumber");
            }

            if (rackNumber == null || rackNumber.isBlank()) {
                missing.add("rackNumber");
            }

            if (building == null || building.isBlank()) {
                missing.add("building");
            }

            if (regionName == null || regionName.isBlank()) {
                missing.add("regionName");
            }

            if (!missing.isEmpty()) {
                scope.emit(MetricNames.RackDetails.MissingParameters.name(), 1.0);
                throw new RenderableException(
                        ErrorCode.MissingParameter,
                        "Missing or empty parameters: " + String.join(", ", missing));
            }

            scope.withDimension("region", GeneralUtils.getRegionInternalName(regionName));
            scope.withDimension("building", building);
            scope.withDimension("rackNumber", rackNumber);

            List<DeviceDetails> deviceDetails =
                    this.racksService.getDeviceDetailsInRack(
                            rackSerialNumber, regionName, rackNumber, building, scope);

            scope.recordSuccess();
            return deviceDetails;
        }
    }

    @Override
    public List<ProjectRack> listProjectRacks(
            String projectId,
            String regionName,
            Boolean showAvailableRacks,
            String opcRequestId,
            Principal principal,
            AuthorizationRequest authorizationRequest) {

        try (MetricsScope scope =
                MetricsScope.create(MetricNames.MetricScopeNames.FETCH_RACKS.name())) {

            if (projectId.isEmpty()) {
                scope.emit(MetricNames.FetchRacks.ProjectIdNull.name(), 1.0);
                throw new RenderableException(
                        ErrorCode.MissingParameter, "Project ID cannot be empty");
            }

            if (regionName.isEmpty()) {
                scope.emit(MetricNames.FetchRacks.RegionNameNull.name(), 1.0);
                throw new RenderableException(
                        ErrorCode.MissingParameter, "Region name cannot be empty");
            }

            scope.withDimension("projectId", projectId);
            scope.withDimension("region", GeneralUtils.getRegionInternalName(regionName));

            boolean includeAvailable = Boolean.TRUE.equals(showAvailableRacks);

            List<ProjectRack> racks =
                    this.racksService.listProjectRacks(
                            projectId, regionName, includeAvailable, scope);

            scope.recordSuccess();
            return racks;
        }
    }
}
