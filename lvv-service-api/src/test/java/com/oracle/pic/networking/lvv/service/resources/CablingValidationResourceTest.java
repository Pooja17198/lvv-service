package com.oracle.pic.networking.lvv.service.resources;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.identity.authentication.Principal;
import com.oracle.pic.identity.authorization.sdk.AuthorizationRequest;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import com.oracle.pic.networking.lvv.service.kiev.JobStatus;
import com.oracle.pic.networking.lvv.service.kiev.ValidationFailureResultDao;
import com.oracle.pic.networking.lvv.service.model.DeviceValidationStatus;
import com.oracle.pic.networking.lvv.service.service.CablingValidationService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CablingValidationResourceTest {

    @Mock CablingValidationService cablingValidationService;
    @Mock ValidationFailureResultDao validationFailureResultDao;
    @Mock ResourceModelTransformer resourceModelTransformer;
    @Mock MetricsScope metricsScope;
    @Mock Principal principal;
    @Mock AuthorizationRequest authorizationRequest;

    CablingValidationResource resource;

    final String region = "us-phx-1";
    final String building = "bldg";
    final String rackSerialNumber = "rack001";
    final String rackNumber = "1234";
    final String opcRequestId = "req-abc";
    final List<String> deviceNames = List.of("sw1", "sw2");

    @BeforeEach
    void setup() {
        resource =
                new CablingValidationResource(
                        cablingValidationService,
                        validationFailureResultDao,
                        resourceModelTransformer);
    }

    @Test
    void testValidateCables_success_invokesService_andRecordsSuccess() {
        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            // allow chaining
            doReturn(metricsScope).when(metricsScope).emit(anyString(), anyDouble());
            doReturn(metricsScope).when(metricsScope).withDimension(anyString(), anyString());
            doReturn(metricsScope).when(metricsScope).recordSuccess();

            resource.validateCables(
                    region,
                    building,
                    rackSerialNumber,
                    rackNumber,
                    deviceNames,
                    opcRequestId,
                    principal,
                    authorizationRequest);

            verify(cablingValidationService)
                    .validateCablingTasks(
                            eq(region),
                            eq(building),
                            eq(rackSerialNumber),
                            eq(rackNumber),
                            eq(deviceNames),
                            eq(metricsScope));
            verify(metricsScope).recordSuccess();
        }
    }

    @Test
    void testValidateCables_throwsOnMissingParams_andEmitsMetric() {
        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            doReturn(metricsScope).when(metricsScope).emit(anyString(), anyDouble());

            // Blank building
            RenderableException ex1 =
                    assertThrows(
                            RenderableException.class,
                            () ->
                                    resource.validateCables(
                                            region,
                                            "",
                                            rackSerialNumber,
                                            rackNumber,
                                            deviceNames,
                                            opcRequestId,
                                            principal,
                                            authorizationRequest));
            assertTrue(ex1.getMessage().contains("building"));

            // Blank rackSerialNumber
            RenderableException ex2 =
                    assertThrows(
                            RenderableException.class,
                            () ->
                                    resource.validateCables(
                                            region,
                                            building,
                                            "",
                                            rackNumber,
                                            deviceNames,
                                            opcRequestId,
                                            principal,
                                            authorizationRequest));
            assertTrue(ex2.getMessage().contains("rackSerialNumber"));

            // Blank rackNumber
            RenderableException ex3 =
                    assertThrows(
                            RenderableException.class,
                            () ->
                                    resource.validateCables(
                                            region,
                                            building,
                                            rackSerialNumber,
                                            "",
                                            deviceNames,
                                            opcRequestId,
                                            principal,
                                            authorizationRequest));
            assertTrue(ex3.getMessage().contains("rackNumber"));

            // Blank region
            RenderableException ex4 =
                    assertThrows(
                            RenderableException.class,
                            () ->
                                    resource.validateCables(
                                            "",
                                            building,
                                            rackSerialNumber,
                                            rackNumber,
                                            deviceNames,
                                            opcRequestId,
                                            principal,
                                            authorizationRequest));
            assertTrue(ex4.getMessage().contains("regionName"));

            verify(metricsScope, atLeastOnce())
                    .emit(eq(MetricNames.ValidateCables.MissingParameters.name()), eq(1.0));
        }
    }

    @Test
    void testGetValidationFailures_throwsOnNullRackSerial() {
        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            doReturn(metricsScope).when(metricsScope).emit(anyString(), anyDouble());

            RenderableException ex =
                    assertThrows(
                            RenderableException.class,
                            () ->
                                    resource.getValidationFailures(
                                            region,
                                            null,
                                            opcRequestId,
                                            principal,
                                            authorizationRequest));
            assertEquals("Rack Serial cannot be empty", ex.getMessage());
            verify(metricsScope)
                    .emit(eq(MetricNames.GetValidationResults.RackSerialNull.name()), eq(1.0));
        }
    }

    @Test
    void testGetValidationFailures_success_emitsMetrics_andRecordsSuccess() {
        String rackSerial = "rack001";
        Object expected = List.of("a", "b");

        when(cablingValidationService.getValidationFailuresByRack(rackSerial)).thenReturn(expected);

        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            doReturn(metricsScope).when(metricsScope).emit(anyString(), anyDouble());
            doReturn(metricsScope).when(metricsScope).withDimension(anyString(), anyString());
            doReturn(metricsScope).when(metricsScope).recordSuccess();

            Object out =
                    resource.getValidationFailures(
                            region, rackSerial, opcRequestId, principal, authorizationRequest);
            assertEquals(expected, out);

            verify(cablingValidationService).getValidationFailuresByRack(rackSerial);
            verify(metricsScope)
                    .emit(eq(MetricNames.GetValidationResults.GetValidationResult.name()), eq(1.0));
            verify(metricsScope).recordSuccess();
        }
    }

    @Test
    void testGetValidationFailures_emptyList_returnsEmpty_andRecordsSuccess() {
        String rackSerial = "rack-xyz";
        when(cablingValidationService.getValidationFailuresByRack(rackSerial))
                .thenReturn(List.of());

        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            doReturn(metricsScope).when(metricsScope).emit(anyString(), anyDouble());
            doReturn(metricsScope).when(metricsScope).withDimension(anyString(), anyString());
            doReturn(metricsScope).when(metricsScope).recordSuccess();

            Object out =
                    resource.getValidationFailures(
                            region, rackSerial, opcRequestId, principal, authorizationRequest);
            assertNotNull(out);
            assertTrue(((List<?>) out).isEmpty());

            verify(metricsScope).recordSuccess();
        }
    }

    @Test
    void testGetValidationJobStatus_nullRackSerial_throwsAndEmitsMetric() {
        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            doReturn(metricsScope).when(metricsScope).emit(anyString(), anyDouble());

            RenderableException ex =
                    assertThrows(
                            RenderableException.class,
                            () ->
                                    resource.getValidationJobStatus(
                                            region,
                                            null,
                                            rackNumber,
                                            Boolean.TRUE,
                                            opcRequestId,
                                            principal,
                                            authorizationRequest));
            assertTrue(ex.getMessage().contains("Rack serial cannot be empty"));
            verify(metricsScope)
                    .emit(eq(MetricNames.GetValidationJobStatus.RackSerialNull.name()), eq(1.0));
        }
    }

    @Test
    void testGetValidationJobStatus_emptyRackNumber_throwsAndEmitsMetric() {
        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            doReturn(metricsScope).when(metricsScope).emit(anyString(), anyDouble());

            RenderableException ex =
                    assertThrows(
                            RenderableException.class,
                            () ->
                                    resource.getValidationJobStatus(
                                            region,
                                            rackSerialNumber,
                                            "",
                                            Boolean.FALSE,
                                            opcRequestId,
                                            principal,
                                            authorizationRequest));
            assertTrue(ex.getMessage().contains("Rack number cannot be empty"));
            verify(metricsScope)
                    .emit(eq(MetricNames.GetValidationJobStatus.RackNumberNull.name()), eq(1.0));
        }
    }

    @Test
    void testGetValidationJobStatus_success_returnsTransformedList_andRecordsSuccess() {
        Map<String, JobStatus> jobStatusMap =
                Map.of("sw1", JobStatus.IN_PROGRESS, "sw2", JobStatus.COMPLETED);
        DeviceValidationStatus d1 = mock(DeviceValidationStatus.class);
        DeviceValidationStatus d2 = mock(DeviceValidationStatus.class);
        List<DeviceValidationStatus> transformed = List.of(d1, d2);

        when(cablingValidationService.getValidationJobStatus(
                        any(MetricsScope.class),
                        eq(region),
                        eq(rackSerialNumber),
                        eq(rackNumber),
                        eq(Boolean.TRUE)))
                .thenReturn(jobStatusMap);
        when(resourceModelTransformer.toModel(jobStatusMap)).thenReturn(transformed);

        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            doReturn(metricsScope).when(metricsScope).emit(anyString(), anyDouble());
            doReturn(metricsScope).when(metricsScope).withDimension(anyString(), anyString());
            doReturn(metricsScope).when(metricsScope).recordSuccess();

            List<DeviceValidationStatus> result =
                    resource.getValidationJobStatus(
                            region,
                            rackSerialNumber,
                            rackNumber,
                            Boolean.TRUE,
                            opcRequestId,
                            principal,
                            authorizationRequest);

            assertEquals(transformed, result);
            verify(cablingValidationService)
                    .getValidationJobStatus(
                            eq(metricsScope),
                            eq(region),
                            eq(rackSerialNumber),
                            eq(rackNumber),
                            eq(Boolean.TRUE));
            verify(metricsScope)
                    .emit(eq(MetricNames.GetValidationJobStatus.GetJobStatus.name()), eq(1.0));
            verify(metricsScope).recordSuccess();
        }
    }

    @Test
    void testValidateCables_serviceThrows_propagates_andNoSuccessRecorded() {
        doThrow(
                        new RenderableException(
                                com.oracle.pic.commons.exceptions.server.ErrorCode.InternalError,
                                "boom"))
                .when(cablingValidationService)
                .validateCablingTasks(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyList(),
                        any(MetricsScope.class));

        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            doReturn(metricsScope).when(metricsScope).emit(anyString(), anyDouble());
            doReturn(metricsScope).when(metricsScope).withDimension(anyString(), anyString());

            assertThrows(
                    RenderableException.class,
                    () ->
                            resource.validateCables(
                                    region,
                                    building,
                                    rackSerialNumber,
                                    rackNumber,
                                    deviceNames,
                                    opcRequestId,
                                    principal,
                                    authorizationRequest));

            verify(metricsScope, never()).recordSuccess();
        }
    }

    @Test
    void testGetValidationFailures_regionPhoenix_mappedToR2() {
        String rackSerial = "rack001";
        when(cablingValidationService.getValidationFailuresByRack(rackSerial))
                .thenReturn(List.of());

        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            doReturn(metricsScope).when(metricsScope).emit(anyString(), anyDouble());
            doReturn(metricsScope).when(metricsScope).withDimension(anyString(), anyString());
            doReturn(metricsScope).when(metricsScope).recordSuccess();

            Object out =
                    resource.getValidationFailures(
                            "us-phoenix-1",
                            rackSerial,
                            opcRequestId,
                            principal,
                            authorizationRequest);
            assertNotNull(out);
            verify(metricsScope).withDimension(eq("region"), eq("r2"));
            verify(metricsScope).recordSuccess();
        }
    }

    @Test
    void testGetValidationFailures_nullRegion_setsNullDimension_andRecordsSuccess() {
        String rackSerial = "rack001";
        when(cablingValidationService.getValidationFailuresByRack(rackSerial))
                .thenReturn(List.of());

        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            doReturn(metricsScope).when(metricsScope).emit(anyString(), anyDouble());
            // allow null value for region dimension
            doReturn(metricsScope).when(metricsScope).withDimension(anyString(), any());
            doReturn(metricsScope).when(metricsScope).recordSuccess();

            Object out =
                    resource.getValidationFailures(
                            null, rackSerial, opcRequestId, principal, authorizationRequest);
            assertNotNull(out);
            verify(metricsScope).withDimension(eq("region"), isNull());
            verify(metricsScope).recordSuccess();
        }
    }

    @Test
    void testGetValidationFailures_serviceReturnsNull_returnsNull_andRecordsSuccess() {
        String rackSerial = "rack001";
        when(cablingValidationService.getValidationFailuresByRack(rackSerial)).thenReturn(null);

        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            doReturn(metricsScope).when(metricsScope).emit(anyString(), anyDouble());
            doReturn(metricsScope).when(metricsScope).withDimension(anyString(), anyString());
            doReturn(metricsScope).when(metricsScope).recordSuccess();

            Object out =
                    resource.getValidationFailures(
                            region, rackSerial, opcRequestId, principal, authorizationRequest);
            assertNull(out);
            verify(metricsScope).recordSuccess();
        }
    }

    @Test
    void testGetValidationJobStatus_regionPhoenix_mappedToR2() {
        Map<String, JobStatus> jobStatusMap = Map.of("sw1", JobStatus.IN_PROGRESS);
        List<DeviceValidationStatus> transformed = List.of(mock(DeviceValidationStatus.class));
        when(cablingValidationService.getValidationJobStatus(
                        any(MetricsScope.class),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyBoolean()))
                .thenReturn(jobStatusMap);
        when(resourceModelTransformer.toModel(jobStatusMap)).thenReturn(transformed);

        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            doReturn(metricsScope).when(metricsScope).emit(anyString(), anyDouble());
            doReturn(metricsScope).when(metricsScope).withDimension(anyString(), anyString());
            doReturn(metricsScope).when(metricsScope).recordSuccess();

            List<DeviceValidationStatus> out =
                    resource.getValidationJobStatus(
                            "us-phoenix-1",
                            rackSerialNumber,
                            rackNumber,
                            Boolean.TRUE,
                            opcRequestId,
                            principal,
                            authorizationRequest);
            assertEquals(transformed, out);
            verify(metricsScope).withDimension(eq("region"), eq("r2"));
            verify(metricsScope).recordSuccess();
        }
    }

    @Test
    void testGetValidationJobStatus_lastAttemptFalse_passedThrough() {
        Map<String, JobStatus> jobStatusMap = Map.of();
        List<DeviceValidationStatus> transformed = List.of();
        when(cablingValidationService.getValidationJobStatus(
                        any(MetricsScope.class),
                        eq(region),
                        eq(rackSerialNumber),
                        eq(rackNumber),
                        eq(Boolean.FALSE)))
                .thenReturn(jobStatusMap);
        when(resourceModelTransformer.toModel(jobStatusMap)).thenReturn(transformed);

        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            doReturn(metricsScope).when(metricsScope).emit(anyString(), anyDouble());
            doReturn(metricsScope).when(metricsScope).withDimension(anyString(), anyString());
            doReturn(metricsScope).when(metricsScope).recordSuccess();

            List<DeviceValidationStatus> out =
                    resource.getValidationJobStatus(
                            region,
                            rackSerialNumber,
                            rackNumber,
                            Boolean.FALSE,
                            opcRequestId,
                            principal,
                            authorizationRequest);
            assertEquals(transformed, out);
            verify(cablingValidationService)
                    .getValidationJobStatus(
                            eq(metricsScope),
                            eq(region),
                            eq(rackSerialNumber),
                            eq(rackNumber),
                            eq(Boolean.FALSE));
            verify(metricsScope).recordSuccess();
        }
    }

    @Test
    void testDownloadValidationFailures_noop_doesNotThrow() {
        resource.downloadValidationFailures(
                "rack001", region, opcRequestId, principal, authorizationRequest);
    }
}
