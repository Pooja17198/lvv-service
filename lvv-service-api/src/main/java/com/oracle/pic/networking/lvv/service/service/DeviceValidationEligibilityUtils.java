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
    private static final String ROLE_PDU = "pdu";
    private static final String ROLE_NVSWITCH = "nvswitch";
    private static final String ROLE_COMPUTE = "compute";

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

    public static boolean isDisplayEligibleDevice(Device device) {
        if (device == null) {
            return false;
        }

        if (hasExcludedRole(device)) {
            return false;
        }

        return !isComputeChildDevice(device);
    }

    static boolean hasExcludedRole(Device device) {
        String role = normalize(device.getRole());
        return ROLE_PDU.equals(role) || ROLE_NVSWITCH.equals(role);
    }

    static boolean isComputeChildDevice(Device device) {
        if (device == null) {
            return false;
        }

        String role = normalize(device.getRole());
        if (ROLE_COMPUTE.equals(role)) {
            return false;
        }

        String name = device.getName();
        if (name == null || name.isBlank()) {
            return false;
        }

        return name.contains("-compute") && !name.matches(".*-compute\\d+$");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
    }
}
