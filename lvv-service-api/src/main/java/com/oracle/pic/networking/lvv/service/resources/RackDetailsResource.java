package com.oracle.pic.networking.lvv.service.resources;

import com.oracle.pic.commons.exceptions.server.ErrorCode;
import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.identity.authentication.Principal;
import com.oracle.pic.identity.authorization.sdk.AuthorizationRequest;
import com.oracle.pic.networking.lvv.service.api.AbstractRackDetailsResource;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import com.oracle.pic.networking.lvv.service.model.Device;
import com.oracle.pic.networking.lvv.service.model.DeviceDetails;
import com.oracle.pic.networking.lvv.service.service.RackDetailsService;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

public class RackDetailsResource extends AbstractRackDetailsResource {

    RackDetailsService rackDetailsService;
    ResourceModelTransformer resourceModelTransformer;

    @Inject
    protected RackDetailsResource(
            RackDetailsService rackDetailsService,
            ResourceModelTransformer resourceModelTransformer) {
        this.rackDetailsService = rackDetailsService;
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
            List<Device> devices;

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

            scope.withDimension("region", regionName);
            scope.withDimension("building", building);
            scope.withDimension("rackNumber", rackNumber);

            List<DeviceDetails> deviceDetails =
                    this.rackDetailsService.getDeviceDetailsInRack(
                            rackSerialNumber, regionName, rackNumber, building, scope);

            scope.recordSuccess();
            return deviceDetails;
        }
    }
}
