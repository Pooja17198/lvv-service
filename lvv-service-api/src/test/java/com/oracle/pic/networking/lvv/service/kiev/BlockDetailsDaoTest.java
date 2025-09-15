package com.oracle.pic.networking.lvv.service.kiev;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.oracle.pic.commons.exceptions.server.ErrorCode;
import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.kiev.Transaction;
import com.oracle.pic.kiev.mapping.*;
import com.oracle.pic.kiev.mapping.token.PaginationTokenSerializer;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class BlockDetailsDaoTest {

    @Mock ConfigurationStore<BlockDetails.Block, BlockDetails> blockDetailsStore;
    @Mock MappedHashBucket<BlockDetails.Block, BlockDetails> blockDetailsProvider;
    @Mock Index<BlockDetails.ProjectIdIndex, BlockDetails> projectIdIndex;
    @Mock PaginationTokenSerializer serializer;
    @Mock MetricsScope metricsScope;
    @Mock Transaction transaction;

    BlockDetailsDao dao;
    BlockDetails blockDetails;
    BlockDetails.Block block;

    @BeforeEach
    void setUp() {
        when(blockDetailsProvider.getIndex(
                        eq(BlockDetails.PROJECT_ID_COLUMN_NAME),
                        eq(BlockDetails.ProjectIdIndex.class)))
                .thenReturn(projectIdIndex);

        dao = new BlockDetailsDao(blockDetailsStore, serializer, blockDetailsProvider);

        block = BlockDetails.Block.builder().blockNumber("b123").building("bldg").build();
        blockDetails = BlockDetails.builder().block(block).projectId("proj1").build();
    }

    // --- addBlockDetails ---
    @Test
    void testAddBlockDetails_success() {
        List<BlockDetails> details = List.of(blockDetails);

        try (MockedStatic<MetricsScope> mScope = mockStatic(MetricsScope.class)) {
            mScope.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);

            dao.addBlockDetails(details, transaction);

            verify(blockDetailsStore).createItem(transaction, blockDetails);
        }
    }

    @Test
    void testAddBlockDetails_tooManyBlocks() {
        List<BlockDetails> blocks =
                Collections.nCopies(51, blockDetails); // MAX_WRITES_PER_TRANSACTION is 50

        try (MockedStatic<MetricsScope> mScope = mockStatic(MetricsScope.class)) {
            mScope.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);

            RenderableException ex =
                    assertThrows(
                            RenderableException.class,
                            () -> dao.addBlockDetails(blocks, transaction));
            assertEquals(ErrorCode.InvalidParameter, ex.getErrorCode());
        }
    }

    // --- deleteBlockDetails ---
    @Test
    void testDeleteBlockDetails_success() throws Exception {

        // Return a single blockDetails for the project
        try (MockedStatic<MetricsScope> mScope = mockStatic(MetricsScope.class)) {
            mScope.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);

            try (MockedStatic<BlockDetailsDao> helperMock =
                    mockStatic(BlockDetailsDao.class, CALLS_REAL_METHODS)) {
                // The method under test will call getBlockDetailsForProject
                BlockDetailsDao realDao = spy(dao);
                doReturn(List.of(blockDetails))
                        .when(realDao)
                        .getBlockDetailsForProject(anyString());
                realDao.deleteBlockDetails("proj1", transaction);
                verify(blockDetailsStore).deleteItem(transaction, block);
            }
        }
    }

    @Test
    void testDeleteBlockDetails_tooManyBlocks() {

        List<BlockDetails> blocks = Collections.nCopies(51, blockDetails);
        BlockDetailsDao realDao = spy(dao);
        try (MockedStatic<MetricsScope> mScope = mockStatic(MetricsScope.class)) {
            mScope.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);

            doReturn(blocks).when(realDao).getBlockDetailsForProject("proj1");

            RenderableException ex =
                    assertThrows(
                            RenderableException.class,
                            () -> realDao.deleteBlockDetails("proj1", transaction));
            assertEquals(ErrorCode.IncorrectState, ex.getErrorCode());
        }
    }

    @Test
    void testDeleteBlockDetails_zeroBlocks() {

        BlockDetailsDao realDao = spy(dao);
        try (MockedStatic<MetricsScope> mScope = mockStatic(MetricsScope.class)) {
            mScope.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);

            doReturn(Collections.emptyList()).when(realDao).getBlockDetailsForProject("proj1");

            RenderableException ex =
                    assertThrows(
                            RenderableException.class,
                            () -> realDao.deleteBlockDetails("proj1", transaction));
            assertEquals(ErrorCode.IncorrectState, ex.getErrorCode());
        }
    }

    // --- getBlockDetails ---
    @Test
    void testGetBlockDetails_success() {
        when(blockDetailsStore.getItem(any())).thenReturn(blockDetails);

        try (MockedStatic<MetricsScope> mScope = mockStatic(MetricsScope.class)) {
            mScope.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);
            when(metricsScope.recordSuccess()).thenReturn(metricsScope);

            BlockDetails found = dao.getBlockDetails("bldg", "b123");
            assertEquals(blockDetails, found);
        }
    }

    @Test
    void testGetBlockDetails_errorReturnsNull() {
        when(blockDetailsStore.getItem(any())).thenThrow(new RuntimeException("fail"));

        try (MockedStatic<MetricsScope> mScope = mockStatic(MetricsScope.class)) {
            mScope.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);

            BlockDetails found = dao.getBlockDetails("bldg", "b123");
            assertNull(found);
        }
    }

    // --- getBlockDetailsForProject ---
    @Test
    void testGetBlockDetailsForProject_pagedResults() {
        BlockDetails.ProjectIdIndex prefix =
                BlockDetails.ProjectIdIndex.builder().projectId("proj1").build();
        ScanPage<BlockDetails> page1 = mock(ScanPage.class);
        ScanPage<BlockDetails> page2 = mock(ScanPage.class);
        PaginationToken token = mock(PaginationToken.class);

        BlockDetails block1 = BlockDetails.builder().projectId("proj1").block(block).build();
        BlockDetails block2 = BlockDetails.builder().projectId("proj1").block(block).build();

        when(projectIdIndex.beginPrefixScan(prefix, 1000)).thenReturn(page1);
        when(page1.results()).thenReturn(List.of(block1));
        when(page1.hasNext()).thenReturn(true);
        when(page1.paginationToken()).thenReturn(token);
        when(projectIdIndex.scan(token)).thenReturn(page2);
        when(page2.results()).thenReturn(List.of(block2));
        when(page2.hasNext()).thenReturn(false);

        try (MockedStatic<MetricsScope> mScope = mockStatic(MetricsScope.class)) {
            mScope.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);
            when(metricsScope.recordSuccess()).thenReturn(metricsScope);

            List<BlockDetails> result = dao.getBlockDetailsForProject("proj1");
            assertEquals(List.of(block1, block2), result);
        }
    }
}
