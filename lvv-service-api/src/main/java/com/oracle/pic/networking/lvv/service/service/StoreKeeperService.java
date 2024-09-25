package com.oracle.pic.networking.lvv.service.service;

import com.google.inject.Inject;
import com.oracle.pic.networking.lvv.service.model.Block;
import com.oracle.pic.networking.lvv.service.model.Building;
import com.oracle.pic.networking.lvv.service.model.LvvAsset;
import com.oracle.pic.networking.lvv.service.model.LvvRack;
import com.oracle.pic.storekeeper.StoreKeeper;
import com.oracle.pic.storekeeper.model.Asset;
import com.oracle.pic.storekeeper.model.RackLocationMap;
import com.oracle.pic.storekeeper.requests.GetAssetRequest;
import com.oracle.pic.storekeeper.requests.ListBlocksRequest;
import com.oracle.pic.storekeeper.requests.ListBuildingsRequest;
import com.oracle.pic.storekeeper.requests.ListRackLocationsMapRequest;
import com.oracle.pic.storekeeper.responses.ListBlocksResponse;
import com.oracle.pic.storekeeper.responses.ListBuildingsResponse;
import com.oracle.pic.storekeeper.responses.ListRackLocationsMapResponse;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StoreKeeperService {
    private final StoreKeeper storeKeeperClient;

    @Inject
    public StoreKeeperService(StoreKeeper storeKeeperClient) {
        this.storeKeeperClient = storeKeeperClient;
    }

    public List<Building> listBuildings(String regionName) {
        List<Building> buildingList = new ArrayList<>();
        ListBuildingsResponse listBuildingsResponse;
        String pageToken = null;
        try {
            do {
                ListBuildingsRequest listBuildingsRequest =
                        ListBuildingsRequest.builder()
                                .regionName(regionName)
                                .details("false")
                                .pageToken(pageToken)
                                .numResults(1000)
                                .build();
                listBuildingsResponse = this.storeKeeperClient.listBuildings(listBuildingsRequest);
                pageToken = listBuildingsResponse.getListBuildings().getNextPageToken();
                listBuildingsResponse
                        .getListBuildings()
                        .getBuildings()
                        .forEach(
                                (item) -> {
                                    Building building =
                                            Building.builder()
                                                    .name(item.getName())
                                                    .canonicalName(item.getCanonicalName())
                                                    .build();
                                    buildingList.add(building);
                                });
            } while (pageToken != null);
        } catch (Exception e) {
            log.error("Exception when listing buildings", e);
        }
        return buildingList;
    }

    public List<Block> listBlocks(String buildingName) {
        List<Block> blockList = new ArrayList<>();
        ListBlocksResponse listBlocksResponse;
        String pageToken = null;
        try {
            do {
                ListBlocksRequest listBlocksRequest =
                        ListBlocksRequest.builder()
                                .buildingName(buildingName)
                                .numResults(1000)
                                .pageToken(pageToken)
                                .build();
                listBlocksResponse = this.storeKeeperClient.listBlocks(listBlocksRequest);
                pageToken = listBlocksResponse.getListBlocks().getNextPageToken();
                listBlocksResponse
                        .getListBlocks()
                        .getBlocks()
                        .forEach(
                                (item) -> {
                                    Block building = Block.builder().name(item.getName()).build();
                                    blockList.add(building);
                                });
            } while (pageToken != null);
        } catch (Exception e) {
            log.error("Exception when listing buildings", e);
        }
        return blockList;
    }

    public List<LvvRack> listRacks(String blockName, String buildingName) {
        List<LvvRack> rackList = new ArrayList<>();
        ListRackLocationsMapResponse rackResponse = null;
        String pageToken = null;
        try {
            do {
                ListRackLocationsMapRequest listRacksRequest =
                        ListRackLocationsMapRequest.builder()
                                .buildingName(buildingName)
                                .numResults(1000)
                                .pageToken(pageToken)
                                .blockName(blockName)
                                .build();
                rackResponse = this.storeKeeperClient.listRackLocationsMap(listRacksRequest);
                pageToken = rackResponse.getListRackLocationsMap().getNextPageToken();
                rackResponse
                        .getListRackLocationsMap()
                        .getRackLocations()
                        .forEach(
                                (rack) -> {
                                    LvvRack lvvRack = this.toLvvRack(rack);
                                    rackList.add(lvvRack);
                                });
            } while (pageToken != null);
        } catch (Exception e) {
            log.error("Exception when listing buildings", e);
        }
        return rackList;
    }

    private LvvRack toLvvRack(RackLocationMap rack) {
        if (rack == null) {
            return null;
        }

        return LvvRack.builder()
                .activationStatus(rack.getActivationStatus())
                .availabilityDomainName(rack.getAvailabilityDomainName())
                .regionName(rack.getRegionName())
                .rackNumber(rack.getRackNumber())
                .ticketNumber(rack.getTicketNumber())
                .storekeeperId(rack.getStorekeeperId())
                .actualRackSerial(rack.getActualRackSerial())
                .rackState(rack.getRackState())
                .powerAllocation(rack.getPowerAllocation())
                .networkPortsAllocation(rack.getNetworkPortsAllocation())
                .platformName(rack.getPlatformName())
                .platformType(rack.getPlatformType())
                .build();
    }

    public LvvAsset getAsset(String skId) {
        Asset asset = null;
        try {
            GetAssetRequest getAssetRequest = GetAssetRequest.builder().storekeeperId(skId).build();
            asset = this.storeKeeperClient.getAsset(getAssetRequest).getAsset();
        } catch (Exception e) {
            log.error("Exception when listing buildings", e);
        }
        return this.toLvvAsset(asset);
    }

    private LvvAsset toLvvAsset(Asset asset) {
        if (asset == null) {
            return null;
        }

        return LvvAsset.builder()
                .regionName(
                        asset.getAvailabilityDomainRoom() == null
                                ? null
                                : asset.getAvailabilityDomainRoom().getRegionName())
                .regionCanonicalName(
                        asset.getAvailabilityDomainRoom() == null
                                ? null
                                : asset.getAvailabilityDomainRoom().getRegionCanonicalName())
                .buildingName(
                        asset.getAvailabilityDomainRoom() == null
                                ? null
                                : asset.getAvailabilityDomainRoom().getBuildingName())
                .buildingCanonicalName(
                        asset.getAvailabilityDomainRoom() == null
                                ? null
                                : asset.getAvailabilityDomainRoom()
                                        .getBuildingCanonicalName()
                                        .toLowerCase())
                .roomCanonicalName(
                        asset.getAvailabilityDomainRoom() == null
                                ? null
                                : asset.getAvailabilityDomainRoom().getRoomCanonicalName())
                .availabilityDomainCanonicalShortCode(
                        asset.getAvailabilityDomainRoom() == null
                                ? null
                                : asset.getAvailabilityDomainRoom()
                                        .getAvailabilityDomainCanonicalShortCode())
                .description(asset.getDescription())
                .partNo(asset.getPartNo())
                .serial(asset.getSerial())
                .type(asset.getType())
                .elevation(asset.getElevation())
                .height(asset.getHeight())
                .state(asset.getState())
                .tag(asset.getTag())
                .salesOrder(asset.getSalesOrder())
                .purchaseOrder(asset.getPurchaseOrder())
                .createdAt(asset.getCreatedAt())
                .updatedAt(asset.getUpdatedAt())
                .version(asset.getVersion())
                .storekeeperId(asset.getStorekeeperId())
                .destroyId(asset.getDestroyId())
                .rackNumber(asset.getRackNumber())
                .slot(asset.getSlot())
                .oracleAssetId(asset.getOracleAssetId())
                .shippedDate(asset.getShippedDate())
                .receivedDate(asset.getReceivedDate())
                .installedDate(asset.getInstalledDate())
                .powerOnDate(asset.getPowerOnDate())
                .availableDate(asset.getAvailableDate())
                .retiredDate(asset.getRetiredDate())
                .reclamationDate(asset.getReclamationDate())
                .oraclePartNo(asset.getOraclePartNo())
                .build();
    }
}
