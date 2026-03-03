package com.oracle.pic.networking.lvv.service.utils;

import com.oracle.pic.networking.autonet.plan.service.model.Device;
import java.util.Map;

public final class DeviceValidationEligibilityUtils {

    public static final String NON_ELIGIBLE_REASON =
            "Device is not in monitored and deployed state.";
    private static final String MONITORING_INTERFACES = "monitoring.interfaces";
    private static final String CONF = "conf";
    private static final String DEVICE_STATE = "device.state";
    private static final String DEPLOYED_STATE = "deployed";

    private DeviceValidationEligibilityUtils() {}

    public static boolean isMonitoredDevice(Device device) {
        if (device == null) {
            return false;
        }
        Map<String, Object> configAttributes = device.getConfigAttributes();
        return configAttributes != null && configAttributes.get(MONITORING_INTERFACES) != null;
    }

    public static boolean isDeployedState(Device device) {
        if (device == null || device.getState() == null) {
            return false;
        }

        Map<String, String> confMap = device.getState().get(CONF);
        if (confMap == null) {
            return false;
        }
        String state = confMap.get(DEVICE_STATE);
        return state != null && DEPLOYED_STATE.equalsIgnoreCase(state);
    }

    public static boolean isValidationEligibleDevice(Device device) {
        return isMonitoredDevice(device) && isDeployedState(device);
    }
}
