package com.oracle.pic.networking.lvv.service.kiev;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.oracle.pic.kiev.Transaction;
import com.oracle.pic.kiev.exceptions.CommitConflictException;
import com.oracle.pic.kiev.exceptions.DuplicateKeyException;
import com.oracle.pic.kiev.mapping.Index;
import com.oracle.pic.kiev.mapping.MappedHashBucket;
import com.oracle.pic.kiev.mapping.ScanPage;
import com.oracle.pic.kiev.mapping.token.PaginationTokenSerializer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class PatchPanelEntryDao {

    private static final int DEFAULT_PAGE_SIZE = 1000;

    private final ConfigurationStore<String, PatchPanelEntry> store;
    private final MappedHashBucket<String, PatchPanelEntry> provider;
    private final Index<PatchPanelEntry.RackSerialIndex, PatchPanelEntry> rackSerialIndex;

    @Inject
    public PatchPanelEntryDao(
            @NonNull ConfigurationStore<String, PatchPanelEntry> store,
            @NonNull PaginationTokenSerializer serializer,
            @NonNull MappedHashBucket<String, PatchPanelEntry> provider) {
        this.store = store;
        this.provider = provider;
        this.rackSerialIndex =
                provider.getIndex(
                        PatchPanelEntry.RACK_SERIAL_COLUMN_NAME,
                        PatchPanelEntry.RackSerialIndex.class);
    }

    /**
     * Upserts all patch panel entries for a rack. Existing entries for the same devicePortKey are
     * overwritten; entries not present in the new list remain untouched.
     */
    public void upsertAll(@NonNull List<PatchPanelEntry> entries) {
        for (PatchPanelEntry entry : entries) {
            upsert(entry);
        }
    }

    private void upsert(PatchPanelEntry entry) {
        PatchPanelEntry existing = null;
        try {
            existing = store.getItem(entry.getDevicePortKey());
        } catch (RuntimeException e) {
            log.debug("PatchPanelEntry for key {} not found, will create", entry.getDevicePortKey());
        }

        if (existing == null) {
            createEntry(entry);
        } else {
            updateEntry(existing, entry);
        }
    }

    private void createEntry(PatchPanelEntry entry) {
        try (Transaction txn = store.beginTransaction(entry.getDevicePortKey())) {
            store.createItem(txn, entry);
            try {
                txn.commit();
                log.debug("Created PatchPanelEntry for key {}", entry.getDevicePortKey());
            } catch (CommitConflictException | DuplicateKeyException e) {
                txn.abort();
                log.warn("Conflict creating PatchPanelEntry for key {}, skipping", entry.getDevicePortKey());
            }
        }
    }

    private void updateEntry(PatchPanelEntry existing, PatchPanelEntry updated) {
        existing.setRackSerial(updated.getRackSerial());
        existing.setDeviceName(updated.getDeviceName());
        existing.setDevicePort(updated.getDevicePort());
        existing.setBuildingName(updated.getBuildingName());
        existing.setRoomName(updated.getRoomName());
        existing.setRackNumber(updated.getRackNumber());
        existing.setEasyMark(updated.getEasyMark());
        existing.setLastFetchedAt(updated.getLastFetchedAt());

        try (Transaction txn = store.beginTransaction(existing.getDevicePortKey())) {
            store.updateItem(txn, existing);
            try {
                txn.commit();
                log.debug("Updated PatchPanelEntry for key {}", existing.getDevicePortKey());
            } catch (CommitConflictException | DuplicateKeyException e) {
                txn.abort();
                log.warn("Conflict updating PatchPanelEntry for key {}, skipping", existing.getDevicePortKey());
            }
        }
    }

    /**
     * Returns all PatchPanelEntry records for a rack, keyed by devicePortKey.
     */
    public Map<String, PatchPanelEntry> getByRackSerial(@NonNull String rackSerial) {
        PatchPanelEntry.RackSerialIndex prefix =
                PatchPanelEntry.RackSerialIndex.builder().rackSerial(rackSerial).build();

        Map<String, PatchPanelEntry> result = new HashMap<>();
        ScanPage<PatchPanelEntry> page = rackSerialIndex.beginPrefixScan(prefix, DEFAULT_PAGE_SIZE);

        while (page != null) {
            KievRateLimiter.throttle();
            List<PatchPanelEntry> pageResults = page.results();
            if (pageResults != null) {
                pageResults.stream()
                        .filter(Objects::nonNull)
                        .filter(e -> Objects.equals(e.getRackSerial(), rackSerial))
                        .forEach(e -> result.put(e.getDevicePortKey(), e));
            }
            page = page.hasNext() ? rackSerialIndex.scan(page.paginationToken()) : null;
        }

        log.info("Found {} PatchPanelEntry records for rack {}", result.size(), rackSerial);
        return result;
    }

    /**
     * Returns all PatchPanelEntry records for a rack as a list.
     */
    public List<PatchPanelEntry> listByRackSerial(@NonNull String rackSerial) {
        return new ArrayList<>(getByRackSerial(rackSerial).values());
    }
}
