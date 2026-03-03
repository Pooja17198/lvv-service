package com.oracle.pic.networking.lvv.service.dependencies.planservice;

import com.google.inject.Inject;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.networking.autonet.plan.service.PlanServiceVClient;
import com.oracle.pic.networking.autonet.plan.service.model.Device;
import com.oracle.pic.networking.autonet.plan.service.requests.GetDevicesByRackRequest;
import com.oracle.pic.networking.autonet.plan.service.responses.GetDevicesByRackResponse;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import com.oracle.pic.networking.lvv.service.utils.DeviceValidationEligibilityUtils;
import com.oracle.pic.networking.lvv.service.utils.RetryHelper;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Builder
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PlanServiceHelper {

    private static final int DEFAULT_CLIENT_RETRY_COUNT = 3;
    private final PlanServiceClient planServiceClient;

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
            long monitoredDeployedCount =
                    devices.stream()
                            .filter(DeviceValidationEligibilityUtils::isValidationEligibleDevice)
                            .count();
            log.info(
                    "Found {} monitored and deployed state devices in rack",
                    monitoredDeployedCount);

            log.info(
                    "Devices in rack {} building {} region {}: {}",
                    rackNumber,
                    building,
                    region,
                    devices);

            return devices;

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
