package com.oracle.pic.networking.lvv.service.kiev;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.kiev.Transaction;
import com.oracle.pic.kiev.exceptions.CommitConflictException;
import com.oracle.pic.kiev.mapping.Index;
import com.oracle.pic.kiev.mapping.MappedHashBucket;
import com.oracle.pic.kiev.mapping.ScanPage;
import com.oracle.pic.kiev.mapping.token.PaginationTokenSerializer;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import com.oracle.pic.networking.lvv.service.kiev.ValidationFailureResult.LinkSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.*;
import org.mockito.*;

public class ValidationFailureResultDaoTest {

    @Mock ConfigurationStore<LinkSource, ValidationFailureResult> mockStore;
    @Mock MappedHashBucket<LinkSource, ValidationFailureResult> mockProvider;
    @Mock PaginationTokenSerializer mockSerializer;

    @Mock
    Index<ValidationFailureResult.RackSerialIndex, ValidationFailureResult> mockRackSerialIndex;

    @Mock Transaction mockTransaction;
    @Mock ScanPage<ValidationFailureResult> mockScanPage;
    @Mock ScanPage<ValidationFailureResult> mockScanPage2;
    @Mock MetricsScope mockScope;

    ValidationFailureResultDao dao;

    ValidationFailureResult linkUp;
    ValidationFailureResult linkDown;
    ValidationFailureResult linkNullStatus;

