package com.oracle.pic.networking.lvv.service.kiev;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.oracle.pic.kiev.Transaction;
import com.oracle.pic.networking.lvv.service.dependencies.notificationservice.NotificationServiceHelper;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonitoringDaoTest {

    @Mock ConfigurationStore<String, Monitoring> monitoringStore;
    @Mock Transaction txn;
    @Mock NotificationServiceHelper notificationServiceHelper;

    private MonitoringDao dao;

    private final String building = "BLD-1";
    private final Timestamp nowTs = Timestamp.from(Instant.now());

    @BeforeEach
    void setUp() {
        dao = new MonitoringDao(monitoringStore, notificationServiceHelper);
    }

    @Test
    void testGet_RowForBuilding_returnsItem() {
        Monitoring item = Monitoring.builder().building(building).lastUpdated(nowTs).build();
        when(monitoringStore.getItem(building)).thenReturn(item);

        Monitoring out = dao.getRowForBuilding(building);
        assertNotNull(out);
        assertEquals(building, out.getBuilding());
        assertEquals(nowTs, out.getLastUpdated());
    }

    @Test
    void testGet_RowForBuilding_returnsNull_whenStoreThrows() {
        when(monitoringStore.getItem(building)).thenThrow(new RuntimeException("not found"));
        Monitoring out = dao.getRowForBuilding(building);
        assertNull(out);
    }

    @Test
    void testUpsert_RowForBuilding_createsWhenMissing_usesProvidedTxn() {
        // existing = null (store throws on get)
        when(monitoringStore.getItem(building)).thenThrow(new RuntimeException("not found"));

        dao.upsertRowForBuilding(building, nowTs, null, txn);

        // created with expected values
        ArgumentCaptor<Monitoring> captor = ArgumentCaptor.forClass(Monitoring.class);
        verify(monitoringStore).createItem(eq(txn), captor.capture());
        Monitoring persisted = captor.getValue();
        assertEquals(building, persisted.getBuilding());
        assertEquals(nowTs, persisted.getLastUpdated());
    }

    @Test
    void testUpsert_updatesWhenExisting_usesProvidedTxn() {
        Monitoring existing = Monitoring.builder().building(building).lastUpdated(nowTs).build();
        when(monitoringStore.getItem(building)).thenReturn(existing);

        Timestamp newer = Timestamp.from(Instant.now().plusSeconds(10));
        dao.upsertRowForBuilding(building, newer, null, txn);

        ArgumentCaptor<Monitoring> captor = ArgumentCaptor.forClass(Monitoring.class);
        verify(monitoringStore).updateItem(eq(txn), captor.capture());
        Monitoring persisted = captor.getValue();
        assertEquals(building, persisted.getBuilding());
        assertEquals(newer, persisted.getLastUpdated());
    }

    @Test
    void testUpsertLastUpdated_delegatesToUpsert_RowForBuilding_andCommitsNewTxn()
            throws Exception {
        // existing = null (store throws)
        when(monitoringStore.getItem(building)).thenThrow(new RuntimeException("not found"));
        when(monitoringStore.beginTransaction(building)).thenReturn(txn);

        dao.upsertLastUpdated(building, nowTs);

        ArgumentCaptor<Monitoring> captor = ArgumentCaptor.forClass(Monitoring.class);
        verify(monitoringStore).createItem(eq(txn), captor.capture());
        Monitoring persisted = captor.getValue();
        assertEquals(building, persisted.getBuilding());
        assertEquals(nowTs, persisted.getLastUpdated());
        verify(txn).commit();
    }

    @Test
    void ensureTopicExistsForBuilding_returnsExisting_withoutCreateOrUpsertRowForBuilding() {
        // existing item with topic OCID present
        Monitoring item =
                Monitoring.builder()
                        .building(building)
                        .lastUpdated(nowTs)
                        .topicOcid("ocid1.topic.existing")
                        .build();
        when(monitoringStore.getItem(building)).thenReturn(item);

        String out = dao.ensureTopicExistsForBuilding(building, txn);

        assertEquals("ocid1.topic.existing", out);
        verify(notificationServiceHelper, never()).createTopic(anyString(), anyString());
        verify(monitoringStore, never()).createItem(any(), any());
        verify(monitoringStore, never()).updateItem(any(), any());
    }

    @Test
    void ensureTopicExistsForBuilding_createsTopic_andUpserts() {
        // no existing record -> should create topic and upsert topicOcid
        when(monitoringStore.getItem(building)).thenThrow(new RuntimeException("not found"));
        when(notificationServiceHelper.createTopic(
                        eq("network-alerting-building-" + building),
                        eq("Topic for sending link down alerts for building " + building)))
                .thenReturn("ocid1.topic.new");

        String out = dao.ensureTopicExistsForBuilding(building, txn);

        assertEquals("ocid1.topic.new", out);

        ArgumentCaptor<Monitoring> captor = ArgumentCaptor.forClass(Monitoring.class);
        verify(monitoringStore).createItem(eq(txn), captor.capture());
        Monitoring persisted = captor.getValue();
        assertEquals(building, persisted.getBuilding());
        assertEquals("ocid1.topic.new", persisted.getTopicOcid());
        verify(notificationServiceHelper)
                .createTopic(
                        eq("network-alerting-building-" + building),
                        eq("Topic for sending link down alerts for building " + building));
    }
}
