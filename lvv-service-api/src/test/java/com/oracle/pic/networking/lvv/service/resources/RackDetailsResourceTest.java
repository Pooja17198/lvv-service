package com.oracle.pic.networking.lvv.service.resources;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.oracle.pic.commons.exceptions.server.ErrorCode;
import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.identity.authentication.Principal;
import com.oracle.pic.identity.authorization.sdk.AuthorizationRequest;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import com.oracle.pic.networking.lvv.service.model.DeviceDetails;
import com.oracle.pic.networking.lvv.service.service.RackDetailsService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RackDetailsResourceTest {

    @Mock RackDetailsService rackDetailsService;
    @Mock ResourceModelTransformer resourceModelTransformer;
    @Mock MetricsScope metricsScope;
    @Mock Principal principal;
    @Mock AuthorizationRequest authorizationRequest;

    RackDetailsResource resource;

    final String regionName = "us-region-1";
    final String building = "HQ1";
    final String rackSerialNumber = "RSN-001";
    final String rackNumber = "RACK-42";
    final String opcRequestId = "req-1234";

    @BeforeEach
    void setup() {
        resource = new RackDetailsResource(rackDetailsService, resourceModelTransformer);
    }

    @Test
    void listDevicesInRack_success_returnsFromService_andRecordsSuccess() {
        List<DeviceDetails> expected =
                List.of(mock(DeviceDetails.class), mock(DeviceDetails.class));

        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);

            doReturn(metricsScope).when(metricsScope).emit(anyString(), anyDouble());
            doReturn(metricsScope).when(metricsScope).withDimension(anyString(), anyString());
            doReturn(metricsScope).when(metricsScope).recordSuccess();

            when(rackDetailsService.getDeviceDetailsInRack(
                            eq(rackSerialNumber),
                            eq(regionName),
                            eq(rackNumber),
                            eq(building),
                            any(MetricsScope.class)))
                    .thenReturn(expected);

            List<DeviceDetails> result =
                    resource.listDevicesInRack(
                            rackSerialNumber,
                            rackNumber,
                            building,
                            regionName,
                            opcRequestId,
                            principal,
                            authorizationRequest);

            assertEquals(expected, result);
            verify(rackDetailsService)
                    .getDeviceDetailsInRack(
                            eq(rackSerialNumber),
                            eq(regionName),
                            eq(rackNumber),
                            eq(building),
                            eq(metricsScope));
            verify(metricsScope).withDimension(eq("region"), eq(regionName));
            verify(metricsScope).withDimension(eq("building"), eq(building));
            verify(metricsScope).withDimension(eq("rackNumber"), eq(rackNumber));
            verify(metricsScope).recordSuccess();
        }
    }

    @Test
    void listDevicesInRack_emptyResult_returnsEmpty_andRecordsSuccess() {
        List<DeviceDetails> expected = List.of();

        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);

            doReturn(metricsScope).when(metricsScope).emit(anyString(), anyDouble());
            doReturn(metricsScope).when(metricsScope).withDimension(anyString(), anyString());
            doReturn(metricsScope).when(metricsScope).recordSuccess();

            when(rackDetailsService.getDeviceDetailsInRack(
                            eq(rackSerialNumber),
                            eq(regionName),
                            eq(rackNumber),
                            eq(building),
                            any(MetricsScope.class)))
                    .thenReturn(expected);

            List<DeviceDetails> result =
                    resource.listDevicesInRack(
                            rackSerialNumber,
                            rackNumber,
                            building,
                            regionName,
                            opcRequestId,
                            principal,
                            authorizationRequest);

            assertNotNull(result);
            assertTrue(result.isEmpty());
            verify(metricsScope).recordSuccess();
        }
    }

    @Test
    void listDevicesInRack_nullOrBlank_rackSerialNumber_throws_andEmitsMissingParameters() {
        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            doReturn(metricsScope).when(metricsScope).emit(anyString(), anyDouble());

            RenderableException ex1 =
                    assertThrows(
                            RenderableException.class,
                            () ->
                                    resource.listDevicesInRack(
                                            null,
                                            rackNumber,
                                            building,
                                            regionName,
                                            opcRequestId,
                                            principal,
                                            authorizationRequest));
            assertEquals(ErrorCode.MissingParameter, ex1.getErrorCode());
            assertTrue(ex1.getMessage().contains("rackSerialNumber"));
            verify(metricsScope)
                    .emit(eq(MetricNames.RackDetails.MissingParameters.name()), eq(1.0));

            RenderableException ex2 =
                    assertThrows(
                            RenderableException.class,
                            () ->
                                    resource.listDevicesInRack(
                                            "   ",
                                            rackNumber,
                                            building,
                                            regionName,
                                            opcRequestId,
                                            principal,
                                            authorizationRequest));
            assertEquals(ErrorCode.MissingParameter, ex2.getErrorCode());
            assertTrue(ex2.getMessage().contains("rackSerialNumber"));
        }
    }

    @Test
    void listDevicesInRack_nullOrBlank_rackNumber_throws_andEmitsMissingParameters() {
        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            doReturn(metricsScope).when(metricsScope).emit(anyString(), anyDouble());

            RenderableException ex1 =
                    assertThrows(
                            RenderableException.class,
                            () ->
                                    resource.listDevicesInRack(
                                            rackSerialNumber,
                                            null,
                                            building,
                                            regionName,
                                            opcRequestId,
                                            principal,
                                            authorizationRequest));
            assertEquals(ErrorCode.MissingParameter, ex1.getErrorCode());
            assertTrue(ex1.getMessage().contains("rackNumber"));
            verify(metricsScope)
                    .emit(eq(MetricNames.RackDetails.MissingParameters.name()), eq(1.0));

            RenderableException ex2 =
                    assertThrows(
                            RenderableException.class,
                            () ->
                                    resource.listDevicesInRack(
                                            rackSerialNumber,
                                            "",
                                            building,
                                            regionName,
                                            opcRequestId,
                                            principal,
                                            authorizationRequest));
            assertEquals(ErrorCode.MissingParameter, ex2.getErrorCode());
            assertTrue(ex2.getMessage().contains("rackNumber"));
        }
    }

    @Test
    void listDevicesInRack_nullOrBlank_building_throws_andEmitsMissingParameters() {
        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            doReturn(metricsScope).when(metricsScope).emit(anyString(), anyDouble());

            RenderableException ex1 =
                    assertThrows(
                            RenderableException.class,
                            () ->
                                    resource.listDevicesInRack(
                                            rackSerialNumber,
                                            rackNumber,
                                            null,
                                            regionName,
                                            opcRequestId,
                                            principal,
                                            authorizationRequest));
            assertEquals(ErrorCode.MissingParameter, ex1.getErrorCode());
            assertTrue(ex1.getMessage().contains("building"));
            verify(metricsScope)
                    .emit(eq(MetricNames.RackDetails.MissingParameters.name()), eq(1.0));

            RenderableException ex2 =
                    assertThrows(
                            RenderableException.class,
                            () ->
                                    resource.listDevicesInRack(
                                            rackSerialNumber,
                                            rackNumber,
                                            " ",
                                            regionName,
                                            opcRequestId,
                                            principal,
                                            authorizationRequest));
            assertEquals(ErrorCode.MissingParameter, ex2.getErrorCode());
            assertTrue(ex2.getMessage().contains("building"));
        }
    }

    @Test
    void listDevicesInRack_nullOrBlank_regionName_throws_andEmitsMissingParameters() {
        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            doReturn(metricsScope).when(metricsScope).emit(anyString(), anyDouble());

            RenderableException ex1 =
                    assertThrows(
                            RenderableException.class,
                            () ->
                                    resource.listDevicesInRack(
                                            rackSerialNumber,
                                            rackNumber,
                                            building,
                                            null,
                                            opcRequestId,
                                            principal,
                                            authorizationRequest));
            assertEquals(ErrorCode.MissingParameter, ex1.getErrorCode());
            assertTrue(ex1.getMessage().contains("regionName"));
            verify(metricsScope)
                    .emit(eq(MetricNames.RackDetails.MissingParameters.name()), eq(1.0));

            RenderableException ex2 =
                    assertThrows(
                            RenderableException.class,
                            () ->
                                    resource.listDevicesInRack(
                                            rackSerialNumber,
                                            rackNumber,
                                            building,
                                            "   ",
                                            opcRequestId,
                                            principal,
                                            authorizationRequest));
            assertEquals(ErrorCode.MissingParameter, ex2.getErrorCode());
            assertTrue(ex2.getMessage().contains("regionName"));
        }
    }

    @Test
    void listDevicesInRack_allParamsMissing_throws_andEmitsMissingOnce() {
        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            doReturn(metricsScope).when(metricsScope).emit(anyString(), anyDouble());

            RenderableException ex =
                    assertThrows(
                            RenderableException.class,
                            () ->
                                    resource.listDevicesInRack(
                                            "",
                                            "",
                                            "",
                                            "",
                                            opcRequestId,
                                            principal,
                                            authorizationRequest));
            assertEquals(ErrorCode.MissingParameter, ex.getErrorCode());
            assertTrue(ex.getMessage().contains("rackSerialNumber"));
            assertTrue(ex.getMessage().contains("rackNumber"));
            assertTrue(ex.getMessage().contains("building"));
            assertTrue(ex.getMessage().contains("regionName"));
            verify(metricsScope)
                    .emit(eq(MetricNames.RackDetails.MissingParameters.name()), eq(1.0));
            verifyNoInteractions(rackDetailsService);
        }
    }

    @Test
    void listDevicesInRack_serviceThrows_propagates_andNoSuccessRecorded() {
        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);

            doReturn(metricsScope).when(metricsScope).withDimension(anyString(), anyString());

            when(rackDetailsService.getDeviceDetailsInRack(
                            anyString(),
                            anyString(),
                            anyString(),
                            anyString(),
                            any(MetricsScope.class)))
                    .thenThrow(new RuntimeException("backend failure"));

            RuntimeException ex =
                    assertThrows(
                            RuntimeException.class,
                            () ->
                                    resource.listDevicesInRack(
                                            rackSerialNumber,
                                            rackNumber,
                                            building,
                                            regionName,
                                            opcRequestId,
                                            principal,
                                            authorizationRequest));
            assertEquals("backend failure", ex.getMessage());
            verify(rackDetailsService)
                    .getDeviceDetailsInRack(
                            eq(rackSerialNumber),
                            eq(regionName),
                            eq(rackNumber),
                            eq(building),
                            eq(metricsScope));
            verify(metricsScope, never()).recordSuccess();
        }
    }
}
