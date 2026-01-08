package com.oracle.pic.networking.lvv.service.dependencies.planservice;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.networking.autonet.plan.service.PlanServiceVClient;
import com.oracle.pic.networking.autonet.plan.service.model.Device;
import com.oracle.pic.networking.autonet.plan.service.requests.GetDevicesByRackRequest;
import com.oracle.pic.networking.autonet.plan.service.responses.GetDevicesByRackResponse;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import com.oracle.pic.networking.lvv.service.utils.RetryHelper;
import java.util.*;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Builder
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PlanServiceHelper {

    private static final int DEFAULT_CLIENT_RETRY_COUNT = 3;
    private static final String DEVICE_STATE = "device.state";
    private static final String DEPLOYED_STATE = "deployed";
    private final PlanServiceClient planServiceClient;

    private boolean isMonitoredDevice(Device device) {
        Map<String, Object> configAttributes = device.getConfigAttributes();
        Preconditions.checkNotNull(
                configAttributes,
                String.format(
                        "Error retrieving config attributes for device : %s", device.getName()));
        Preconditions.checkNotNull(
                device.getState(),
                String.format("Error retrieving state for device : %s", device.getName()));
        return configAttributes.get("monitoring.interfaces") != null;
    }

    private boolean isDeployedState(Device device) {
        return device.getState().get("conf").get(DEVICE_STATE).equalsIgnoreCase(DEPLOYED_STATE);
    }

    public List<Device> getDeviceListInRack(
            String rackNumber, String building, String region, MetricsScope scope) {

        try {
            log.info(
                    "Fetching list of devices in the rack {} building {} region {}",
                    rackNumber,
                    building,
                    region);

            PlanServiceVClient planServiceVClient = planServiceClient.getPlanServiceVClient(region);

            GetDevicesByRackRequest request =
                    GetDevicesByRackRequest.builder()
                            .region(region)
                            .rackNumber(rackNumber)
                            .bldg(building)
                            .planUid(null)
                            .withState(true)
                            .build();

            scope.emit(MetricNames.RackDetails.FetchDevices.name(), 1.0);

            GetDevicesByRackResponse response =
                    RetryHelper.newRetryHelper(
                                    () -> planServiceVClient.getDevicesByRack(request),
                                    DEFAULT_CLIENT_RETRY_COUNT,
                                    RetryHelper.retryAll)
                            .run();

            List<Device> devices =
                    response.getItems() != null ? response.getItems() : new ArrayList<>();
            log.info("Found {} total devices in rack", devices.size());

            List<Device> monitoredDeployedDevices =
                    devices.stream()
                            .filter(this::isMonitoredDevice)
                            .filter(this::isDeployedState)
                            .toList();

            log.info(
                    "Found {} monitored and deployed state devices in rack",
                    monitoredDeployedDevices.size());

            log.info(
                    "Devices that needs to be validated for rack {} building {} region {}: {}",
                    rackNumber,
                    building,
                    region,
                    monitoredDeployedDevices);

            return monitoredDeployedDevices;

        } catch (Exception e) {
            log.error(
                    "Unable to fetch devices in rack {} building {} region {}",
                    rackNumber,
                    building,
                    region,
                    e);
            scope.emit(MetricNames.RackDetails.FetchDevicesFailed.name(), 1.0);
            return new ArrayList<>();
        }
    }
}
