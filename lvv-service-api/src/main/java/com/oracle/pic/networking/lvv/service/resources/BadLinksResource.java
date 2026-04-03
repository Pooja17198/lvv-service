package com.oracle.pic.networking.lvv.service.resources;

import com.google.inject.Inject;
import com.oracle.pic.commons.exceptions.server.ErrorCode;
import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.identity.authentication.Principal;
import com.oracle.pic.identity.authorization.sdk.AuthorizationRequest;
import com.oracle.pic.networking.lvv.service.api.AbstractBadLinksResource;
import com.oracle.pic.networking.lvv.service.config.LvvServiceApiConfiguration;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import com.oracle.pic.networking.lvv.service.model.BadLinkDetail;
import com.oracle.pic.networking.lvv.service.service.BadLinksService;
import com.oracle.pic.networking.lvv.service.utils.GeneralUtils;
import java.util.List;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ToString
public class BadLinksResource extends AbstractBadLinksResource {

    private final BadLinksService badLinksService;
    private final LvvServiceApiConfiguration config;

    @Inject
    protected BadLinksResource(BadLinksService badLinksService, LvvServiceApiConfiguration config) {
        this.badLinksService = badLinksService;
        this.config = config;
    }

    @Override
    public List<BadLinkDetail> getBadLinks(
            String regionName,
            String buildingName,
            String opcRequestId,
            Principal principal,
            AuthorizationRequest authorizationRequest) {

        if (!config.isEnableNetworkMonitoringAndAlerting()) {
            log.info(
                    "Network monitoring and alerting disabled by config; returning empty badLinks list for building {}",
                    buildingName);
            return List.of();
        }

        try (MetricsScope scope =
                MetricsScope.create(MetricNames.MetricScopeNames.BAD_LINKS.name())) {

            log.info("GetBadLinks for building {}", buildingName);

            if (buildingName == null || buildingName.isBlank()) {
                scope.emit(MetricNames.BadLinks.EmptyBuildingName.name(), 1.0);
                throw new RenderableException(
                        ErrorCode.MissingParameter, "buildingName cannot be empty");
            }

            scope.withDimension("region", GeneralUtils.getRegionInternalName(regionName));
            scope.withDimension("buildingName", buildingName);
            scope.emit(MetricNames.BadLinks.GetBadLinks.name(), 1.0);
            List<BadLinkDetail> details = badLinksService.getBadLinks(buildingName, scope);
            scope.recordSuccess();
            return details;
        }
    }
}
