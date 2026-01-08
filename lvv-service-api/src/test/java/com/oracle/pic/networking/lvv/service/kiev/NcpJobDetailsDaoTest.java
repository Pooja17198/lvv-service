package com.oracle.pic.networking.lvv.service.kiev;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.kiev.Transaction;
import com.oracle.pic.kiev.exceptions.CommitConflictException;
import com.oracle.pic.kiev.exceptions.DuplicateKeyException;
import com.oracle.pic.kiev.mapping.Index;
import com.oracle.pic.kiev.mapping.MappedHashBucket;
import com.oracle.pic.kiev.mapping.ScanPage;
import com.oracle.pic.kiev.mapping.token.PaginationTokenSerializer;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NcpJobDetailsDaoTest {

    @Mock ConfigurationStore<String, NcpJobDetails> store;
    @Mock MappedHashBucket<String, NcpJobDetails> bucket;
    @Mock PaginationTokenSerializer serializer;
    @Mock Index<NcpJobDetails.RackSerialIndex, NcpJobDetails> rackIndex;
    @Mock ScanPage<NcpJobDetails> page1;
    @Mock ScanPage<NcpJobDetails> page2;
    @Mock Transaction txn;
    @Mock MetricsScope scope;

    NcpJobDetailsDao dao;

    final String rackSerial = "RSN-1";

    @BeforeEach
    void setUp() {
        // beginTransaction returns a fresh transaction each time
        when(store.beginTransaction(anyString())).thenReturn(txn);
        dao = new NcpJobDetailsDao(store, serializer, bucket);
    }

    private static Map<String, String> mapOf(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    private static NcpJobDetails job(String device, String rack, String jobId, JobStatus status) {
        return NcpJobDetails.builder()
                .deviceName(device)
                .rackSerial(rack)
                .jobId(jobId)
                .jobStatus(status)
                .build();
    }

    @Test
    void addUpdateNcpJobDetails_insertsNew_withCorrectStatus() throws Exception {
        // existing empty
        NcpJobDetailsDao spyDao = Mockito.spy(dao);
        doReturn(Collections.emptyList()).when(spyDao).getNcpJobDetailsForRack(rackSerial);

        HashMap<String, String> jobs = new HashMap<>();
        jobs.put("devA", ""); // NOT_TRIGGERED
        jobs.put("devB", "job-1"); // IN_PROGRESS

        // Act
        spyDao.addUpdateNcpJobDetails(jobs, rackSerial, scope);

        // Assert: one transaction, two creates with expected properties, commit called
        ArgumentCaptor<NcpJobDetails> created = ArgumentCaptor.forClass(NcpJobDetails.class);
        verify(store, times(1)).beginTransaction(rackSerial);
        verify(store, times(2)).createItem(eq(txn), created.capture());
        verify(txn).commit();

        Map<String, NcpJobDetails> byDevice =
                created.getAllValues().stream()
                        .collect(Collectors.toMap(NcpJobDetails::getDeviceName, x -> x));

        assertEquals(JobStatus.NOT_TRIGGERED, byDevice.get("devA").getJobStatus());
        assertEquals("", byDevice.get("devA").getJobId());
        assertEquals(JobStatus.IN_PROGRESS, byDevice.get("devB").getJobStatus());
        assertEquals("job-1", byDevice.get("devB").getJobId());

        // metric emitted once per add
        verify(scope).emit(anyString(), eq(1.0));
    }

    @Test
    void addUpdateNcpJobDetails_updatesExisting_onlyWhenIncomingNotTriggered() {
        // existing devX present
        NcpJobDetails existingTriggered = job("devX", rackSerial, "old", JobStatus.IN_PROGRESS);
        NcpJobDetails existingNotTriggered = job("devY", rackSerial, "", JobStatus.NOT_TRIGGERED);

        NcpJobDetailsDao spyDao = Mockito.spy(dao);
        doReturn(Arrays.asList(existingTriggered, existingNotTriggered))
                .when(spyDao)
                .getNcpJobDetailsForRack(rackSerial);

        HashMap<String, String> jobs = new HashMap<>();
        jobs.put("devX", "new-id"); // should update existing (-> IN_PROGRESS, id=new-id)
        jobs.put("devY", ""); // NOT_TRIGGERED incoming, should NOT overwrite existing

        spyDao.addUpdateNcpJobDetails(jobs, rackSerial, scope);

        ArgumentCaptor<NcpJobDetails> updated = ArgumentCaptor.forClass(NcpJobDetails.class);
        // Only devX gets updated
        verify(store, times(1)).updateItem(eq(txn), updated.capture());
        NcpJobDetails upd = updated.getValue();
        assertEquals("devX", upd.getDeviceName());
        assertEquals("new-id", upd.getJobId());
        assertEquals(JobStatus.IN_PROGRESS, upd.getJobStatus());

        // devY (NOT_TRIGGERED incoming) does not trigger update
        verify(store, never()).createItem(any(), argThat(j -> "devY".equals(j.getDeviceName())));
    }

    @Test
    void addUpdateNcpJobDetails_batchesBy50() throws Exception {
        NcpJobDetailsDao spyDao = Mockito.spy(dao);
        doReturn(Collections.emptyList()).when(spyDao).getNcpJobDetailsForRack(rackSerial);

        HashMap<String, String> jobs = new HashMap<>();
        for (int i = 0; i < 51; i++) {
            jobs.put("dev-" + i, "");
        }

        spyDao.addUpdateNcpJobDetails(jobs, rackSerial, scope);

        // 51 create calls, across multiple transactions (2 batches)
        verify(store, times(2)).beginTransaction(rackSerial);
        verify(store, times(51)).createItem(eq(txn), any(NcpJobDetails.class));
        verify(txn, times(2)).commit();
    }

    @Test
    void addUpdateNcpJobDetails_commitConflict_abortsAndThrowsRenderable() throws Exception {
        NcpJobDetailsDao spyDao = Mockito.spy(dao);
        doReturn(Collections.emptyList()).when(spyDao).getNcpJobDetailsForRack(rackSerial);

        HashMap<String, String> jobs = new HashMap<>();
        jobs.put("devA", "job");

        doThrow(new CommitConflictException("conflict")).when(txn).commit();

        RenderableException ex =
                assertThrows(
                        RenderableException.class,
                        () -> spyDao.addUpdateNcpJobDetails(jobs, rackSerial, scope));
        assertTrue(ex.getMessage().contains("DB transaction failed"));

        verify(txn).abort();
    }

    @Test
    void addUpdateNcpJobDetails_duplicateKey_abortsAndThrowsRenderable() throws Exception {
        NcpJobDetailsDao spyDao = Mockito.spy(dao);
        doReturn(Collections.emptyList()).when(spyDao).getNcpJobDetailsForRack(rackSerial);

        HashMap<String, String> jobs = new HashMap<>();
        jobs.put("devA", "job");

        doThrow(new DuplicateKeyException("dup")).when(txn).commit();

        assertThrows(
                RenderableException.class,
                () -> spyDao.addUpdateNcpJobDetails(jobs, rackSerial, scope));
        verify(txn).abort();
    }

    @Test
    void getNcpJobDetails_returnsItemOrNullOnException() {
        NcpJobDetails item = job("dev1", rackSerial, "id", JobStatus.IN_PROGRESS);

        when(store.getItem("dev1")).thenReturn(item);
        assertSame(item, dao.getNcpJobDetails("dev1"));

        when(store.getItem("oops")).thenThrow(new RuntimeException("nope"));
        assertNull(dao.getNcpJobDetails("oops"));
    }

    @Test
    void updateNcpJobDetails_nullNoop() {
        dao.updateNcpJobDetails(null);

        verify(store, never()).beginTransaction(anyString());
        verify(store, never()).updateItem(any(), any());
    }

    @Test
    void updateNcpJobDetails_happyPath() throws Exception {
        NcpJobDetails item = job("dev1", rackSerial, "id", JobStatus.IN_PROGRESS);

        dao.updateNcpJobDetails(item);

        verify(store).beginTransaction("dev1");
        verify(store).updateItem(txn, item);
        verify(txn).commit();
    }

    @Test
    void updateNcpJobDetails_commitExceptions_abortAndThrow() throws Exception {
        NcpJobDetails item = job("dev1", rackSerial, "id", JobStatus.IN_PROGRESS);

        doThrow(new CommitConflictException("conf")).when(txn).commit();
        assertThrows(RenderableException.class, () -> dao.updateNcpJobDetails(item));
        verify(txn).abort();

        // Second path DuplicateKeyException
        reset(txn);
        when(store.beginTransaction("dev1")).thenReturn(txn);
        doThrow(new DuplicateKeyException("dup")).when(txn).commit();
        assertThrows(RenderableException.class, () -> dao.updateNcpJobDetails(item));
        verify(txn).abort();
    }

    @Test
    void updateNcpJobStatus_whenExisting_updatesAndCommits() throws Exception {
        NcpJobDetails existing = job("dev1", rackSerial, "id", JobStatus.NOT_TRIGGERED);
        when(store.beginTransaction("dev1")).thenReturn(txn);
        // dao.getNcpJobDetails calls store.getItem internally
        when(store.getItem("dev1")).thenReturn(existing);

        dao.updateNcpJobStatus("dev1", JobStatus.COMPLETED);

        assertEquals(JobStatus.COMPLETED, existing.getJobStatus());
        verify(store).updateItem(txn, existing);
        verify(txn).commit();
    }

    @Test
    void updateNcpJobStatus_whenMissing_stillCommitsWithoutUpdate() throws Exception {
        when(store.beginTransaction("dev1")).thenReturn(txn);
        when(store.getItem("dev1")).thenReturn(null);

        dao.updateNcpJobStatus("dev1", JobStatus.FAILED);

        verify(store, never()).updateItem(any(), any());
        verify(txn).commit();
    }

    @Test
    void updateNcpJobStatus_commitExceptions_abortAndThrow() throws Exception {
        when(store.beginTransaction("dev1")).thenReturn(txn);
        when(store.getItem("dev1"))
                .thenReturn(job("dev1", rackSerial, "id", JobStatus.NOT_TRIGGERED));
        doThrow(new CommitConflictException("conf")).when(txn).commit();

        assertThrows(
                RenderableException.class, () -> dao.updateNcpJobStatus("dev1", JobStatus.FAILED));
        verify(txn).abort();

        reset(txn);
        when(store.beginTransaction("dev1")).thenReturn(txn);
        when(store.getItem("dev1"))
                .thenReturn(job("dev1", rackSerial, "id", JobStatus.NOT_TRIGGERED));
        doThrow(new DuplicateKeyException("dup")).when(txn).commit();

        assertThrows(
                RenderableException.class, () -> dao.updateNcpJobStatus("dev1", JobStatus.FAILED));
        verify(txn).abort();
    }

    @Test
    void getNcpJobDetailsForRack_multiPage_filtersAndAggregates_andThrottlesPerPage() {
        // Prepare page1 with mixed results (nulls and other rack serials filtered out)
        NcpJobDetails r1a = job("a", rackSerial, "id", JobStatus.IN_PROGRESS);
        NcpJobDetails r1bOther = job("b", "OTHER", "id", JobStatus.IN_PROGRESS);

        when(bucket.getIndex(anyString(), eq(NcpJobDetails.RackSerialIndex.class)))
                .thenReturn(rackIndex);
        dao = new NcpJobDetailsDao(store, serializer, bucket);
        when(rackIndex.beginPrefixScan(any(NcpJobDetails.RackSerialIndex.class), anyInt()))
                .thenReturn(page1);
        when(page1.results()).thenReturn(Arrays.asList(r1a, null, r1bOther));
        when(page1.hasNext()).thenReturn(true);
        com.oracle.pic.kiev.mapping.PaginationToken t1 =
                mock(com.oracle.pic.kiev.mapping.PaginationToken.class);
        when(page1.paginationToken()).thenReturn(t1);
        when(rackIndex.scan(t1)).thenReturn(page2);

        // page2 only matches rackSerial
        NcpJobDetails r2c = job("c", rackSerial, "id2", JobStatus.COMPLETED);
        when(page2.results()).thenReturn(Collections.singletonList(r2c));
        when(page2.hasNext()).thenReturn(false);

        // Mock static KievRateLimiter.throttle
        try (MockedStatic<KievRateLimiter> rate = Mockito.mockStatic(KievRateLimiter.class)) {
            List<NcpJobDetails> out = dao.getNcpJobDetailsForRack(rackSerial);
            assertEquals(2, out.size());
            List<String> names = out.stream().map(NcpJobDetails::getDeviceName).sorted().toList();
            assertEquals(Arrays.asList("a", "c"), names);
            // throttle called twice (once per page)
            rate.verify(KievRateLimiter::throttle, times(2));
        }
    }

    @Test
    void getNcpJobDetailsForRack_singlePage_noNext() {
        NcpJobDetails r1 = job("x", rackSerial, "id", JobStatus.IN_PROGRESS);
        when(bucket.getIndex(anyString(), eq(NcpJobDetails.RackSerialIndex.class)))
                .thenReturn(rackIndex);
        dao = new NcpJobDetailsDao(store, serializer, bucket);
        when(rackIndex.beginPrefixScan(any(NcpJobDetails.RackSerialIndex.class), anyInt()))
                .thenReturn(page1);
        when(page1.results()).thenReturn(Collections.singletonList(r1));
        when(page1.hasNext()).thenReturn(false);

        try (MockedStatic<KievRateLimiter> rate = Mockito.mockStatic(KievRateLimiter.class)) {
            List<NcpJobDetails> out = dao.getNcpJobDetailsForRack(rackSerial);
            assertEquals(1, out.size());
            assertEquals("x", out.get(0).getDeviceName());
            rate.verify(KievRateLimiter::throttle, times(1));
        }
    }
}
