package com.oracle.pic.networking.lvv.service.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.commons.util.Realm;
import com.oracle.pic.commons.util.Region;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import com.oracle.pic.networking.lvv.service.model.RegionObject;
import com.oracle.pic.networking.lvv.service.resources.ResourceModelTransformer;
import java.util.List;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@ToString
public class RegionsService {

    @NonNull ResourceModelTransformer resourceModelTransformer;

    @Inject
    public RegionsService(ResourceModelTransformer resourceModelTransformer) {
        this.resourceModelTransformer = resourceModelTransformer;
    }

    public List<RegionObject> getAllRegionsList(String realmName, MetricsScope scope) {

        Realm realm = Realm.fromName(realmName);
        log.info("Getting regions for realm {}", realm.getName());
        Region[] regions = Region.getRegions(realm);

        List<RegionObject> regionsList = resourceModelTransformer.toModel(regions);

        if (regionsList.isEmpty()) {
            scope.emit(MetricNames.FetchRegions.NoRegionsFound.name(), 1.0);
        }

        return regionsList;
    }
}
