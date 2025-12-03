package com.oracle.pic.networking.lvv.service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.commons.util.Realm;
import com.oracle.pic.commons.util.Region;
import com.oracle.pic.networking.lvv.service.model.RegionObject;
import com.oracle.pic.networking.lvv.service.resources.ResourceModelTransformer;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class RegionsServiceTest {

    @Mock ResourceModelTransformer resourceModelTransformer;
    @Mock MetricsScope metricsScope;

    RegionsService regionsService;

    @BeforeEach
    void setUp() {
        regionsService = new RegionsService(resourceModelTransformer);
    }

    @Test
    void testGetAllRegionsList_returnsRegions() {
        String realmName = "oc1";
        Realm realm = Realm.fromName(realmName);
        Region[] regionArr = Region.getRegions(realm);
        List<RegionObject> modelList = List.of(mock(RegionObject.class));

        when(resourceModelTransformer.toModel(regionArr)).thenReturn(modelList);

        List<RegionObject> result = regionsService.getAllRegionsList(realmName, metricsScope);

        assertEquals(modelList, result);
        verify(resourceModelTransformer, times(1)).toModel(regionArr);
        verifyNoInteractions(metricsScope);
    }

    @Test
    void testGetAllRegionsList_noRegions_emitsMetric() {
        String realmName = "oc1";
        Realm realm = Realm.fromName(realmName);
        Region[] regionArr = Region.getRegions(realm);

        when(resourceModelTransformer.toModel(regionArr)).thenReturn(List.of());

        List<RegionObject> result = regionsService.getAllRegionsList(realmName, metricsScope);

        assertTrue(result.isEmpty());
        verify(metricsScope, times(1)).emit(contains("NoRegionsFound"), eq(1.0));
    }

    @Test
    void testGetAllRegionsList_invalidRealmName_throwsException() {
        String invalidRealmName = "BADREALM";
        // Realm.fromName may throw IllegalArgumentException for unknown names.
        assertThrows(
                Exception.class,
                () -> regionsService.getAllRegionsList(invalidRealmName, metricsScope));
        verifyNoInteractions(resourceModelTransformer);
    }
}
