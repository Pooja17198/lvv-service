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
    void testAddValidationFailureResultsForRack_emptyResults() {
        dao.addValidationFailureResultsForRack(Collections.emptyList(), "rack1", mockScope, "regA");
        verify(mockScope).emit(eq(MetricNames.AddValidationResults.NoMoreFailures), eq(1.0));
        // Should not attempt to update or add
        verify(mockStore, never()).beginTransaction(any());
    }

    @Test
    void testAddValidationFailureResultsForRack_allNormal() {
        // Existing links present for this rack
        when(mockRackSerialIndex.beginPrefixScan(any(), anyInt()))
                .thenReturn(mockScanPage)
                .thenReturn(null);
        when(mockScanPage.results()).thenReturn(List.of(linkUp, linkDown));
        when(mockScanPage.hasNext()).thenReturn(false);

        List<ValidationFailureResult> addLinks = List.of(linkDown);
        when(mockStore.beginTransaction(any())).thenReturn(mockTransaction);
        when(mockStore.getItem(any())).thenReturn(linkUp);

        dao.addValidationFailureResultsForRack(addLinks, "rack1", mockScope, "regA");
        // Should update existing and add new
        verify(mockStore, atLeastOnce()).updateItem(any(), any());
    }

    @Test
    void testAddValidationFailureResultsForRack_commitConflict() throws CommitConflictException {
        // Setup
        when(mockRackSerialIndex.beginPrefixScan(any(), anyInt()))
                .thenReturn(mockScanPage)
                .thenReturn(null);
        when(mockScanPage.results()).thenReturn(List.of(linkUp));
        when(mockScanPage.hasNext()).thenReturn(false);

        List<ValidationFailureResult> addLinks = List.of(linkDown);
        when(mockStore.beginTransaction(any())).thenReturn(mockTransaction);
        when(mockStore.getItem(any())).thenReturn(linkUp);

        doThrow(new CommitConflictException("fail")).when(mockTransaction).commit();

        assertThrows(
                RenderableException.class,
                () -> dao.addValidationFailureResultsForRack(addLinks, "rack1", mockScope, "regA"));
    }

    @Test
    void testAddValidationFailureResultsForRack_duplicateKeyException()
            throws CommitConflictException {
        // Setup
        when(mockRackSerialIndex.beginPrefixScan(any(), anyInt()))
                .thenReturn(mockScanPage)
                .thenReturn(null);
        when(mockScanPage.results()).thenReturn(List.of(linkUp));
        when(mockScanPage.hasNext()).thenReturn(false);

        List<ValidationFailureResult> addLinks = List.of(linkDown);
        when(mockStore.beginTransaction(any())).thenReturn(mockTransaction);
        when(mockStore.getItem(any())).thenReturn(linkUp);

        doThrow(new DuplicateKeyException("fail")).when(mockTransaction).commit();

        assertThrows(
                RenderableException.class,
                () -> dao.addValidationFailureResultsForRack(addLinks, "rack1", mockScope, "regA"));
    }

    @Test
    void testUpdateValidationFailureResultsForDevices_empty() {
        dao.updateValidationFailureResultsForDevices(Collections.emptyList(), mockScope, "regX");
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
                () -> dao.updateValidationFailureResultsForDevices(input, mockScope, "region12"));
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
}
