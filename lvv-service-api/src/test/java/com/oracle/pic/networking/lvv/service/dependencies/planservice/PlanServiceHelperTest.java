package com.oracle.pic.networking.lvv.service.dependencies.planservice;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.networking.autonet.plan.service.PlanServiceVClient;
import com.oracle.pic.networking.autonet.plan.service.model.Device;
import com.oracle.pic.networking.autonet.plan.service.requests.GetDevicesByRackRequest;
import com.oracle.pic.networking.autonet.plan.service.responses.GetDevicesByRackResponse;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import com.oracle.pic.networking.lvv.service.utils.RetryHelper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.*;

class PlanServiceHelperTest {
    @Mock PlanServiceClient planServiceClient;
    @Mock PlanServiceVClient planServiceVClient;
    @Mock MetricsScope metricsScope;
    @Mock GetDevicesByRackResponse getDevicesByRackResponse;

    PlanServiceHelper helper;

    final String rackNumber = "42";
    final String building = "HQ";
    final String region = "us-region";

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        helper = PlanServiceHelper.builder().planServiceClient(planServiceClient).build();
        // Avoid emit(Enum,String) overload ambiguity
        doReturn(metricsScope).when(metricsScope).emit(anyString(), anyDouble());
        doReturn(metricsScope).when(metricsScope).emit(any(Enum.class), anyDouble());
        when(metricsScope.withDimension(anyString(), anyString())).thenReturn(metricsScope);
        when(metricsScope.recordSuccess()).thenReturn(metricsScope);
    }

    private Device mkDevice(String name, boolean monitored, String deviceStateVal) {
        Device d = mock(Device.class);
        when(d.getName()).thenReturn(name);
        Map<String, Object> cfg =
                monitored
                        ? new HashMap<>(Map.of("monitoring.interfaces", List.of("Eth0/1")))
                        : new HashMap<>();
        when(d.getConfigAttributes()).thenReturn(cfg);

        Map<String, String> conf = new HashMap<>();
        if (deviceStateVal != null) {
            conf.put("device.state", deviceStateVal);
        }
        Map<String, Map<String, String>> state = new HashMap<>();
        state.put("conf", conf);
        when(d.getState()).thenReturn(state);

        return d;
    }

    @Test
    void testGetDeviceListInRack_success_filtersMonitoredAndDeployedOnly() throws Exception {
        Device d1 = mkDevice("dev-1", true, "deployed"); // include
        Device d2 = mkDevice("dev-2", true, "provisioning"); // exclude (state not deployed)
        Device d3 = mkDevice("dev-3", false, "deployed"); // exclude (not monitored)
        Device d4 = mkDevice("dev-4", true, "deployed"); // include

        when(planServiceClient.getPlanServiceVClient(region)).thenReturn(planServiceVClient);
        when(planServiceVClient.getDevicesByRack(any(GetDevicesByRackRequest.class)))
                .thenReturn(getDevicesByRackResponse);
        when(getDevicesByRackResponse.getItems()).thenReturn(Arrays.asList(d1, d2, d3, d4));

        try (MockedStatic<RetryHelper> retryStatic = mockStatic(RetryHelper.class)) {
            RetryHelper mockRetryHelper = mock(RetryHelper.class);
            when(mockRetryHelper.run()).thenReturn(getDevicesByRackResponse);
            retryStatic
                    .when(() -> RetryHelper.newRetryHelper(any(), anyInt(), any()))
                    .thenReturn(mockRetryHelper);

            List<Device> result =
                    helper.getDeviceListInRack(rackNumber, building, region, metricsScope);

            assertEquals(List.of(d1, d4), result);
            verify(planServiceClient).getPlanServiceVClient(region);
            verify(mockRetryHelper).run();
            verify(metricsScope).emit(eq(MetricNames.RackDetails.FetchDevices.name()), eq(1.0));
            verify(metricsScope, never())
                    .emit(eq(MetricNames.RackDetails.FetchDevicesFailed.name()), anyDouble());
        }
    }

    @Test
    void testGetDeviceListInRack_devicesListIsNull_returnsEmptyList() throws Exception {
        when(planServiceClient.getPlanServiceVClient(region)).thenReturn(planServiceVClient);
        when(planServiceVClient.getDevicesByRack(any(GetDevicesByRackRequest.class)))
                .thenReturn(getDevicesByRackResponse);
        when(getDevicesByRackResponse.getItems()).thenReturn(null);

        try (MockedStatic<RetryHelper> retryStatic = mockStatic(RetryHelper.class)) {
            RetryHelper mockRetryHelper = mock(RetryHelper.class);
            when(mockRetryHelper.run()).thenReturn(getDevicesByRackResponse);
            retryStatic
                    .when(() -> RetryHelper.newRetryHelper(any(), anyInt(), any()))
                    .thenReturn(mockRetryHelper);

            List<Device> result =
                    helper.getDeviceListInRack(rackNumber, building, region, metricsScope);

            assertNotNull(result);
            assertTrue(result.isEmpty());
            verify(metricsScope).emit(eq(MetricNames.RackDetails.FetchDevices.name()), eq(1.0));
            verify(metricsScope, never())
                    .emit(eq(MetricNames.RackDetails.FetchDevicesFailed.name()), anyDouble());
        }
    }

    @Test
    void testGetDeviceListInRack_exceptionInDependency_returnsEmptyAndEmitsFailureMetric() {
        when(planServiceClient.getPlanServiceVClient(region))
                .thenThrow(new RuntimeException("network"));

        try (MockedStatic<RetryHelper> retryStatic = mockStatic(RetryHelper.class)) {
            List<Device> result =
                    helper.getDeviceListInRack(rackNumber, building, region, metricsScope);

            assertNotNull(result);
            assertTrue(result.isEmpty());
            // Since exception happened before success metric emission, only failure metric should
            // be emitted
            verify(metricsScope)
                    .emit(eq(MetricNames.RackDetails.FetchDevicesFailed.name()), eq(1.0));
            verify(metricsScope, never())
                    .emit(eq(MetricNames.RackDetails.FetchDevices.name()), anyDouble());
        }
    }

    @Test
    void testGetDeviceListInRack_retryHelperThrows_returnsEmptyAndEmitsFailureMetric()
            throws Exception {
        when(planServiceClient.getPlanServiceVClient(region)).thenReturn(planServiceVClient);

        try (MockedStatic<RetryHelper> retryStatic = mockStatic(RetryHelper.class)) {
            RetryHelper mockRetryHelper = mock(RetryHelper.class);
            when(mockRetryHelper.run()).thenThrow(new RuntimeException("plan down"));
            retryStatic
                    .when(() -> RetryHelper.newRetryHelper(any(), anyInt(), any()))
                    .thenReturn(mockRetryHelper);

            List<Device> result =
                    helper.getDeviceListInRack(rackNumber, building, region, metricsScope);

            assertNotNull(result);
            assertTrue(result.isEmpty());
            verify(metricsScope).emit(eq(MetricNames.RackDetails.FetchDevices.name()), eq(1.0));
            verify(metricsScope)
                    .emit(eq(MetricNames.RackDetails.FetchDevicesFailed.name()), eq(1.0));
        }
    }

    @Test
    void
            testGetDeviceListInRack_nullConfigAttributes_inDevice_caughtAndReturnsEmpty_emitsBothMetrics()
                    throws Exception {
        Device bad = mkDevice("bad", true, "deployed");
        // Force null configAttributes to trigger Preconditions in isMonitoredDevice
        when(bad.getConfigAttributes()).thenReturn(null);

        when(planServiceClient.getPlanServiceVClient(region)).thenReturn(planServiceVClient);
        when(planServiceVClient.getDevicesByRack(any(GetDevicesByRackRequest.class)))
                .thenReturn(getDevicesByRackResponse);
        when(getDevicesByRackResponse.getItems()).thenReturn(List.of(bad));

        try (MockedStatic<RetryHelper> retryStatic = mockStatic(RetryHelper.class)) {
            RetryHelper mockRetryHelper = mock(RetryHelper.class);
            when(mockRetryHelper.run()).thenReturn(getDevicesByRackResponse);
            retryStatic
                    .when(() -> RetryHelper.newRetryHelper(any(), anyInt(), any()))
                    .thenReturn(mockRetryHelper);

            List<Device> result =
                    helper.getDeviceListInRack(rackNumber, building, region, metricsScope);

            assertNotNull(result);
            assertTrue(result.isEmpty());
            verify(metricsScope).emit(eq(MetricNames.RackDetails.FetchDevices.name()), eq(1.0));
            verify(metricsScope)
                    .emit(eq(MetricNames.RackDetails.FetchDevicesFailed.name()), eq(1.0));
        }
    }

    @Test
    void testGetDeviceListInRack_nullState_inDevice_caughtAndReturnsEmpty_emitsBothMetrics()
            throws Exception {
        Device bad = mkDevice("bad", true, "deployed");
        when(bad.getState()).thenReturn(null); // trigger Preconditions in isMonitoredDevice

        when(planServiceClient.getPlanServiceVClient(region)).thenReturn(planServiceVClient);
        when(planServiceVClient.getDevicesByRack(any(GetDevicesByRackRequest.class)))
                .thenReturn(getDevicesByRackResponse);
        when(getDevicesByRackResponse.getItems()).thenReturn(List.of(bad));

        try (MockedStatic<RetryHelper> retryStatic = mockStatic(RetryHelper.class)) {
            RetryHelper mockRetryHelper = mock(RetryHelper.class);
            when(mockRetryHelper.run()).thenReturn(getDevicesByRackResponse);
            retryStatic
                    .when(() -> RetryHelper.newRetryHelper(any(), anyInt(), any()))
                    .thenReturn(mockRetryHelper);

            List<Device> result =
                    helper.getDeviceListInRack(rackNumber, building, region, metricsScope);

            assertNotNull(result);
            assertTrue(result.isEmpty());
            verify(metricsScope).emit(eq(MetricNames.RackDetails.FetchDevices.name()), eq(1.0));
            verify(metricsScope)
                    .emit(eq(MetricNames.RackDetails.FetchDevicesFailed.name()), eq(1.0));
        }
    }

    @Test
    void testGetDeviceListInRack_missingConfOrDeviceState_caughtAndReturnsEmpty_emitsBothMetrics()
            throws Exception {
        // Missing 'conf' key
        Device missingConf = mkDevice("missing-conf", true, null);
        Map<String, Map<String, String>> stateNoConf = new HashMap<>();
        when(missingConf.getState()).thenReturn(stateNoConf);

        // Missing 'device.state' under conf
        Device missingState = mkDevice("missing-state", true, null);
        Map<String, String> conf = new HashMap<>();
        Map<String, Map<String, String>> state = new HashMap<>();
        state.put("conf", conf);
        when(missingState.getState()).thenReturn(state);

        when(planServiceClient.getPlanServiceVClient(region)).thenReturn(planServiceVClient);
        when(planServiceVClient.getDevicesByRack(any(GetDevicesByRackRequest.class)))
                .thenReturn(getDevicesByRackResponse);
        when(getDevicesByRackResponse.getItems()).thenReturn(List.of(missingConf, missingState));

        try (MockedStatic<RetryHelper> retryStatic = mockStatic(RetryHelper.class)) {
            RetryHelper mockRetryHelper = mock(RetryHelper.class);
            when(mockRetryHelper.run()).thenReturn(getDevicesByRackResponse);
            retryStatic
                    .when(() -> RetryHelper.newRetryHelper(any(), anyInt(), any()))
                    .thenReturn(mockRetryHelper);

            List<Device> result =
                    helper.getDeviceListInRack(rackNumber, building, region, metricsScope);

            assertNotNull(result);
            assertTrue(result.isEmpty());
            verify(metricsScope).emit(eq(MetricNames.RackDetails.FetchDevices.name()), eq(1.0));
            verify(metricsScope)
                    .emit(eq(MetricNames.RackDetails.FetchDevicesFailed.name()), eq(1.0));
        }
    }
}
