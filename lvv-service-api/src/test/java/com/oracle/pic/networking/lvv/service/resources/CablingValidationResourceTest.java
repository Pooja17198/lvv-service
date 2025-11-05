// package com.oracle.pic.networking.lvv.service.resources;
//
// import static org.junit.jupiter.api.Assertions.*;
// import static org.mockito.ArgumentMatchers.*;
// import static org.mockito.Mockito.*;
//
// import com.oracle.pic.commons.exceptions.server.RenderableException;
// import com.oracle.pic.commons.metrics.MetricsScope;
// import com.oracle.pic.identity.authentication.Principal;
// import com.oracle.pic.identity.authorization.sdk.AuthorizationRequest;
// import com.oracle.pic.networking.lvv.service.kiev.ValidationFailureResult;
// import com.oracle.pic.networking.lvv.service.kiev.ValidationFailureResultDao;
// import com.oracle.pic.networking.lvv.service.model.ValidationFailureDisplayDTO;
// import com.oracle.pic.networking.lvv.service.service.CablingValidationService;
// import java.util.List;
// import javax.servlet.http.HttpServletResponse;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.ExtendWith;
// import org.mockito.*;
//
// @ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
// class CablingValidationResourceTest {
//
//    @Mock CablingValidationService cablingValidationService;
//    @Mock ValidationFailureResultDao validationFailureResultDao;
//    @Mock ResourceModelTransformer resourceModelTransformer;
//    @Mock MetricsScope metricsScope;
//    @Mock Principal principal;
//    @Mock AuthorizationRequest authorizationRequest;
//    @Mock HttpServletResponse httpServletResponse;
//
//    CablingValidationResource resource;
//
//    final String building = "bldg";
//    final String block = "blk";
//    final String rackSerialNumber = "rack001";
//    final String opcRequestId = "req-abc";
//    final List<String> deviceNames = List.of("sw1", "sw2");
//
//    @BeforeEach
//    void setup() {
//        resource =
//                new CablingValidationResource(
//                        cablingValidationService,
//                        validationFailureResultDao,
//                        resourceModelTransformer);
//    }
//
//    @Test
//    void testValidateCables_delegatesToService() {
//
//        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
//            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
//            when(cablingValidationService.validateCablingTasks(
//                            building, rackSerialNumber, deviceNames, metricsScope))
//                    .thenReturn("job-id");
//            when(metricsScope.withDimension(anyString(), anyString())).thenReturn(metricsScope);
//            String result =
//                    resource.validateCables(
//                            building,
//                            rackSerialNumber,
//                            deviceNames,
//                            opcRequestId,
//                            principal,
//                            authorizationRequest);
//            assertEquals("job-id", result);
//            verify(cablingValidationService)
//                    .validateCablingTasks(building, rackSerialNumber, deviceNames, metricsScope);
//        }
//    }
//
//    @Test
//    void testValidateCables_throwsOnMissingParams() {
//        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
//            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
//            when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);
//
//            // Test blank building
//            RenderableException ex =
//                    assertThrows(
//                            RenderableException.class,
//                            () ->
//                                    resource.validateCables(
//                                            "",
//                                            rackSerialNumber,
//                                            deviceNames,
//                                            opcRequestId,
//                                            principal,
//                                            authorizationRequest));
//            assertTrue(ex.getMessage().contains("building"));
//
//            // Test blank rackSerialNumber
//            ex =
//                    assertThrows(
//                            RenderableException.class,
//                            () ->
//                                    resource.validateCables(
//                                            building,
//                                            "",
//                                            deviceNames,
//                                            opcRequestId,
//                                            principal,
//                                            authorizationRequest));
//            assertTrue(ex.getMessage().contains("rackSerialNumber"));
//        }
//    }
//
//    @Test
//    void testGetValidationFailures_throwsOnNullRackSerial() {
//        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
//            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
//            when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);
//
//            RenderableException ex =
//                    assertThrows(
//                            RenderableException.class,
//                            () ->
//                                    resource.getValidationFailures(
//                                            "iad78",
//                                            null,
//                                            opcRequestId,
//                                            principal,
//                                            authorizationRequest));
//            assertEquals("Rack Serial cannot be empty", ex.getMessage());
//            verify(metricsScope).emit(contains("RackSerialNull"), eq(1.0));
//        }
//    }
//
//    @Test
//    void testGetValidationFailures_success() {
//        String rackSerial = "rack001";
//        ValidationFailureResult vfr1 = mock(ValidationFailureResult.class);
//        ValidationFailureResult vfr2 = mock(ValidationFailureResult.class);
//        List<ValidationFailureResult> results = List.of(vfr1, vfr2);
//
//        ValidationFailureDisplayDTO dto1 = mock(ValidationFailureDisplayDTO.class);
//        ValidationFailureDisplayDTO dto2 = mock(ValidationFailureDisplayDTO.class);
//
//        when(validationFailureResultDao.getValidationFailuresByRack(rackSerial, true))
//                .thenReturn(results);
//        when(resourceModelTransformer.toModel(vfr1)).thenReturn(dto1);
//        when(resourceModelTransformer.toModel(vfr2)).thenReturn(dto2);
//
//        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
//            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
//            when(metricsScope.withDimension(anyString(), anyString())).thenReturn(metricsScope);
//            when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);
//            when(metricsScope.recordSuccess()).thenReturn(metricsScope);
//
//            List<ValidationFailureDisplayDTO> out =
//                    resource.getValidationFailures(
//                            "iad78", rackSerial, opcRequestId, principal, authorizationRequest);
//            assertEquals(List.of(dto1, dto2), out);
//            verify(validationFailureResultDao).getValidationFailuresByRack(rackSerial, true);
//            verify(metricsScope).recordSuccess();
//        }
//    }
//
//    @Test
//    void testGetValidationJobStatus_ThrowsOnNullJobId() {
//        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
//            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
//            when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);
//
//            RenderableException ex =
//                    assertThrows(
//                            RenderableException.class,
//                            () ->
//                                    resource.getValidationJobStatus(
//                                            null,
//                                            "trial",
//                                            "testRackSerial",
//                                            opcRequestId,
//                                            principal,
//                                            authorizationRequest));
//            assertTrue(ex.getMessage().contains("Job ID cannot be empty"));
//            verify(metricsScope).emit(contains("JobIdNull"), eq(1.0));
//        }
//    }
//
//    @Test
//    void testGetValidationJobStatus_Success() {
//        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
//            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
//            when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);
//            when(metricsScope.withDimension(anyString(), anyString())).thenReturn(metricsScope);
//            when(metricsScope.recordSuccess()).thenReturn(metricsScope);
//
//            when(cablingValidationService.getValidationJobStatus(
//                            anyString(), any(), anyString(), anyString()))
//                    .thenReturn("COMPLETED");
//
//            String result =
//                    resource.getValidationJobStatus(
//                            "job123",
//                            "iad68",
//                            "testRackSerial",
//                            opcRequestId,
//                            principal,
//                            authorizationRequest);
//
//            assertEquals("COMPLETED", result);
//            verify(cablingValidationService)
//                    .getValidationJobStatus(
//                            eq("job123"),
//                            eq(metricsScope),
//                            eq("us-ashburn-1"),
//                            eq("testRackSerial"));
//            verify(metricsScope).recordSuccess();
//        }
//    }
// }
