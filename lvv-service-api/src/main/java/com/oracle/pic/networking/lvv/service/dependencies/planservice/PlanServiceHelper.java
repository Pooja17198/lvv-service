package com.oracle.pic.networking.lvv.service.dependencies.planservice;

import com.google.inject.Inject;
import com.oracle.bmc.model.BmcException;
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
                                    RetryHelper.PLAN_SERVICE_NON_RETRYABLE_404)
                            .run();

            List<Device> devices =
                    response.getItems() != null ? response.getItems() : new ArrayList<>();
            log.info("Found {} total devices in rack {}", devices.size(), rackNumber);
            long monitoredDeployedCount =
                    devices.stream()
                            .filter(DeviceValidationEligibilityUtils::isValidationEligibleDevice)
                            .count();
            log.info(
                    "Found {} monitored and deployed state devices in rack {}",
                    monitoredDeployedCount,
                    rackNumber);

            log.info(
                    "Devices in rack {} building {} region {}: {}",
                    rackNumber,
                    building,
                    region,
                    devices);

            return devices;
        } catch (BmcException e) {
            if (e.getStatusCode() == 404) {
                log.error(
                        "Devices not found in plan in rack {} building {} region {}. Request retries skipped.",
                        rackNumber,
                        building,
                        region,
                        e);
                scope.emit(MetricNames.RackDetails.DevicesNotFound.name(), 1.0);
            } else {
                handlePlanServiceException(rackNumber, building, region, scope, e);
            }
            return new ArrayList<>();
        } catch (Exception e) {
            handlePlanServiceException(rackNumber, building, region, scope, e);
            return new ArrayList<>();
        }
    }

    private static void handlePlanServiceException(
            String rackNumber,
            String building,
            String region,
            MetricsScope scope,
            Exception exception) {
        log.error(
                "Unable to fetch devices in rack {} building {} region {}",
                rackNumber,
                building,
                region,
                exception);
        scope.emit(MetricNames.RackDetails.FetchDevicesFailed.name(), 1.0);
    }
}
