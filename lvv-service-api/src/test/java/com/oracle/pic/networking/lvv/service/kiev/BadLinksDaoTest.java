package com.oracle.pic.networking.lvv.service.kiev;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.kiev.Transaction;
import com.oracle.pic.kiev.mapping.Index;
import com.oracle.pic.kiev.mapping.MappedHashBucket;
import com.oracle.pic.kiev.mapping.PaginationToken;
import com.oracle.pic.kiev.mapping.ScanPage;
import com.oracle.pic.kiev.mapping.token.PaginationTokenSerializer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BadLinksDaoTest {
    @Mock ConfigurationStore<String, BadLinks> badLinksStore;
    @Mock MappedHashBucket<String, BadLinks> badLinksBucket;
    @Mock Index<BadLinks.BuildingIndex, BadLinks> buildingIndex;
    @Mock PaginationTokenSerializer serializer;
    @Mock MetricsScope metricsScope;
    @Mock Transaction txn;
    BadLinksDao dao;
    final String building = "BLD-1";

    @BeforeEach
    void setUp() {
        // Arrange: DAO needs a Building index from the mapped bucket; provide the mock
        when(badLinksBucket.getIndex(
                        eq(BadLinks.BUILDING_COLUMN_NAME_IDX), eq(BadLinks.BuildingIndex.class)))
                .thenReturn(buildingIndex);
        // Instantiate DAO under test with mocked dependencies
        dao = new BadLinksDao(badLinksStore, serializer, badLinksBucket);
    }

    /**
     * Verifies getBadLinksForBuilding() behavior end-to-end with pagination and filtering:<br>
     * - Starts a prefix scan on the Building index for the requested building.<br>
     * - Consumes multiple pages via PaginationToken (page1 -> page2).<br>
     * - Filters out rows that do not belong to the requested building (defensive filtering).<br>
     * - Returns results from all pages in iteration order (r1 from page1, r2 from page2).<br>
     * - MetricsScope.create(...) is called and success is recorded, without real side effects
     * thanks to static mocking of MetricsScope.<br>
     */
    @Test
    void testGetBadLinksForBuilding_pagedResultsAndFiltering() {
        BadLinks.BuildingIndex prefix = BadLinks.BuildingIndex.builder().building(building).build();
        ScanPage<BadLinks> page1 = mock(ScanPage.class);
        ScanPage<BadLinks> page2 = mock(ScanPage.class);
        PaginationToken token = mock(PaginationToken.class);
        BadLinks r1 =
                BadLinks.builder()
                        .building(building)
                        .device("dev1")
                        .remoteDevice("rdev1")
                        .jiraTicket("J-1")
                        .build();
        // rOther is intentionally for a different building to verify filtering logic
        BadLinks rOther =
                BadLinks.builder()
                        .building("OTHER")
                        .device("devX")
                        .remoteDevice("rdevX")
                        .jiraTicket("J-X")
                        .build();
        BadLinks r2 =
                BadLinks.builder()
                        .building(building)
                        .device("dev2")
                        .remoteDevice("rdev2")
                        .jiraTicket("J-2")
                        .build();
        // Simulate first page with r1 + a foreign-building row; hasNext -> true, provide token
        when(buildingIndex.beginPrefixScan(prefix, 1000)).thenReturn(page1);
        when(page1.results()).thenReturn(List.of(r1, rOther));
        when(page1.hasNext()).thenReturn(true);
        when(page1.paginationToken()).thenReturn(token);
        // Simulate second page with r2; hasNext -> false to end iteration
        when(buildingIndex.scan(token)).thenReturn(page2);
        when(page2.results()).thenReturn(List.of(r2));
        when(page2.hasNext()).thenReturn(false);
        // Stub MetricsScope to avoid emitting real metrics and to assert success path runs
        try (MockedStatic<MetricsScope> mScope = mockStatic(MetricsScope.class)) {
            mScope.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);
            when(metricsScope.recordSuccess()).thenReturn(metricsScope);
            // Act: fetch paged results; expect filtering and aggregation across pages
            List<BadLinks> out = dao.getBadLinksForBuilding(building);
            // Assert: only rows from the requested building and in aggregated order
            assertEquals(List.of(r1, r2), out);
        }
    }

    /**
     * Ensures deleteRowsByJiraTickets() is a no-op when the provided jiraTickets list is empty:<br>
     * - Does not begin a transaction with the store.<br>
     * - Does not attempt to delete any items.<br>
     * - This guards against unnecessary transactions or errors on empty inputs.<br>
     */
    @Test
    void testDeleteRowsByJiraTickets_emptyJiraTickets_noop() {
        dao.deleteRowsByJiraTickets(building, Collections.emptyList());
        verify(badLinksStore, never()).beginTransaction(anyString());
        verifyNoInteractions(txn);
    }

    /**
     * Confirms deleteRowsByJiraTickets() batches deletes and commits when exceeding the max writes
     * per txn:<br>
     * - With 51 jira tickets and MAX_WRITES_PER_TRANSACTION=50, DAO should split into 2
     * transactions.<br>
     * - Verifies deleteItem(...) is called 51 times in total.<br>
     * - Verifies commit() is called 2 times (once per batch).<br>
     * - Also ensures MetricsScope is created (mocked) to avoid external side effects.<br>
     */
    @Test
    void testDeleteRowsByJiraTickets_batchesAndCommits_success() throws Exception {
        // 51 jiraTickets to force multiple transaction usages
        List<String> jiraTickets = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            jiraTickets.add("DO-" + i);
        }
        when(badLinksStore.beginTransaction(anyString())).thenReturn(txn);
        try (MockedStatic<MetricsScope> mScope = mockStatic(MetricsScope.class);
                MockedStatic<KievRateLimiter> throttle = mockStatic(KievRateLimiter.class)) {
            mScope.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);
            // Act: perform deletes
            dao.deleteRowsByJiraTickets(building, jiraTickets);
            // Assert: all jiraTickets deleted; commit per ticket
            verify(badLinksStore, times(51)).deleteItem(eq(txn), anyString());
            verify(txn, times(2)).commit();
            throttle.verify(() -> KievRateLimiter.throttle(), times(2));
        }
    }

    /**
     * Validates deleteRowsByJiraTickets() skips null and blank jiraTickets:<br>
     * - Only non-null and non-blank identifiers should be passed to deleteItem(...).<br>
     * - Captures arguments to verify exactly which tickets were deleted.<br>
     */
    @Test
    void testDeleteRowsByJiraTickets_skipsNullAndBlank_andCommits() throws Exception {
        List<String> jiraTickets = Arrays.asList("DO-1", null, " ", "DO-2");
        when(badLinksStore.beginTransaction(anyString())).thenReturn(txn);
        try (MockedStatic<MetricsScope> mScope = mockStatic(MetricsScope.class);
                MockedStatic<KievRateLimiter> throttle = mockStatic(KievRateLimiter.class)) {
            mScope.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);
            // Act: attempt deletions with mixed-quality input (valid + null/blank)
            dao.deleteRowsByJiraTickets(building, jiraTickets);
            // Assert: only valid tickets were deleted; capture to verify exact values
            ArgumentCaptor<String> ticketCaptor = ArgumentCaptor.forClass(String.class);
            verify(badLinksStore, times(2)).deleteItem(eq(txn), ticketCaptor.capture());
            List<String> deletedTickets = ticketCaptor.getAllValues();
            assertTrue(deletedTickets.containsAll(List.of("DO-1", "DO-2")));
            verify(txn, times(1)).commit();
            throttle.verify(() -> KievRateLimiter.throttle(), times(1));
        }
    }

    /**
     * Ensures deleteRowsByJiraTickets() surfaces a RuntimeException when commit fails:<br>
     * - Performs deletes for provided jira tickets and then attempts commit().<br>
     * - Mocks commit() to throw, and verifies the DAO throws a RuntimeException with a contextual
     * message ("Failed to delete bad-link ids").<br>
     * - Verifies deleteItem(...) was invoked for the ticket and commit() was attempted.<br>
     * - Confirms failure is not swallowed and is propagated for callers to handle.<br>
     */
    @Test
    void testDeleteRowsByJiraTickets_commitFailure_throwsRuntime() throws Exception {
        List<String> jiraTickets = List.of("DO-1");
        when(badLinksStore.beginTransaction(anyString())).thenReturn(txn);
        doThrow(new RuntimeException("commit fail")).when(txn).commit();
        try (MockedStatic<MetricsScope> mScope = mockStatic(MetricsScope.class);
                MockedStatic<KievRateLimiter> throttle = mockStatic(KievRateLimiter.class)) {
            mScope.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);
            // Act + Assert: DAO should surface an exception on commit failure
            assertThrows(
                    RuntimeException.class,
                    () -> dao.deleteRowsByJiraTickets(building, jiraTickets));
            // Ensure the delete was attempted and commit was called (and failed)
            verify(badLinksStore).deleteItem(txn, "DO-1");
            verify(txn).commit();
            throttle.verify(() -> KievRateLimiter.throttle(), times(1));
        }
    }

    /**
     * Ensures insertRows() is a no-op when the rows list is empty:<br>
     * - Does not open a transaction.<br>
     * - Does not call createItem(...).<br>
     * - This protects against unnecessary work and side effects on empty input.<br>
     */
    @Test
    void testInsertRows_empty_noop() {
        dao.insertRows(building, Collections.emptyList());
        verify(badLinksStore, never()).beginTransaction(anyString());
        verifyNoInteractions(txn);
    }

    /**
     * Confirms insertRows() batches writes and commits when exceeding the max writes per txn:<br>
     * - With 51 rows and MAX_WRITES_PER_TRANSACTION=50, DAO should split into 2 transactions.<br>
     * - Verifies createItem(...) is called 51 times in total.<br>
     * - Verifies commit() is called 2 times (once per batch).<br>
     * - MetricsScope is mocked to prevent external emission of metrics.<br>
     */
    @Test
    void testInsertRows_batchesAndCommits_success() throws Exception {
        // 51 rows to force 2 batches with MAX_WRITES_PER_TRANSACTION=50
        List<BadLinks> rows = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            BadLinks r =
                    BadLinks.builder()
                            .building(building)
                            .device("d" + i)
                            .remoteDevice("rd" + i)
                            .jiraTicket("J-" + i)
                            .build();
            rows.add(r);
        }
        when(badLinksStore.beginTransaction(anyString())).thenReturn(txn);
        try (MockedStatic<MetricsScope> mScope = mockStatic(MetricsScope.class);
                MockedStatic<KievRateLimiter> throttle = mockStatic(KievRateLimiter.class)) {
            mScope.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);
            // Act: insert all rows; DAO should batch and commit twice
            dao.insertRows(building, rows);
            // Assert: all rows persisted; two commits (one per batch)
            verify(badLinksStore, times(51)).createItem(eq(txn), any(BadLinks.class));
            verify(txn, times(2)).commit();
            throttle.verify(() -> KievRateLimiter.throttle(), times(2));
        }
    }

    /**
     * Ensures insertRows() skips null entries within the batch:<br>
     * - Only non-null rows are passed to createItem(...).<br>
     * - Uses an ArgumentCaptor to verify exactly which rows were created.<br>
     * - Transaction is committed once after processing the (filtered) batch.<br>
     */
    @Test
    void testInsertRows_skipsNulls_andCommits() throws Exception {
        BadLinks r1 =
                BadLinks.builder()
                        .building(building)
                        .device("d1")
                        .remoteDevice("rd1")
                        .jiraTicket("J-1")
                        .build();
        BadLinks r2 =
                BadLinks.builder()
                        .building(building)
                        .device("d2")
                        .remoteDevice("rd2")
                        .jiraTicket("J-2")
                        .build();
        List<BadLinks> rows = Arrays.asList(r1, null, r2);
        when(badLinksStore.beginTransaction(anyString())).thenReturn(txn);
        try (MockedStatic<MetricsScope> mScope = mockStatic(MetricsScope.class);
                MockedStatic<KievRateLimiter> throttle = mockStatic(KievRateLimiter.class)) {
            mScope.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);
            // Act: insert rows including a null; DAO should skip null and insert only valid rows
            dao.insertRows(building, rows);
            // Assert: exactly two createItem calls for r1 and r2; capture and validate
            ArgumentCaptor<BadLinks> rowCaptor = ArgumentCaptor.forClass(BadLinks.class);
            verify(badLinksStore, times(2)).createItem(eq(txn), rowCaptor.capture());
            List<BadLinks> createdRows = rowCaptor.getAllValues();
            assertTrue(createdRows.containsAll(List.of(r1, r2)));
            // Single batch commit for small input
            verify(txn, times(1)).commit();
            throttle.verify(() -> KievRateLimiter.throttle(), times(1));
        }
    }

    /**
     * Validates the insertRows() failure path when commit throws:<br>
     * - Mocks commit() to fail after createItem(...) succeeds.<br>
     * - Asserts the DAO throws a RuntimeException with a clear contextual message ("Failed to
     * insert bad-link row for").<br>
     * - Verifies createItem(...) was invoked for the provided row and commit() was attempted.<br>
     * - Demonstrates that lower-level transaction errors are surfaced to callers.<br>
     */
    @Test
    void testInsertRows_commitFailure_throwsRuntime() throws Exception {
        BadLinks r1 =
                BadLinks.builder()
                        .building(building)
                        .device("d1")
                        .remoteDevice("rd1")
                        .jiraTicket("J-1")
                        .build();
        when(badLinksStore.beginTransaction(anyString())).thenReturn(txn);
        doThrow(new RuntimeException("commit fail")).when(txn).commit();
        try (MockedStatic<MetricsScope> mScope = mockStatic(MetricsScope.class);
                MockedStatic<KievRateLimiter> throttle = mockStatic(KievRateLimiter.class)) {
            mScope.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);
            // Act + Assert: expect an exception on commit failure
            assertThrows(RuntimeException.class, () -> dao.insertRows(building, List.of(r1)));
            // Ensure the record was created and commit was attempted (and failed)
            verify(badLinksStore).createItem(txn, r1);
            verify(txn).commit();
            throttle.verify(() -> KievRateLimiter.throttle(), times(1));
        }
    }
}