    LinkSource linkSourceA;
    LinkSource linkSourceB;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);

        doReturn(mockRackSerialIndex).when(mockProvider).getIndex(any(), any());

        dao = new ValidationFailureResultDao(mockStore, mockSerializer, mockProvider);

        linkSourceA = LinkSource.builder().deviceAName("devA").deviceAPort("p1").build();
        linkSourceB = LinkSource.builder().deviceAName("devA").deviceAPort("p2").build();

        linkUp =
                ValidationFailureResult.builder()
                        .linkSource(linkSourceA)
                        .rackSerial("rack1")
                        .linkStatus(LinkStatus.UP)
                        .lastValidatedTime(Timestamp.from(Instant.now()))
                        .build();

        linkDown =
                ValidationFailureResult.builder()
                        .linkSource(linkSourceB)
                        .rackSerial("rack1")
                        .linkStatus(LinkStatus.DOWN)
                        .lastValidatedTime(Timestamp.from(Instant.now()))
                        .build();

        linkNullStatus =
                ValidationFailureResult.builder()
                        .linkSource(
                                LinkSource.builder().deviceAName("devX").deviceAPort("pX").build())
                        .rackSerial("rack2")
                        .lastValidatedTime(Timestamp.from(Instant.now()))
                        .build();
    }

    @Test
    void testUpdateValidationFailureResultsForDevices_empty() {
        dao.addUpdateValidationFailureResultsForDevices(Collections.emptyList(), mockScope, "regX");
        verify(mockStore, never()).beginTransaction(any());
    }

    @Test
    void testUpdateValidationFailureResultsForDevices_mixed() {
        // First device has DOWN, next one has only UP
        ValidationFailureResult d1 =
                ValidationFailureResult.builder()
                        .linkSource(LinkSource.builder().deviceAName("d1").deviceAPort("A").build())
                        .rackSerial("rackX")
                        .linkStatus(LinkStatus.DOWN)
                        .lastValidatedTime(Timestamp.from(Instant.now()))
                        .build();

        ValidationFailureResult d2 =
                ValidationFailureResult.builder()
                        .linkSource(LinkSource.builder().deviceAName("d1").deviceAPort("B").build())
                        .rackSerial("rackX")
                        .linkStatus(LinkStatus.UP)
                        .lastValidatedTime(Timestamp.from(Instant.now()))
                        .build();

        ValidationFailureResult d3 =
                ValidationFailureResult.builder()
                        .linkSource(LinkSource.builder().deviceAName("d2").deviceAPort("C").build())
                        .rackSerial("rackX")
                        .linkStatus(LinkStatus.UP)
                        .lastValidatedTime(Timestamp.from(Instant.now()))
                        .build();

        List<ValidationFailureResult> input = List.of(d1, d2, d3);

        when(mockProvider.beginPrefixScan(any(), anyInt()))
                .thenReturn(mockScanPage)
                .thenReturn(null);
        when(mockScanPage.results()).thenReturn(new ArrayList<>());
        when(mockScanPage.hasNext()).thenReturn(false);

        when(mockRackSerialIndex.beginPrefixScan(any(), anyInt())).thenReturn(null);

        when(mockStore.beginTransaction(any())).thenReturn(mockTransaction);

        assertThrows(
                RenderableException.class,
                () ->
                        dao.addUpdateValidationFailureResultsForDevices(
                                input, mockScope, "region12"));
    }

    @Test
    void testGetValidationFailuresByDevice_onlyDown() {
        when(mockProvider.beginPrefixScan(any(), anyInt()))
                .thenReturn(mockScanPage)
                .thenReturn(null);

        ValidationFailureResult down =
                ValidationFailureResult.builder()
                        .linkSource(
                                LinkSource.builder().deviceAName("devA").deviceAPort("p1").build())
                        .rackSerial("rack1")
                        .linkStatus(LinkStatus.DOWN)
                        .lastValidatedTime(Timestamp.from(Instant.now()))
                        .build();
        ValidationFailureResult up =
                ValidationFailureResult.builder()
                        .linkSource(
                                LinkSource.builder().deviceAName("devA").deviceAPort("p2").build())
                        .rackSerial("rack1")
                        .linkStatus(LinkStatus.UP)
                        .lastValidatedTime(Timestamp.from(Instant.now()))
                        .build();

        when(mockScanPage.results()).thenReturn(List.of(down, up));
        when(mockScanPage.hasNext()).thenReturn(false);

        List<ValidationFailureResult> found = dao.getValidationFailuresByDevice("devA", true);
        assertEquals(1, found.size());
        assertEquals(LinkStatus.DOWN, found.get(0).getLinkStatus());
    }

    @Test
    void testGetValidationFailuresByDevice_nullDeviceName() {
        assertThrows(
                NullPointerException.class, () -> dao.getValidationFailuresByDevice(null, true));
    }

    @Test
    void testGetValidationFailuresByRack_basic() {
        when(mockRackSerialIndex.beginPrefixScan(any(), anyInt()))
                .thenReturn(mockScanPage)
                .thenReturn(null);

        ValidationFailureResult v1 =
                ValidationFailureResult.builder()
                        .linkSource(
                                LinkSource.builder().deviceAName("d9").deviceAPort("f2").build())
                        .rackSerial("rackZZ")
                        .linkStatus(LinkStatus.UP)
                        .lastValidatedTime(Timestamp.from(Instant.now()))
                        .build();

        ValidationFailureResult v2 =
                ValidationFailureResult.builder()
                        .linkSource(
                                LinkSource.builder().deviceAName("d9").deviceAPort("f3").build())
                        .rackSerial("rackZZ")
                        .linkStatus(LinkStatus.DOWN)
                        .lastValidatedTime(Timestamp.from(Instant.now()))
                        .build();

        when(mockScanPage.results()).thenReturn(List.of(v1, v2));
        when(mockScanPage.hasNext()).thenReturn(false);

        List<ValidationFailureResult> found = dao.getValidationFailuresByRack("rackZZ", false);
        assertEquals(2, found.size());
    }

    @Test
    void testGetValidationFailuresByRack_onlyDown() {
        when(mockRackSerialIndex.beginPrefixScan(any(), anyInt()))
                .thenReturn(mockScanPage)
                .thenReturn(null);

        ValidationFailureResult v1 =
                ValidationFailureResult.builder()
                        .linkSource(LinkSource.builder().deviceAName("d1").deviceAPort("x").build())
                        .rackSerial("rackKX")
                        .linkStatus(LinkStatus.UP)
                        .lastValidatedTime(Timestamp.from(Instant.now()))
                        .build();
        ValidationFailureResult v2 =
                ValidationFailureResult.builder()
                        .linkSource(LinkSource.builder().deviceAName("d1").deviceAPort("y").build())
                        .rackSerial("rackKX")
                        .linkStatus(LinkStatus.DOWN)
                        .lastValidatedTime(Timestamp.from(Instant.now()))
                        .build();

        when(mockScanPage.results()).thenReturn(List.of(v1, v2));
        when(mockScanPage.hasNext()).thenReturn(false);

        List<ValidationFailureResult> found = dao.getValidationFailuresByRack("rackKX", true);
        assertEquals(1, found.size());
        assertEquals(LinkStatus.DOWN, found.get(0).getLinkStatus());
    }

    @Test
    void testGetValidationFailuresByRack_nullRackSerial() {
        assertThrows(NullPointerException.class, () -> dao.getValidationFailuresByRack(null, true));
    }

    @Test
    void testAddUpdateValidationFailureResultsForDevices_createsNewDownLinks() throws Exception {
        // No existing links for the device
        when(mockProvider.beginPrefixScan(any(), anyInt())).thenReturn(mockScanPage);
        when(mockScanPage.results()).thenReturn(new ArrayList<>());
        when(mockScanPage.hasNext()).thenReturn(false);

        when(mockStore.beginTransaction(any())).thenReturn(mockTransaction);

        ValidationFailureResult l1 =
                ValidationFailureResult.builder()
                        .linkSource(
                                LinkSource.builder().deviceAName("devZ").deviceAPort("a").build())
                        .rackSerial("rackZ")
                        .linkStatus(LinkStatus.DOWN)
                        .build();
        ValidationFailureResult l2 =
                ValidationFailureResult.builder()
                        .linkSource(
                                LinkSource.builder().deviceAName("devZ").deviceAPort("b").build())
                        .rackSerial("rackZ")
                        .linkStatus(LinkStatus.DOWN)
                        .build();

        dao.addUpdateValidationFailureResultsForDevices(List.of(l1, l2), mockScope, "regionX");

        // Both should be created and have lastValidatedTime set
        verify(mockStore, times(1)).beginTransaction(any());
        verify(mockStore, times(2))
                .createItem(eq(mockTransaction), any(ValidationFailureResult.class));
        verify(mockTransaction, times(1)).commit();
        assertNotNull(l1.getLastValidatedTime());
        assertNotNull(l2.getLastValidatedTime());
    }

    @Test
    void testAddUpdateValidationFailureResultsForDevices_updatesExistingDownLink() {
        // One existing link for the device
        ValidationFailureResult existing =
                ValidationFailureResult.builder()
                        .linkSource(
                                LinkSource.builder().deviceAName("devU").deviceAPort("p1").build())
                        .rackSerial("rackU")
                        .linkStatus(LinkStatus.DOWN)
                        .lastValidatedTime(Timestamp.from(Instant.now().minusSeconds(3600)))
                        .build();

        when(mockProvider.beginPrefixScan(any(), anyInt())).thenReturn(mockScanPage);
        when(mockScanPage.results()).thenReturn(List.of(existing));
        when(mockScanPage.hasNext()).thenReturn(false);

        when(mockStore.beginTransaction(any())).thenReturn(mockTransaction);
        // Return a non-null item when updating existing links to UP
        when(mockStore.getItem(any())).thenReturn(existing);

        ValidationFailureResult update =
                ValidationFailureResult.builder()
                        .linkSource(
                                LinkSource.builder().deviceAName("devU").deviceAPort("p1").build())
                        .rackSerial("rackU")
                        .linkStatus(LinkStatus.DOWN)
                        .lastValidatedTime(Timestamp.from(Instant.now().minusSeconds(7200)))
                        .build();

        dao.addUpdateValidationFailureResultsForDevices(List.of(update), mockScope, "regU");

        // Ensure the DOWN link provided is used for update
        verify(mockStore, atLeastOnce()).updateItem(eq(mockTransaction), same(update));
        assertNotNull(update.getLastValidatedTime());
    }

    @Test
    void testAddUpdateValidationFailureResultsForDevices_existingLinksBatchingUpdatesToUp()
            throws Exception {
        // Create 51 existing links so batching (size 50) triggers 2 transactions
        List<ValidationFailureResult> existing = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            existing.add(
                    ValidationFailureResult.builder()
                            .linkSource(
                                    LinkSource.builder()
                                            .deviceAName("devBatch")
                                            .deviceAPort("p" + i)
                                            .build())
                            .rackSerial("rackB")
                            .linkStatus(LinkStatus.DOWN)
                            .lastValidatedTime(Timestamp.from(Instant.now()))
                            .build());
        }

        when(mockProvider.beginPrefixScan(any(), anyInt())).thenReturn(mockScanPage);
        when(mockScanPage.results()).thenReturn(existing);
        when(mockScanPage.hasNext()).thenReturn(false);

        when(mockStore.beginTransaction(any())).thenReturn(mockTransaction);
        // Return a non-null item for each getItem call
        when(mockStore.getItem(any()))
                .thenReturn(
                        ValidationFailureResult.builder()
                                .linkSource(
                                        LinkSource.builder()
                                                .deviceAName("devBatch")
                                                .deviceAPort("px")
                                                .build())
                                .rackSerial("rackB")
                                .linkStatus(LinkStatus.DOWN)
                                .lastValidatedTime(Timestamp.from(Instant.now()))
                                .build());

        // Provide an input list for the same device where first link is UP to skip DOWN updates
        ValidationFailureResult placeholderUp =
                ValidationFailureResult.builder()
                        .linkSource(
                                LinkSource.builder()
                                        .deviceAName("devBatch")
                                        .deviceAPort("placeholder")
                                        .build())
                        .rackSerial("rackB")
                        .linkStatus(LinkStatus.UP)
                        .lastValidatedTime(Timestamp.from(Instant.now()))
                        .build();

        dao.addUpdateValidationFailureResultsForDevices(List.of(placeholderUp), mockScope, "regB");

        // Two batches (50 + 1)
        verify(mockStore, times(2)).beginTransaction(any());
        verify(mockStore, times(51))
                .updateItem(eq(mockTransaction), any(ValidationFailureResult.class));
        verify(mockTransaction, times(2)).commit();
    }

    @Test
    void
            testAddUpdateValidationFailureResultsForDevices_updateDownCommitFailure_emitsMetricAndAborts()
                    throws Exception {
        // No existing links
        when(mockProvider.beginPrefixScan(any(), anyInt())).thenReturn(mockScanPage);
        when(mockScanPage.results()).thenReturn(new ArrayList<>());
        when(mockScanPage.hasNext()).thenReturn(false);

        when(mockStore.beginTransaction(any())).thenReturn(mockTransaction);
        // Fail commit during DOWN updates
        doThrow(new CommitConflictException("conflict")).when(mockTransaction).commit();

        ValidationFailureResult l1 =
                ValidationFailureResult.builder()
                        .linkSource(
                                LinkSource.builder().deviceAName("devErr").deviceAPort("a").build())
                        .rackSerial("rackErr")
                        .linkStatus(LinkStatus.DOWN)
                        .lastValidatedTime(Timestamp.from(Instant.now()))
                        .build();

        assertThrows(
                RenderableException.class,
                () -> dao.addUpdateValidationFailureResultsForDevices(List.of(l1), mockScope, "r"));

        // Metric emitted and transaction aborted
        verify(mockScope, times(1))
                .emit(eq(MetricNames.AddValidationResults.KievResultUpdateFailure.name()), eq(1.0));
        verify(mockTransaction, times(1)).abort();
    }

    @Test
    void testGetValidationFailuresByRack_nullIndex_throws() {
        @SuppressWarnings("unchecked")
        MappedHashBucket<LinkSource, ValidationFailureResult> provider2 =
                mock(MappedHashBucket.class);
        when(provider2.getIndex(any(), any())).thenReturn(null);
        ValidationFailureResultDao dao2 =
                new ValidationFailureResultDao(mockStore, mockSerializer, provider2);
        assertThrows(
                NullPointerException.class, () -> dao2.getValidationFailuresByRack("rack1", false));
    }
}
