// package com.oracle.pic.networking.lvv.service.kiev;
//
// import static org.junit.jupiter.api.Assertions.*;
// import static org.mockito.ArgumentMatchers.any;
// import static org.mockito.ArgumentMatchers.anyInt;
// import static org.mockito.Mockito.*;
//
// import com.google.common.collect.Lists;
// import com.oracle.pic.commons.metrics.MetricsScope;
// import com.oracle.pic.kiev.Transaction;
// import com.oracle.pic.kiev.mapping.Index;
// import com.oracle.pic.kiev.mapping.MappedHashBucket;
// import com.oracle.pic.kiev.mapping.ScanPage;
// import com.oracle.pic.kiev.mapping.token.PaginationTokenSerializer;
// import java.sql.Timestamp;
// import java.time.Instant;
// import java.util.List;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.ExtendWith;
// import org.mockito.Mock;
// import org.mockito.junit.jupiter.MockitoExtension;
//
// @ExtendWith(MockitoExtension.class)
// class ValidationFailureResultDaoTest {
//
//    @Mock
//    private ConfigurationStore<ValidationFailureResult.LinkSource, ValidationFailureResult>
//            validationResultStore;
//
//    @Mock
//    private MappedHashBucket<ValidationFailureResult.LinkSource, ValidationFailureResult>
//            validationResultProvider;
//
//    @Mock private PaginationTokenSerializer serializer;
//
//    @Mock
//    private Index<ValidationFailureResult.RackSerialIndex, ValidationFailureResult>
// rackSerialIndex;
//
//    @Mock private Transaction transaction;
//
//    @Mock MetricsScope metricsScope;
//
//    private ValidationFailureResultDao dao;
//    private ValidationFailureResult result;
//    private List<ValidationFailureResult> results;
//
//    @BeforeEach
//    void setup() {
//        result =
//                ValidationFailureResult.builder()
//                        .linkSource(ValidationFailureResult.LinkSource.builder().build())
//                        .rackSerial("rackSerial")
//                        .linkStatus(LinkStatus.DOWN)
//                        .lastValidatedTime(Timestamp.from(Instant.now()))
//                        .build();
//        results = Lists.newArrayList(result);
//
//        // Stub BEFORE DAO construction so rackSerialIndex is initialized properly
//        when(validationResultProvider.getIndex(
//                        ValidationFailureResult.RACK_SERIAL_COLUMN_NAME,
//                        ValidationFailureResult.RackSerialIndex.class))
//                .thenReturn(rackSerialIndex);
//
//        dao =
//                new ValidationFailureResultDao(
//                        validationResultStore, serializer, validationResultProvider);
//    }
//
//    @Test
//    void testAddValidationFailureResultSuccess() throws Exception {
//
//        ScanPage<ValidationFailureResult> emptyPage = mock(ScanPage.class);
//        when(emptyPage.results()).thenReturn(List.of());
//        when(emptyPage.hasNext()).thenReturn(false);
//        when(rackSerialIndex.beginPrefixScan(any(), anyInt())).thenReturn(emptyPage);
//
//        when(validationResultStore.beginTransaction(any())).thenReturn(transaction);
//        when(validationResultStore.createItem(any(), any())).thenReturn(result);
//
//        dao.addValidationFailureResultsForRack(results, "rackSerial", metricsScope);
//
//        verify(validationResultStore).createItem(any(), any());
//        verify(transaction).commit();
//    }
//
//    @Test
//    void testAddValidationFailureResultUpdate() throws Exception {
//        ScanPage<ValidationFailureResult> page = mock(ScanPage.class);
//        when(page.results()).thenReturn(results);
//        when(page.hasNext()).thenReturn(false);
//        when(rackSerialIndex.beginPrefixScan(any(), anyInt())).thenReturn(page);
//
//        when(validationResultStore.beginTransaction(any())).thenReturn(transaction);
//        when(validationResultStore.updateItem(any(), any())).thenReturn(result);
//        when(validationResultStore.getItem(any())).thenReturn(result);
//
//        dao.addValidationFailureResultsForRack(results, "rackSerial", metricsScope);
//
//        verify(validationResultStore).updateItem(any(), any());
//        verify(transaction).commit();
//    }
//
//        @Test
//        void testAddValidationFailureResultCommitConflict() throws Exception {
//            ScanPage<ValidationFailureResult> emptyPage = mock(ScanPage.class);
//            when(emptyPage.results()).thenReturn(List.of());
//            when(emptyPage.hasNext()).thenReturn(false);
//            when(rackSerialIndex.beginPrefixScan(any(), anyInt())).thenReturn(emptyPage);
//
//            when(validationResultStore.beginTransaction(any())).thenReturn(transaction);
//            when(validationResultStore.createItem(any(), any())).thenReturn(result);
//            doThrow(CommitConflictException.class).when(transaction).commit();
//
//            when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);
//
//            assertThrows(
//                    Exception.class,
//                    () -> dao.addValidationFailureResultsForRack(results, "rackSerial",
//     metricsScope));
//        }
//
//    @Test
//    void testGetValidationFailuresByRackSuccess() throws Exception {
//        ScanPage<ValidationFailureResult> page1 = mock(ScanPage.class);
//        when(page1.results()).thenReturn(results);
//        when(page1.hasNext()).thenReturn(false);
//        when(rackSerialIndex.beginPrefixScan(any(), anyInt())).thenReturn(page1);
//
//        List<ValidationFailureResult> validationFailures =
//                dao.getValidationFailuresByRack("rackSerial", true);
//
//        assertNotNull(validationFailures);
//        assertEquals(1, validationFailures.size());
//        verify(rackSerialIndex).beginPrefixScan(any(), anyInt());
//    }
//
//    @Test
//    void testGetValidationFailuresByRackException() throws Exception {
//        when(rackSerialIndex.beginPrefixScan(any(), anyInt())).thenThrow(new RuntimeException());
//
//        assertThrows(Exception.class, () -> dao.getValidationFailuresByRack("rackSerial", true));
//    }
// }
