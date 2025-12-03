package com.oracle.pic.networking.lvv.service.resources;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.identity.authentication.Principal;
import com.oracle.pic.identity.authorization.sdk.AuthorizationRequest;
import com.oracle.pic.networking.lvv.service.model.RegionObject;
import com.oracle.pic.networking.lvv.service.service.RegionsService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class RegionsResourceTest {

    @Mock RegionsService regionsService;
    @Mock MetricsScope metricsScope;
    @Mock Principal principal;
    @Mock AuthorizationRequest authorizationRequest;

    RegionsResource regionsResource;

    @BeforeEach
    void setup() {
        regionsResource = new RegionsResource(regionsService);
    }

    @Test
    void testGetAllRegionsList_happyPath() {
        String realm = "oc1";
        String opcRequestId = "req-123";
        List<RegionObject> mockRegions =
                List.of(mock(RegionObject.class), mock(RegionObject.class));
        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            when(regionsService.getAllRegionsList(realm, metricsScope)).thenReturn(mockRegions);
            when(metricsScope.recordSuccess()).thenReturn(metricsScope);

            List<RegionObject> result =
                    regionsResource.getAllRegionsList(
                            realm, opcRequestId, principal, authorizationRequest);

            assertEquals(mockRegions, result);
            verify(regionsService).getAllRegionsList(eq(realm), eq(metricsScope));
            verify(metricsScope).recordSuccess();
        }
    }

    @Test
    void testGetAllRegionsList_returnsEmptyList_whenServiceReturnsEmpty() {
        String realm = "oc1";
        String opcRequestId = "req-xyz";
        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            when(regionsService.getAllRegionsList(eq(realm), eq(metricsScope)))
                    .thenReturn(List.of());
            when(metricsScope.recordSuccess()).thenReturn(metricsScope);

            List<RegionObject> result =
                    regionsResource.getAllRegionsList(
                            realm, opcRequestId, principal, authorizationRequest);

            assertTrue(result.isEmpty());
            verify(regionsService).getAllRegionsList(eq(realm), eq(metricsScope));
            verify(metricsScope).recordSuccess();
        }
    }

    @Test
    void testGetAllRegionsList_realmEmpty_emitsMetricReturnsEmpty() {
        String realm = "";
        String opcRequestId = "req-abc";
        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);

            List<RegionObject> result =
                    regionsResource.getAllRegionsList(
                            realm, opcRequestId, principal, authorizationRequest);

            assertTrue(result.isEmpty());
            verify(metricsScope).emit(contains("RealmEmpty"), eq(1.0));
            verify(metricsScope, never()).recordSuccess();
            verifyNoInteractions(regionsService);
        }
    }

    @Test
    void testGetAllRegionsList_regionsServiceThrows_propagates() {
        String realm = "oc1";
        String opcRequestId = "req-ex";
        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            when(regionsService.getAllRegionsList(eq(realm), eq(metricsScope)))
                    .thenThrow(new RuntimeException("fail!"));

            assertThrows(
                    RuntimeException.class,
                    () ->
                            regionsResource.getAllRegionsList(
                                    realm, opcRequestId, principal, authorizationRequest));
        }
    }
}
