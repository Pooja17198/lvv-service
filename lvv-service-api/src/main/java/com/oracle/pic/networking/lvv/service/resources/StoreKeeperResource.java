package com.oracle.pic.networking.lvv.service.resources;

import com.oracle.pic.identity.authentication.Principal;
import com.oracle.pic.identity.authorization.sdk.AuthorizationRequest;
import com.oracle.pic.networking.lvv.service.api.AbstractStorekeeperResource;
import com.oracle.pic.networking.lvv.service.model.Block;
import com.oracle.pic.networking.lvv.service.model.Building;
import com.oracle.pic.networking.lvv.service.model.LvvAsset;
import com.oracle.pic.networking.lvv.service.model.LvvRack;
import com.oracle.pic.networking.lvv.service.service.StoreKeeperService;
import java.util.List;
import javax.inject.Inject;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ToString
public class StoreKeeperResource extends AbstractStorekeeperResource {

    private final StoreKeeperService storeKeeperService;

    @Inject
    public StoreKeeperResource(StoreKeeperService storeKeeperService) {
        this.storeKeeperService = storeKeeperService;
    }

    @Override
    public List<Building> listBuildings(
            String regionName, Principal principal, AuthorizationRequest authorizationRequest) {
        return this.storeKeeperService.listBuildings(regionName);
    }

    @Override
    public List<LvvRack> listRacks(
            String buildingName,
            String blockName,
            Principal principal,
            AuthorizationRequest authorizationRequest) {
        return this.storeKeeperService.listRacks(blockName, buildingName);
    }

    @Override
    public LvvAsset getAsset(
            String storekeeperId, Principal principal, AuthorizationRequest authorizationRequest) {
        return this.storeKeeperService.getAsset(storekeeperId);
    }

    @Override
    public List<Block> listBlocks(
            String buildingName, Principal principal, AuthorizationRequest authorizationRequest) {
        return this.storeKeeperService.listBlocks(buildingName);
    }
}
