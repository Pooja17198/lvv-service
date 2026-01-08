package com.oracle.pic.networking.lvv.service.service;

import com.google.inject.Inject;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.networking.autonet.plan.service.model.Device;
import com.oracle.pic.networking.lvv.service.dependencies.planservice.PlanServiceHelper;
import com.oracle.pic.networking.lvv.service.kiev.JobStatus;
import com.oracle.pic.networking.lvv.service.kiev.NcpJobDetailsDao;
import com.oracle.pic.networking.lvv.service.model.DeviceDetails;
import com.oracle.pic.networking.lvv.service.resources.ResourceModelTransformer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RackDetailsService {

    private final PlanServiceHelper planServiceHelper;
    private final NcpJobDetailsDao ncpJobDetailsDao;
    private final CablingValidationService cablingValidationService;
    private ResourceModelTransformer resourceModelTransformer;

    @Inject
    public RackDetailsService(
            PlanServiceHelper planServiceHelper,
            NcpJobDetailsDao ncpJobDetailsDao,
            CablingValidationService cablingValidationService,
            ResourceModelTransformer resourceModelTransformer) {
        this.planServiceHelper = planServiceHelper;
        this.ncpJobDetailsDao = ncpJobDetailsDao;
        this.cablingValidationService = cablingValidationService;
        this.resourceModelTransformer = resourceModelTransformer;
    }

    private List<Device> listDevicesInRack(
            String regionName, String rackNumber, String building, MetricsScope scope) {

        return planServiceHelper.getDeviceListInRack(rackNumber, building, regionName, scope);
    }

    public List<DeviceDetails> getDeviceDetailsInRack(
            String rackSerialNumber,
            String regionName,
            String rackNumber,
            String building,
            MetricsScope scope) {

        List<Device> devices = listDevicesInRack(regionName, rackNumber, building, scope);

        // Map that stores {deviceName -> NCP Job ID}
        HashMap<String, String> emptyNcpJobs = new HashMap<>();

        // If we get a list of devices, we create job entries for each of that device
        // Else, we create a single job for the entire rack, and set the device name to the rack
        // serial itself
        if (!devices.isEmpty()) {
            for (Device device : devices) {
                emptyNcpJobs.put(device.getName(), "");
            }
        } else {
            emptyNcpJobs.put(rackSerialNumber, "");
        }

        // We add empty job details in the DB for the devices which haven't been added to the DB yet
        ncpJobDetailsDao.addUpdateNcpJobDetails(emptyNcpJobs, rackSerialNumber, scope);

        Map<String, JobStatus> deviceJobStatus =
                cablingValidationService.getValidationJobStatus(
                        scope, regionName, rackSerialNumber, rackNumber, false);

        return resourceModelTransformer.toModel(deviceJobStatus, devices);
    }
}
