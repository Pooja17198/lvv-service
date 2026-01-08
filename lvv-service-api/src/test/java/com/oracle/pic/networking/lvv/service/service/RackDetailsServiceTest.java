package com.oracle.pic.networking.lvv.service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.networking.autonet.plan.service.model.Device;
import com.oracle.pic.networking.lvv.service.dependencies.planservice.PlanServiceHelper;
import com.oracle.pic.networking.lvv.service.kiev.JobStatus;
import com.oracle.pic.networking.lvv.service.kiev.NcpJobDetailsDao;
import com.oracle.pic.networking.lvv.service.model.DeviceDetails;
import com.oracle.pic.networking.lvv.service.resources.ResourceModelTransformer;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RackDetailsServiceTest {

    @Mock PlanServiceHelper planServiceHelper;
    @Mock NcpJobDetailsDao ncpJobDetailsDao;
    @Mock CablingValidationService cablingValidationService;
    @Mock ResourceModelTransformer resourceModelTransformer;
    @Mock MetricsScope metricsScope;

    RackDetailsService service;

    final String region = "us-region";
    final String rackNumber = "RACK1";
    final String building = "B2";
    final String rackSerial = "RSN123";

    @BeforeEach
    void setup() {
        service =
                new RackDetailsService(
                        planServiceHelper,
                        ncpJobDetailsDao,
                        cablingValidationService,
                        resourceModelTransformer);
    }

    private Device mockDeviceWithName(String name) {
        Device device = mock(Device.class);
        when(device.getName()).thenReturn(name);
        return device;
    }

    @Test
    void getDeviceDetailsInRack_devicesPresent_createsJobsForEachDevice_andTransforms() {
        // Arrange
        Device devA = mockDeviceWithName("devA");
        Device devB = mockDeviceWithName("devB");
        List<Device> devices = Arrays.asList(devA, devB);

        when(planServiceHelper.getDeviceListInRack(rackNumber, building, region, metricsScope))
                .thenReturn(devices);

        Map<String, JobStatus> jobStatus = new HashMap<>();
        jobStatus.put("devA", JobStatus.IN_PROGRESS);
        jobStatus.put("devB", JobStatus.COMPLETED);
        when(cablingValidationService.getValidationJobStatus(
                        metricsScope, region, rackSerial, rackNumber, false))
                .thenReturn(jobStatus);

        List<DeviceDetails> expectedDetails = Collections.singletonList(mock(DeviceDetails.class));
        when(resourceModelTransformer.toModel(jobStatus, devices)).thenReturn(expectedDetails);

        ArgumentCaptor<HashMap<String, String>> jobsCaptor = ArgumentCaptor.forClass(HashMap.class);

        // Act
        List<DeviceDetails> result =
                service.getDeviceDetailsInRack(
                        rackSerial, region, rackNumber, building, metricsScope);

        // Assert
        assertNotNull(result);
        assertEquals(expectedDetails, result);

        verify(planServiceHelper).getDeviceListInRack(rackNumber, building, region, metricsScope);

        verify(ncpJobDetailsDao)
                .addUpdateNcpJobDetails(jobsCaptor.capture(), eq(rackSerial), eq(metricsScope));
        Map<String, String> jobsInserted = jobsCaptor.getValue();
        assertEquals(2, jobsInserted.size());
        assertEquals("", jobsInserted.get("devA"));
        assertEquals("", jobsInserted.get("devB"));
        assertFalse(
                jobsInserted.containsKey(rackSerial),
                "rack serial must not be used as a device when devices exist");

        verify(cablingValidationService)
                .getValidationJobStatus(metricsScope, region, rackSerial, rackNumber, false);
        verify(resourceModelTransformer).toModel(jobStatus, devices);
        verifyNoMoreInteractions(resourceModelTransformer, cablingValidationService);
    }

    @Test
    void getDeviceDetailsInRack_noDevices_createsJobForRackSerial_andTransforms() {
        // Arrange
        when(planServiceHelper.getDeviceListInRack(rackNumber, building, region, metricsScope))
                .thenReturn(Collections.emptyList());

        Map<String, JobStatus> jobStatus = new HashMap<>(); // could be empty or anything
        when(cablingValidationService.getValidationJobStatus(
                        metricsScope, region, rackSerial, rackNumber, false))
                .thenReturn(jobStatus);

        List<DeviceDetails> expected = Collections.emptyList();
        when(resourceModelTransformer.toModel(jobStatus, Collections.emptyList()))
                .thenReturn(expected);

        ArgumentCaptor<HashMap<String, String>> jobsCaptor = ArgumentCaptor.forClass(HashMap.class);

        // Act
        List<DeviceDetails> result =
                service.getDeviceDetailsInRack(
                        rackSerial, region, rackNumber, building, metricsScope);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(ncpJobDetailsDao)
                .addUpdateNcpJobDetails(jobsCaptor.capture(), eq(rackSerial), eq(metricsScope));
        Map<String, String> jobsInserted = jobsCaptor.getValue();
        assertEquals(1, jobsInserted.size());
        assertEquals("", jobsInserted.get(rackSerial));

        verify(cablingValidationService)
                .getValidationJobStatus(metricsScope, region, rackSerial, rackNumber, false);
        verify(resourceModelTransformer).toModel(jobStatus, Collections.emptyList());
    }

    @Test
    void getDeviceDetailsInRack_duplicateDeviceNames_deduplicatesJobsMap() {
        // Arrange: two devices with same name
        Device d1 = mockDeviceWithName("dup");
        Device d2 = mockDeviceWithName("dup");
        List<Device> devices = Arrays.asList(d1, d2);

        when(planServiceHelper.getDeviceListInRack(rackNumber, building, region, metricsScope))
                .thenReturn(devices);

        Map<String, JobStatus> jobStatus = new HashMap<>();
        jobStatus.put("dup", JobStatus.IN_PROGRESS);
        when(cablingValidationService.getValidationJobStatus(
                        metricsScope, region, rackSerial, rackNumber, false))
                .thenReturn(jobStatus);

        when(resourceModelTransformer.toModel(jobStatus, devices))
                .thenReturn(Collections.emptyList());

        ArgumentCaptor<HashMap<String, String>> jobsCaptor = ArgumentCaptor.forClass(HashMap.class);

        // Act
        service.getDeviceDetailsInRack(rackSerial, region, rackNumber, building, metricsScope);

        // Assert: only one entry for duplicated name
        verify(ncpJobDetailsDao)
                .addUpdateNcpJobDetails(jobsCaptor.capture(), eq(rackSerial), eq(metricsScope));
        Map<String, String> jobsInserted = jobsCaptor.getValue();
        assertEquals(1, jobsInserted.size());
        assertEquals("", jobsInserted.get("dup"));
    }

    @Test
    void getDeviceDetailsInRack_propagatesException_fromPlanService() {
        // Arrange
        RuntimeException boom = new RuntimeException("plan service down");
        when(planServiceHelper.getDeviceListInRack(rackNumber, building, region, metricsScope))
                .thenThrow(boom);

        // Act + Assert
        RuntimeException thrown =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                service.getDeviceDetailsInRack(
                                        rackSerial, region, rackNumber, building, metricsScope));
        assertSame(boom, thrown);

        verify(planServiceHelper).getDeviceListInRack(rackNumber, building, region, metricsScope);
        verifyNoInteractions(ncpJobDetailsDao, cablingValidationService, resourceModelTransformer);
    }

    @Test
    void getDeviceDetailsInRack_allowsNullRegionRackBuilding_andStillCreatesRackJob() {
        // Arrange: explicitly stub for nulls to ensure pass-through of args
        when(planServiceHelper.getDeviceListInRack(
                        isNull(), isNull(), isNull(), same(metricsScope)))
                .thenReturn(Collections.emptyList());

        Map<String, JobStatus> emptyStatus = Collections.emptyMap();
        when(cablingValidationService.getValidationJobStatus(
                        metricsScope, null, rackSerial, null, false))
                .thenReturn(emptyStatus);

        when(resourceModelTransformer.toModel(emptyStatus, Collections.emptyList()))
                .thenReturn(Collections.emptyList());

        ArgumentCaptor<HashMap<String, String>> jobsCaptor = ArgumentCaptor.forClass(HashMap.class);

        // Act
        List<DeviceDetails> result =
                service.getDeviceDetailsInRack(rackSerial, null, null, null, metricsScope);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(planServiceHelper).getDeviceListInRack(null, null, null, metricsScope);
        verify(ncpJobDetailsDao)
                .addUpdateNcpJobDetails(jobsCaptor.capture(), eq(rackSerial), eq(metricsScope));
        Map<String, String> jobsInserted = jobsCaptor.getValue();
        assertEquals(1, jobsInserted.size());
        assertEquals("", jobsInserted.get(rackSerial));

        verify(cablingValidationService)
                .getValidationJobStatus(metricsScope, null, rackSerial, null, false);
        verify(resourceModelTransformer).toModel(emptyStatus, Collections.emptyList());
    }

    @Test
    void getDeviceDetailsInRack_propagatesException_fromTransformer() {
        // Arrange
        Device dev = mockDeviceWithName("devA");
        List<Device> devices = Collections.singletonList(dev);
        when(planServiceHelper.getDeviceListInRack(rackNumber, building, region, metricsScope))
                .thenReturn(devices);

        Map<String, JobStatus> jobStatus = new HashMap<>();
        jobStatus.put("devA", JobStatus.COMPLETED);
        when(cablingValidationService.getValidationJobStatus(
                        metricsScope, region, rackSerial, rackNumber, false))
                .thenReturn(jobStatus);

        RuntimeException boom = new RuntimeException("transformer failed");
        when(resourceModelTransformer.toModel(jobStatus, devices)).thenThrow(boom);

        // Act + Assert
        RuntimeException thrown =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                service.getDeviceDetailsInRack(
                                        rackSerial, region, rackNumber, building, metricsScope));
        assertSame(boom, thrown);

        verify(planServiceHelper).getDeviceListInRack(rackNumber, building, region, metricsScope);
        verify(cablingValidationService)
                .getValidationJobStatus(metricsScope, region, rackSerial, rackNumber, false);
        verify(resourceModelTransformer).toModel(jobStatus, devices);
    }

    @Test
    void getDeviceDetailsInRack_cablingServiceThrows_afterDbInsert_propagates() {
        when(planServiceHelper.getDeviceListInRack(rackNumber, building, region, metricsScope))
                .thenReturn(Collections.emptyList());

        RuntimeException boom = new RuntimeException("cabling failed");
        when(cablingValidationService.getValidationJobStatus(
                        metricsScope, region, rackSerial, rackNumber, false))
                .thenThrow(boom);

        assertSame(
                boom,
                assertThrows(
                        RuntimeException.class,
                        () ->
                                service.getDeviceDetailsInRack(
                                        rackSerial, region, rackNumber, building, metricsScope)));

        verify(ncpJobDetailsDao)
                .addUpdateNcpJobDetails(
                        Mockito.<HashMap<String, String>>any(), eq(rackSerial), eq(metricsScope));
        verifyNoInteractions(resourceModelTransformer);
    }

    @Test
    void getDeviceDetailsInRack_callsDependenciesInOrder() {
        Device dev = mockDeviceWithName("dev1");
        List<Device> devices = Collections.singletonList(dev);
        when(planServiceHelper.getDeviceListInRack(rackNumber, building, region, metricsScope))
                .thenReturn(devices);

        Map<String, JobStatus> status = new HashMap<>();
        status.put("dev1", JobStatus.IN_PROGRESS);
        when(cablingValidationService.getValidationJobStatus(
                        metricsScope, region, rackSerial, rackNumber, false))
                .thenReturn(status);

        when(resourceModelTransformer.toModel(status, devices)).thenReturn(Collections.emptyList());

        service.getDeviceDetailsInRack(rackSerial, region, rackNumber, building, metricsScope);

        InOrder inOrder =
                inOrder(
                        planServiceHelper,
                        ncpJobDetailsDao,
                        cablingValidationService,
                        resourceModelTransformer);
        inOrder.verify(planServiceHelper)
                .getDeviceListInRack(rackNumber, building, region, metricsScope);
        inOrder.verify(ncpJobDetailsDao)
                .addUpdateNcpJobDetails(
                        Mockito.<HashMap<String, String>>any(), eq(rackSerial), eq(metricsScope));
        inOrder.verify(cablingValidationService)
                .getValidationJobStatus(metricsScope, region, rackSerial, rackNumber, false);
        inOrder.verify(resourceModelTransformer).toModel(status, devices);
        inOrder.verifyNoMoreInteractions();
    }
}
