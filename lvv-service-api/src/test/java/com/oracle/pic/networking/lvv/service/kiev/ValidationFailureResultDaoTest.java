package com.oracle.pic.networking.lvv.service.kiev;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.kiev.Transaction;
import com.oracle.pic.kiev.exceptions.CommitConflictException;
import com.oracle.pic.kiev.exceptions.DuplicateKeyException;
import com.oracle.pic.kiev.mapping.Index;
import com.oracle.pic.kiev.mapping.MappedHashBucket;
import com.oracle.pic.kiev.mapping.PaginationToken;
import com.oracle.pic.kiev.mapping.ScanPage;
import com.oracle.pic.kiev.mapping.token.PaginationTokenSerializer;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ValidationFailureResultDaoTest {

    @Mock ConfigurationStore<String, ValidationFailureResult> mockStore;
    @Mock MappedHashBucket<String, ValidationFailureResult> mockProvider;
    @Mock PaginationTokenSerializer mockSerializer;

    @Mock
    Index<ValidationFailureResult.RackSerialIndex, ValidationFailureResult> mockRackSerialIndex;

    @Mock Transaction mockTransaction;
    @Mock ScanPage<ValidationFailureResult> mockScanPage1;
    @Mock ScanPage<ValidationFailureResult> mockScanPage2;
    @Mock MetricsScope mockScope;

    ValidationFailureResultDao dao;

    @BeforeEach
    void setup() {
        when(mockProvider.getIndex(
                        eq(ValidationFailureResult.RACK_SERIAL_COLUMN_NAME),
                        ArgumentMatchers.<Class<ValidationFailureResult.RackSerialIndex>>any()))
                .thenReturn(mockRackSerialIndex);
        dao = new ValidationFailureResultDao(mockStore, mockSerializer, mockProvider);
    }

    private static ValidationFailureResult buildResult(
            String rack,
            String device,
            Map<String, List<Map<String, String>>> vr,
            int validations) {
        return ValidationFailureResult.builder()
                .rackSerial(rack)
                .deviceName(device)
                .validationResults(vr)
                .numberOfValidations(validations)
                .firstValidatedTime(Timestamp.from(Instant.now()))
                .build();
    }

    @Test
    void addValidationFailureResultForDevice_success_createsAndCommits()
            throws CommitConflictException {
        Map<String, List<Map<String, String>>> result = Map.of("errors", List.of(Map.of("k", "v")));

        when(mockStore.beginTransaction("dev1")).thenReturn(mockTransaction);

        dao.addValidationFailureResultForDevice("rack1", "dev1", result, mockScope);

        ArgumentCaptor<ValidationFailureResult> captor =
                ArgumentCaptor.forClass(ValidationFailureResult.class);
        verify(mockStore).createItem(eq(mockTransaction), captor.capture());
        ValidationFailureResult created = captor.getValue();

        assertEquals("dev1", created.getDeviceName());
        assertEquals("rack1", created.getRackSerial());
        assertEquals(result, created.getValidationResults());
        assertEquals(1, created.getNumberOfValidations());
        assertNotNull(created.getFirstValidatedTime());

        verify(mockTransaction, times(1)).commit();
    }

    @Test
    void addValidationFailureResultForDevice_commitConflict_emitsMetric_abort_throws()
            throws CommitConflictException {
        when(mockStore.beginTransaction("devX")).thenReturn(mockTransaction);
        doThrow(new CommitConflictException("conflict")).when(mockTransaction).commit();

        assertThrows(
                RenderableException.class,
                () ->
                        dao.addValidationFailureResultForDevice(
                                "rackX", "devX", Map.of(), mockScope));

        verify(mockScope, times(1))
                .emit(eq(MetricNames.AddValidationResults.KievResultUpdateFailure.name()), eq(1.0));
        verify(mockTransaction, times(1)).abort();
    }

    @Test
    void updateValidationFailureResultsForDevice_success_updatesAndIncrementsCount()
            throws CommitConflictException {
        ValidationFailureResult existing =
                buildResult("rackU", "devU", Map.of("init", List.of()), 2);
        Map<String, List<Map<String, String>>> newResult =
                Map.of("updated", List.of(Map.of("x", "y")));

        when(mockStore.beginTransaction("devU")).thenReturn(mockTransaction);

        dao.updateValidationFailureResultsForDevice(existing, "devU", newResult, mockScope);

        ArgumentCaptor<ValidationFailureResult> captor =
                ArgumentCaptor.forClass(ValidationFailureResult.class);
        verify(mockStore).updateItem(eq(mockTransaction), captor.capture());
        ValidationFailureResult updated = captor.getValue();

        assertSame(existing, updated);
        assertEquals(3, updated.getNumberOfValidations());
        assertEquals(newResult, updated.getValidationResults());
        assertNotNull(updated.getFirstValidatedTime());

        verify(mockTransaction, times(1)).commit();
    }

    @Test
    void updateValidationFailureResultsForDevice_duplicateKey_emitsMetric_abort_throws()
            throws CommitConflictException {
        ValidationFailureResult existing = buildResult("r", "devE", Map.of(), 5);
        when(mockStore.beginTransaction("devE")).thenReturn(mockTransaction);
        doThrow(new DuplicateKeyException("dup")).when(mockTransaction).commit();

        assertThrows(
                RenderableException.class,
                () ->
                        dao.updateValidationFailureResultsForDevice(
                                existing, "devE", Map.of(), mockScope));

        verify(mockScope, times(1))
                .emit(eq(MetricNames.AddValidationResults.KievResultUpdateFailure.name()), eq(1.0));
        verify(mockTransaction, times(1)).abort();
    }

    @Test
    void addUpdateValidationFailureResultsForDevices_addsWhenMissing_updatesWhenExisting() {
        // Spy so we can verify internal add/update delegations without exercising commit logic
        ValidationFailureResultDao spyDao =
                Mockito.spy(
                        new ValidationFailureResultDao(mockStore, mockSerializer, mockProvider));

        Map<String, List<Map<String, String>>> rs1 = Map.of("errs", List.of(Map.of("k", "v")));
        Map<String, List<Map<String, String>>> rs2 = Map.of("errs2", List.of(Map.of("x", "y")));

        Map<String, Map<String, List<Map<String, String>>>> inputs = new HashMap<>();
        inputs.put("devMissing", rs1);
        inputs.put("devExisting", rs2);

        ValidationFailureResult existing = buildResult("rack123", "devExisting", Map.of(), 7);

        when(mockStore.getItem("devMissing")).thenThrow(new RuntimeException("not found"));
        when(mockStore.getItem("devExisting")).thenReturn(existing);

        // Stub void methods on spy to avoid running transactions
        doNothing()
                .when(spyDao)
                .addValidationFailureResultForDevice(
                        eq("rack123"), eq("devMissing"), eq(rs1), same(mockScope));
        doNothing()
                .when(spyDao)
                .updateValidationFailureResultsForDevice(
                        same(existing), eq("devExisting"), eq(rs2), same(mockScope));

        spyDao.addUpdateValidationFailureResultsForDevices("rack123", inputs, mockScope);

        verify(spyDao, times(1))
                .addValidationFailureResultForDevice(
                        eq("rack123"), eq("devMissing"), eq(rs1), same(mockScope));
        verify(spyDao, times(1))
                .updateValidationFailureResultsForDevice(
                        same(existing), eq("devExisting"), eq(rs2), same(mockScope));
    }

    @Test
    void getValidationFailuresByRack_basicPagination_filtersByRack_andAggregates() {
        Map<String, List<Map<String, String>>> mv1 = Map.of("a", List.of(Map.of("k", "1")));
        Map<String, List<Map<String, String>>> mv2 = Map.of("b", List.of(Map.of("k", "2")));

        ValidationFailureResult r1 = buildResult("rackA", "devA", mv1, 1);
        ValidationFailureResult rOther = buildResult("other", "devOther", Map.of(), 1);
        List<ValidationFailureResult> page1Results = Arrays.asList(r1, rOther, null);

        ValidationFailureResult r2 = buildResult("rackA", "devB", mv2, 2);
        List<ValidationFailureResult> page2Results = List.of(r2);

        when(mockRackSerialIndex.beginPrefixScan(any(), anyInt())).thenReturn(mockScanPage1);
        when(mockScanPage1.results()).thenReturn(page1Results);
        when(mockScanPage1.hasNext()).thenReturn(true);
        PaginationToken token = mock(PaginationToken.class);
        when(mockScanPage1.paginationToken()).thenReturn(token);
        when(mockRackSerialIndex.scan(token)).thenReturn(mockScanPage2);
        when(mockScanPage2.results()).thenReturn(page2Results);
        when(mockScanPage2.hasNext()).thenReturn(false);

        Object out = dao.getValidationFailuresByRack("rackA");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) out;

        assertEquals(2, result.size());
        assertSame(mv1, result.get("devA"));
        assertSame(mv2, result.get("devB"));
        assertFalse(result.containsKey("devOther"));
    }

    @Test
    void getValidationFailuresByRack_nullIndex_throws() {
        @SuppressWarnings("unchecked")
        MappedHashBucket<String, ValidationFailureResult> provider2 = mock(MappedHashBucket.class);
        when(provider2.getIndex(
                        eq(ValidationFailureResult.RACK_SERIAL_COLUMN_NAME),
                        ArgumentMatchers.<Class<ValidationFailureResult.RackSerialIndex>>any()))
                .thenReturn(null);

        ValidationFailureResultDao dao2 =
                new ValidationFailureResultDao(mockStore, mockSerializer, provider2);

        assertThrows(NullPointerException.class, () -> dao2.getValidationFailuresByRack("rack1"));
    }

    @Test
    void getValidationFailuresByRack_nullPageResults_returnsEmptyMap() {
        when(mockRackSerialIndex.beginPrefixScan(any(), anyInt())).thenReturn(mockScanPage1);
        when(mockScanPage1.results()).thenReturn(null);
        when(mockScanPage1.hasNext()).thenReturn(false);

        Object out = dao.getValidationFailuresByRack("rackZ");
        Map<?, ?> map = (Map<?, ?>) out;
        assertTrue(map.isEmpty());
    }

    @Test
    void addValidationFailureResultForDevice_duplicateKey_emitsMetric_abort_throws()
            throws CommitConflictException {
        when(mockStore.beginTransaction("devDup")).thenReturn(mockTransaction);
        doThrow(new DuplicateKeyException("dup")).when(mockTransaction).commit();

        assertThrows(
                RenderableException.class,
                () ->
                        dao.addValidationFailureResultForDevice(
                                "rackDup", "devDup", Map.of(), mockScope));

        verify(mockScope, times(1))
                .emit(eq(MetricNames.AddValidationResults.KievResultUpdateFailure.name()), eq(1.0));
        verify(mockTransaction, times(1)).abort();
    }

    @Test
    void getValidationFailuresByRack_beginPrefixScanNull_returnsEmptyMap() {
        when(mockRackSerialIndex.beginPrefixScan(any(), anyInt())).thenReturn(null);

        Object out = dao.getValidationFailuresByRack("rackN");
        Map<?, ?> map = (Map<?, ?>) out;
        assertTrue(map.isEmpty());
    }

    @Test
    void addUpdateValidationFailureResultsForDevices_emptyInput_noTransactions() {
        dao.addUpdateValidationFailureResultsForDevices("rack0", Collections.emptyMap(), mockScope);
        verify(mockStore, never()).beginTransaction(anyString());
        verify(mockStore, never()).getItem(anyString());
    }

    @Test
    void updateValidationFailureResultsForDevice_preservesFirstValidatedTime() {
        ValidationFailureResult existing = buildResult("r", "d", Map.of(), 5);
        Timestamp original = existing.getFirstValidatedTime();

        when(mockStore.beginTransaction("d")).thenReturn(mockTransaction);
        Map<String, List<Map<String, String>>> newRs = Map.of("k", List.of(Map.of("a", "b")));
        dao.updateValidationFailureResultsForDevice(existing, "d", newRs, mockScope);

        assertEquals(original, existing.getFirstValidatedTime());
    }
}
