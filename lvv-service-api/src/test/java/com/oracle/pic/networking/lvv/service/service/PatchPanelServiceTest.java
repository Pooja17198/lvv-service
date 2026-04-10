package com.oracle.pic.networking.lvv.service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oracle.pic.networking.lvv.service.dependencies.ide.IdeClient;
import com.oracle.pic.networking.lvv.service.kiev.PatchPanelEntry;
import com.oracle.pic.networking.lvv.service.kiev.PatchPanelEntryDao;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PatchPanelServiceTest {

    private PatchPanelEntryDao dao;
    private IdeClient ideClient;
    private PatchPanelService service;

    @BeforeEach
    void setUp() {
        dao = mock(PatchPanelEntryDao.class);
        ideClient = mock(IdeClient.class);
        service = new PatchPanelService(dao, ideClient);
    }

    @Test
    void getPatchPanelForRack_noCache_fetchesFromIdeAndStoresMappedEntries() {
        when(dao.listByRackSerial("rack-serial")).thenReturn(List.of());
        when(ideClient.fetchPhysicalCutsheetsForRack("iad60", "4426"))
                .thenReturn(
                        List.of(
                                Map.of(
                                        "deviceName", "host-a",
                                        "devicePort", "Eth1/1",
                                        "buildingName", "iad60",
                                        "roomName", "room-1",
                                        "rackNumber", "4426",
                                        "easyMark", List.of("A", "B")),
                                // This item is invalid and should be skipped.
                                Map.of(
                                        "deviceName", "host-b",
                                        "buildingName", "iad60")));

        List<PatchPanelEntry> result =
                service.getPatchPanelForRack("iad60", "4426", "rack-serial");

        assertEquals(1, result.size());
        PatchPanelEntry stored = result.get(0);
        assertEquals("host-a#Eth1/1", stored.getDevicePortKey());
        assertEquals("rack-serial", stored.getRackSerial());
        assertEquals("host-a", stored.getDeviceName());
        assertEquals("Eth1/1", stored.getDevicePort());
        assertEquals("iad60", stored.getBuildingName());
        assertEquals("room-1", stored.getRoomName());
        assertEquals("4426", stored.getRackNumber());
        assertEquals(List.of("A", "B"), stored.getEasyMark());
        assertNotNull(stored.getLastFetchedAt());

        ArgumentCaptor<List<PatchPanelEntry>> upsertCaptor = ArgumentCaptor.forClass(List.class);
        verify(dao).upsertAll(upsertCaptor.capture());
        assertEquals(1, upsertCaptor.getValue().size());
        assertEquals("host-a#Eth1/1", upsertCaptor.getValue().get(0).getDevicePortKey());
    }

    @Test
    void getPatchPanelForRack_cachedDataExists_returnsCacheAndSkipsIdeFetch() {
        PatchPanelEntry cachedEntry =
                PatchPanelEntry.builder()
                        .devicePortKey("cached#Eth1/9")
                        .rackSerial("rack-serial")
                        .deviceName("cached")
                        .devicePort("Eth1/9")
                        .lastFetchedAt(new Timestamp(System.currentTimeMillis()))
                        .build();
        when(dao.listByRackSerial("rack-serial")).thenReturn(List.of(cachedEntry));

        List<PatchPanelEntry> result =
                service.getPatchPanelForRack("iad60", "4426", "rack-serial");

        assertEquals(1, result.size());
        assertEquals("cached#Eth1/9", result.get(0).getDevicePortKey());
        verify(ideClient, never()).fetchPhysicalCutsheetsForRack("iad60", "4426");
        verify(dao, never()).upsertAll(anyList());
    }

    @Test
    void getPatchPanelForRack_easyMarkNotList_mapsAsNull() {
        when(dao.listByRackSerial("rack-serial")).thenReturn(List.of());
        when(ideClient.fetchPhysicalCutsheetsForRack("iad60", "4426"))
                .thenReturn(
                        List.of(
                                Map.of(
                                        "deviceName", "host-a",
                                        "devicePort", "Eth1/1",
                                        "easyMark", "not-a-list")));

        List<PatchPanelEntry> result =
                service.getPatchPanelForRack("iad60", "4426", "rack-serial");

        assertEquals(1, result.size());
        assertNull(result.get(0).getEasyMark());
    }
}
