package com.oracle.pic.networking.lvv.service.resources;

import com.google.inject.Inject;
import com.oracle.pic.commons.exceptions.server.ErrorCode;
import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.identity.authentication.Principal;
import com.oracle.pic.identity.authorization.sdk.AuthorizationRequest;
import com.oracle.pic.networking.lvv.service.api.AbstractPatchPanelResource;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import com.oracle.pic.networking.lvv.service.kiev.PatchPanelEntry;
import com.oracle.pic.networking.lvv.service.model.PatchPanelItem;
import com.oracle.pic.networking.lvv.service.service.PatchPanelService;
import com.oracle.pic.networking.lvv.service.utils.GeneralUtils;
import java.util.ArrayList;
import java.util.List;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ToString
public class PatchPanelResource extends AbstractPatchPanelResource {

    private final PatchPanelService patchPanelService;

    @Inject
    protected PatchPanelResource(PatchPanelService patchPanelService) {
        this.patchPanelService = patchPanelService;
    }

    @Override
    public List<PatchPanelItem> getPatchPanel(
            String buildingName,
            String rackNumber,
            String rackSerialNumber,
            String regionName,
            String opcRequestId,
            Principal principal,
            AuthorizationRequest authorizationRequest) {

        try (MetricsScope scope =
                MetricsScope.create(MetricNames.MetricScopeNames.GET_PATCH_PANEL.name())) {

            List<String> missing = new ArrayList<>();
            if (buildingName == null || buildingName.isBlank()) missing.add("buildingName");
            if (rackNumber == null || rackNumber.isBlank()) missing.add("rackNumber");
            if (rackSerialNumber == null || rackSerialNumber.isBlank()) missing.add("rackSerialNumber");

            if (!missing.isEmpty()) {
                scope.emit(MetricNames.GetPatchPanel.MissingParameters.name(), 1.0);
                throw new RenderableException(
                        ErrorCode.MissingParameter,
                        "Missing or empty parameters: " + String.join(", ", missing));
            }

            scope.withDimension("region", GeneralUtils.getRegionInternalName(regionName));
            scope.withDimension("buildingName", buildingName);
            scope.withDimension("rackNumber", rackNumber);
            scope.emit(MetricNames.GetPatchPanel.GetPatchPanel.name(), 1.0);

            log.info("GetPatchPanel for rack {} building {} rackSerial {}",
                    rackNumber, buildingName, rackSerialNumber);

            List<PatchPanelEntry> entries;
            try {
                entries = patchPanelService.getPatchPanelForRack(buildingName, rackNumber, rackSerialNumber);
            } catch (RuntimeException e) {
                scope.emit(MetricNames.GetPatchPanel.IdeFetchFailed.name(), 1.0);
                log.error("Failed to fetch patch panel for rack {} building {}", rackNumber, buildingName, e);
                throw new RenderableException(
                        ErrorCode.ExternalServerInvalidResponse,
                        "Failed to fetch patch panel data: " + e.getMessage());
            }

            List<PatchPanelItem> response = new ArrayList<>(entries.size());
            for (PatchPanelEntry entry : entries) {
                PatchPanelItem item =
                        PatchPanelItem.builder()
                                .deviceName(entry.getDeviceName())
                                .devicePort(entry.getDevicePort())
                                .buildingName(entry.getBuildingName())
                                .roomName(entry.getRoomName())
                                .rackNumber(entry.getRackNumber())
                                .easyMark(entry.getEasyMark())
                                .build();
                response.add(item);
            }

            scope.recordSuccess();
            return response;
        }
    }
}
