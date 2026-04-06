package com.oracle.pic.networking.lvv.service.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.oracle.pic.networking.lvv.service.dependencies.ide.IdeClient;
import com.oracle.pic.networking.lvv.service.kiev.PatchPanelEntry;
import com.oracle.pic.networking.lvv.service.kiev.PatchPanelEntryDao;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages patch panel (physical cutsheet) data.
 *
 * <p>On each request: checks Kiev for cached data. If missing or stale (older than {@code
 * STALE_THRESHOLD_MS}), fetches fresh data from the IDE service and stores it in Kiev.
 */
@Slf4j
@Singleton
public class PatchPanelService {

    // Staleness threshold: 24 hours. Staleness check is a placeholder for now;
    // refresh-on-stale logic can be enabled by toggling REFRESH_IF_STALE.
    static final long STALE_THRESHOLD_MS = 24 * 60 * 60 * 1000L;
    static final boolean REFRESH_IF_STALE = false; // enable in a follow-up once agreed

    private final PatchPanelEntryDao dao;
    private final IdeClient ideClient;

    @Inject
    public PatchPanelService(PatchPanelEntryDao dao, IdeClient ideClient) {
        this.dao = dao;
        this.ideClient = ideClient;
    }

    /**
     * Returns all patch panel entries for the rack. Fetches from IDE and caches in Kiev if no
     * cached data exists (or if cache is stale and REFRESH_IF_STALE is enabled).
     */
    public List<PatchPanelEntry> getPatchPanelForRack(
            String buildingName, String rackNumber, String rackSerial) {

        List<PatchPanelEntry> cached = dao.listByRackSerial(rackSerial);

        if (cached.isEmpty()) {
            log.info("No cached patch panel data for rack {}, fetching from IDE", rackSerial);
            return fetchAndStore(buildingName, rackNumber, rackSerial);
        }

        if (REFRESH_IF_STALE && isStale(cached)) {
            log.info("Cached patch panel data for rack {} is stale, refreshing from IDE", rackSerial);
            return fetchAndStore(buildingName, rackNumber, rackSerial);
        }

        log.info("Returning {} cached patch panel entries for rack {}", cached.size(), rackSerial);
        return cached;
    }

    private List<PatchPanelEntry> fetchAndStore(
            String buildingName, String rackNumber, String rackSerial) {

        List<Map<String, Object>> ideItems =
                ideClient.fetchPhysicalCutsheetsForRack(buildingName, rackNumber);

        Timestamp now = Timestamp.from(Instant.now());
        List<PatchPanelEntry> entries = new ArrayList<>(ideItems.size());

        for (Map<String, Object> item : ideItems) {
            String deviceName = stringField(item, "deviceName");
            String devicePort = stringField(item, "devicePort");

            if (deviceName == null || devicePort == null) {
                log.warn("Skipping IDE item with missing deviceName or devicePort: {}", item);
                continue;
            }

            @SuppressWarnings("unchecked")
            List<String> easyMark = item.get("easyMark") instanceof List
                    ? (List<String>) item.get("easyMark")
                    : null;

            entries.add(
                    PatchPanelEntry.builder()
                            .devicePortKey(PatchPanelEntry.buildKey(deviceName, devicePort))
                            .rackSerial(rackSerial)
                            .deviceName(deviceName)
                            .devicePort(devicePort)
                            .buildingName(stringField(item, "buildingName"))
                            .roomName(stringField(item, "roomName"))
                            .rackNumber(stringField(item, "rackNumber"))
                            .easyMark(easyMark)
                            .lastFetchedAt(now)
                            .build());
        }

        log.info("Storing {} patch panel entries for rack {} in Kiev", entries.size(), rackSerial);
        dao.upsertAll(entries);
        return entries;
    }

    private boolean isStale(List<PatchPanelEntry> entries) {
        long now = System.currentTimeMillis();
        return entries.stream()
                .anyMatch(
                        e ->
                                e.getLastFetchedAt() == null
                                        || (now - e.getLastFetchedAt().getTime())
                                                > STALE_THRESHOLD_MS);
    }

    private String stringField(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val instanceof String ? (String) val : null;
    }
}
