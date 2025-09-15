package com.oracle.pic.networking.lvv.service.kiev;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.oracle.pic.commons.exceptions.server.ErrorCode;
import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.kiev.Transaction;
import com.oracle.pic.kiev.exceptions.CommitConflictException;
import com.oracle.pic.kiev.mapping.*;
import com.oracle.pic.kiev.mapping.token.PaginationTokenSerializer;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class ProjectItemDaoTest {

    @Mock ConfigurationStore<String, ProjectItem> projectItemStore;
    @Mock BlockDetailsDao blockDetailsDao;
    @Mock PaginationTokenSerializer serializer;
    @Mock MappedHashBucket<String, ProjectItem> projectItemProvider;
    @Mock Index<ProjectItem.VendorNameIndex, ProjectItem> vendorNameIndex;
    @Mock MetricsScope metricsScope;
    @Mock Transaction transaction;

    ProjectItemDao dao;
    final String projectId = "pid1";
    final String vendorName = "testVendor";
    ProjectItem projItem;

    @BeforeEach
    void setUp() {
        // Simulate provider/index setup:
        when(projectItemProvider.getIndex(
                        eq(ProjectItem.VENDOR_COLUMN_NAME), eq(ProjectItem.VendorNameIndex.class)))
                .thenReturn(vendorNameIndex);
        dao =
                new ProjectItemDao(
                        projectItemStore, serializer, projectItemProvider, blockDetailsDao);

        projItem = ProjectItem.builder().projectId(projectId).vendorName(vendorName).build();
    }

    // --- checkBlockIsUnassigned ---
    @Test
    void testCheckBlockIsUnassigned_nullBlockDetails_returnsTrue() {
        BlockDetails blockDetailsToAdd =
                BlockDetails.builder()
                        .block(
                                BlockDetails.Block.builder()
                                        .blockNumber("b1")
                                        .building("bldg")
                                        .build())
                        .projectId("proj1")
                        .build();

        when(blockDetailsDao.getBlockDetails("bldg", "b1")).thenReturn(null);
        assertNull(dao.checkBlockIsUnassigned(blockDetailsToAdd));
    }

    @Test
    void testCheckBlockIsUnassigned_blockExists_returnsFalse() {
        BlockDetails blockDetailsToAdd =
                BlockDetails.builder()
                        .block(
                                BlockDetails.Block.builder()
                                        .blockNumber("b1")
                                        .building("bldg")
                                        .build())
                        .projectId("proj1")
                        .build();
        BlockDetails existingBlock =
                BlockDetails.builder()
                        .block(
                                BlockDetails.Block.builder()
                                        .blockNumber("b1")
                                        .building("bldg")
                                        .build())
                        .projectId("proj2")
                        .build();
        when(blockDetailsDao.getBlockDetails("bldg", "b1")).thenReturn(existingBlock);
        assertNotNull(dao.checkBlockIsUnassigned(blockDetailsToAdd));
    }

    // --- addUpdateProjectItem ---
    @Test
    void testAddProjectItem_newItem() throws Exception {
        BlockDetails blockDetailsToAdd =
                BlockDetails.builder()
                        .block(
                                BlockDetails.Block.builder()
                                        .blockNumber("b1")
                                        .building("bldg")
                                        .build())
                        .projectId("proj1")
                        .build();
        List<BlockDetails> detailList = List.of(blockDetailsToAdd);

        when(projectItemStore.beginTransaction(projectId)).thenReturn(transaction);
        when(blockDetailsDao.getBlockDetails(anyString(), anyString())).thenReturn(null);

        try (MockedStatic<MetricsScope> mScope = mockStatic(MetricsScope.class)) {
            mScope.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);

            when(projectItemStore.getItem(projectId)).thenThrow(new RuntimeException("not found"));

            dao.addProjectItem(projItem, detailList, metricsScope);

            verify(blockDetailsDao, never()).deleteBlockDetails(any(), any());
            verify(transaction).commit();
        }
    }

    @Test
    void testUpdateProjectItem_newItem() throws Exception {
        BlockDetails blockDetails = mock(BlockDetails.class);
        List<BlockDetails> detailList = List.of(blockDetails);

        when(blockDetails.getBlock())
                .thenReturn(
                        BlockDetails.Block.builder().blockNumber("b1").building("bldg").build());
        when(projectItemStore.beginTransaction(projectId)).thenReturn(transaction);

        when(projectItemStore.getItem(projectId)).thenReturn(projItem);

        dao.updateProjectItem(projItem, detailList, metricsScope);

        verify(blockDetailsDao).deleteBlockDetails(any(), any());
        verify(transaction).commit();
    }

    @Test
    void testAddProjectItem_blockAlreadyAssigned_throws() {
        // Create fully stubbed BlockDetails and Block
        BlockDetails blockDetailsToAdd =
                BlockDetails.builder()
                        .block(
                                BlockDetails.Block.builder()
                                        .blockNumber("b1")
                                        .building("bldg")
                                        .build())
                        .projectId("proj1")
                        .build();
        BlockDetails blockDetailsExisting =
                BlockDetails.builder()
                        .block(
                                BlockDetails.Block.builder()
                                        .blockNumber("b1")
                                        .building("bldg")
                                        .build())
                        .projectId("proj2")
                        .build();
        List<BlockDetails> detailList = List.of(blockDetailsToAdd);

        // This causes checkBlockIsUnassigned to return false (block is already assigned)
        when(blockDetailsDao.getBlockDetails(anyString(), anyString()))
                .thenReturn(blockDetailsExisting);
        when(projectItemStore.beginTransaction(projectId)).thenReturn(transaction);
        // projectItemStore.getItem must return something (not throw)

        when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);

        RenderableException ex =
                assertThrows(
                        RenderableException.class,
                        () -> dao.addProjectItem(projItem, detailList, metricsScope));
        assertEquals(ErrorCode.InvalidParameter, ex.getErrorCode());
    }

    @Test
    void testUpdateProjectItem_blockAlreadyAssigned_throws() {
        // Create fully stubbed BlockDetails and Block
        BlockDetails blockDetailsToUpdate =
                BlockDetails.builder()
                        .block(
                                BlockDetails.Block.builder()
                                        .blockNumber("b1")
                                        .building("bldg")
                                        .build())
                        .projectId("proj1")
                        .build();
        BlockDetails blockDetailsExisting =
                BlockDetails.builder()
                        .block(
                                BlockDetails.Block.builder()
                                        .blockNumber("b1")
                                        .building("bldg")
                                        .build())
                        .projectId("proj2")
                        .build();

        List<BlockDetails> detailList = List.of(blockDetailsToUpdate);

        // This causes checkBlockIsUnassigned to return false (block is already assigned)
        when(blockDetailsDao.getBlockDetails(anyString(), anyString()))
                .thenReturn(blockDetailsExisting);
        when(projectItemStore.beginTransaction(projectId)).thenReturn(transaction);
        // projectItemStore.getItem must return something (not throw)
        when(projectItemStore.getItem(anyString())).thenReturn(projItem);

        when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);

        RenderableException ex =
                assertThrows(
                        RenderableException.class,
                        () -> dao.updateProjectItem(projItem, detailList, metricsScope));
        assertEquals(ErrorCode.InvalidParameter, ex.getErrorCode());
    }

    @Test
    void testUpdateProjectItem_existingItem_triggersUpdate() {
        BlockDetails blockDetails = mock(BlockDetails.class);
        List<BlockDetails> detailList = List.of(blockDetails);
        when(blockDetails.getBlock())
                .thenReturn(
                        BlockDetails.Block.builder().blockNumber("b1").building("bldg").build());

        when(projectItemStore.beginTransaction(projectId)).thenReturn(transaction);

        // Simulate found item in store (triggers update, not create)
        when(projectItemStore.getItem(projectId)).thenReturn(projItem);

        dao.updateProjectItem(projItem, detailList, metricsScope);

        verify(projectItemStore).updateItem(eq(transaction), eq(projItem));
    }

    @Test
    void testAddProjectItem_commitConflictHandled() throws Exception {
        BlockDetails blockDetailsToAdd =
                BlockDetails.builder()
                        .block(
                                BlockDetails.Block.builder()
                                        .blockNumber("b1")
                                        .building("bldg")
                                        .build())
                        .projectId("proj1")
                        .build();

        List<BlockDetails> detailList = List.of(blockDetailsToAdd);

        when(projectItemStore.beginTransaction(projectId)).thenReturn(transaction);
        when(blockDetailsDao.getBlockDetails(anyString(), anyString())).thenReturn(null);
        when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);
        when(projectItemStore.getItem(projectId)).thenReturn(null);

        doThrow(new CommitConflictException("Conflict!")).when(transaction).commit();

        RenderableException ex =
                assertThrows(
                        RenderableException.class,
                        () -> dao.addProjectItem(projItem, detailList, metricsScope));
        assertEquals(ErrorCode.ExternalServerInvalidResponse, ex.getErrorCode());
        verify(transaction).abort();
    }

    @Test
    void testUpdateProjectItem_commitConflictHandled() throws Exception {
        BlockDetails blockDetails = mock(BlockDetails.class);
        List<BlockDetails> detailList = List.of(blockDetails);
        when(blockDetails.getBlock())
                .thenReturn(
                        BlockDetails.Block.builder().blockNumber("b1").building("bldg").build());

        when(projectItemStore.beginTransaction(projectId)).thenReturn(transaction);
        when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);
        when(projectItemStore.getItem(projectId)).thenReturn(projItem);

        doThrow(new CommitConflictException("Conflict!")).when(transaction).commit();

        RenderableException ex =
                assertThrows(
                        RenderableException.class,
                        () -> dao.updateProjectItem(projItem, detailList, metricsScope));
        assertEquals(ErrorCode.ExternalServerInvalidResponse, ex.getErrorCode());
        verify(transaction).abort();
    }

    // --- getProjectItem ---
    @Test
    void testGetProjectItem_success() {
        when(projectItemStore.getItem(projectId)).thenReturn(projItem);

        ProjectItem found = dao.getProjectItem(projectId);
        assertEquals(projItem, found);
    }

    // --- deleteProjectItem ---
    @Test
    void testDeleteProjectItem_success() throws Exception {
        when(projectItemStore.beginTransaction(projectId)).thenReturn(transaction);
        when(projectItemStore.deleteItem(transaction, projectId)).thenReturn(true);

        try (MockedStatic<MetricsScope> mScope = mockStatic(MetricsScope.class)) {
            mScope.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);

            dao.deleteProjectItem(projectId, metricsScope);
            verify(blockDetailsDao).deleteBlockDetails(projectId, transaction);
            verify(transaction).commit();
        }
    }

    @Test
    void testDeleteProjectItem_projectNotExist_throws() {
        when(projectItemStore.beginTransaction(projectId)).thenReturn(transaction);
        when(projectItemStore.deleteItem(transaction, projectId)).thenReturn(false);
        when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);

        RenderableException ex =
                assertThrows(
                        RenderableException.class,
                        () -> dao.deleteProjectItem(projectId, metricsScope));
        assertEquals(ErrorCode.NotAuthorizedOrNotFound, ex.getErrorCode());
    }

    @Test
    void testDeleteProjectItem_commitConflictHandled() throws Exception {
        when(projectItemStore.beginTransaction(projectId)).thenReturn(transaction);
        when(projectItemStore.deleteItem(transaction, projectId)).thenReturn(true);

        doThrow(new CommitConflictException("conflict")).when(transaction).commit();

        when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);

        RenderableException ex =
                assertThrows(
                        RenderableException.class,
                        () -> dao.deleteProjectItem(projectId, metricsScope));
        assertEquals(ErrorCode.ExternalServerInvalidResponse, ex.getErrorCode());
        verify(transaction).abort();
    }

    // --- getProjectItemsForVendor ---
    @Test
    void testGetProjectItemsForVendor_pagedResults() {
        String vname = "testVendor";
        ProjectItem.VendorNameIndex prefix =
                ProjectItem.VendorNameIndex.builder().vendorName(vname).build();
        ScanPage<ProjectItem> page1 = mock(ScanPage.class);
        ScanPage<ProjectItem> page2 = mock(ScanPage.class);
        PaginationToken token = mock(PaginationToken.class);

        ProjectItem item1 = ProjectItem.builder().projectId("proj1").vendorName(vname).build();
        ProjectItem item2 = ProjectItem.builder().projectId("proj1").vendorName(vname).build();

        when(vendorNameIndex.beginPrefixScan(prefix, 1000)).thenReturn(page1);
        when(page1.results()).thenReturn(List.of(item1));
        when(page1.hasNext()).thenReturn(true);
        when(page1.paginationToken()).thenReturn(token);
        when(vendorNameIndex.scan(token)).thenReturn(page2);
        when(page2.results()).thenReturn(List.of(item2));
        when(page2.hasNext()).thenReturn(false);

        List<ProjectItem> result = dao.getProjectItemsForVendor(vname);
        assertEquals(List.of(item1, item2), result);
    }

    @Test
    void testCheckBlockIsUnassigned_blockAssignedToSelf_returnsNull() {
        BlockDetails blockDetailsToAdd =
                BlockDetails.builder()
                        .block(
                                BlockDetails.Block.builder()
                                        .blockNumber("b1")
                                        .building("bldg")
                                        .build())
                        .projectId("proj1")
                        .build();
        when(blockDetailsDao.getBlockDetails("bldg", "b1")).thenReturn(blockDetailsToAdd);
        assertNull(dao.checkBlockIsUnassigned(blockDetailsToAdd));
    }

    @Test
    void testAddProjectItem_projectAlreadyExists_throws() {
        BlockDetails blockDetailsToAdd =
                BlockDetails.builder()
                        .block(
                                BlockDetails.Block.builder()
                                        .blockNumber("b1")
                                        .building("bldg")
                                        .build())
                        .projectId("proj1")
                        .build();
        List<BlockDetails> detailList = List.of(blockDetailsToAdd);

        when(projectItemStore.beginTransaction(projectId)).thenReturn(transaction);
        when(projectItemStore.getItem(projectId)).thenReturn(projItem); // already exists

        when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);

        RenderableException ex =
                assertThrows(
                        RenderableException.class,
                        () -> dao.addProjectItem(projItem, detailList, metricsScope));
        assertEquals(ErrorCode.ResourceAlreadyExists, ex.getErrorCode());
    }

    @Test
    void testUpdateProjectItem_projectDoesNotExist_throws() {
        BlockDetails blockDetails = mock(BlockDetails.class);
        List<BlockDetails> detailList = List.of(blockDetails);
        when(projectItemStore.beginTransaction(projectId)).thenReturn(transaction);
        when(projectItemStore.getItem(projectId)).thenThrow(new RuntimeException("not found"));

        when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);

        RenderableException ex =
                assertThrows(
                        RenderableException.class,
                        () -> dao.updateProjectItem(projItem, detailList, metricsScope));
        assertEquals(ErrorCode.NotAuthorizedOrNotFound, ex.getErrorCode());
    }

    @Test
    void testGetProjectItem_runtimeException_returnsNull() {
        when(projectItemStore.getItem(projectId)).thenThrow(new RuntimeException("DB error"));
        ProjectItem result = dao.getProjectItem(projectId);
        assertNull(result);
    }

    @Test
    void testGetProjectItemsForVendor_vendorIndexIsNull_throwsNullPointerException() {
        // Force projectItemProvider.getIndex to return null
        when(projectItemProvider.getIndex(any(), any())).thenReturn(null);

        ProjectItemDao newDao =
                new ProjectItemDao(
                        projectItemStore, serializer, projectItemProvider, blockDetailsDao);

        assertThrows(NullPointerException.class, () -> newDao.getProjectItemsForVendor(vendorName));
    }

    @Test
    void testGetProjectItemsForVendor_emptyResults() {
        String vname = "testVendor";
        ProjectItem.VendorNameIndex prefix =
                ProjectItem.VendorNameIndex.builder().vendorName(vname).build();
        ScanPage<ProjectItem> page1 = mock(ScanPage.class);

        when(vendorNameIndex.beginPrefixScan(prefix, 1000)).thenReturn(page1);
        when(page1.results()).thenReturn(Collections.emptyList());
        when(page1.hasNext()).thenReturn(false);

        List<ProjectItem> result = dao.getProjectItemsForVendor(vname);
        assertTrue(result.isEmpty());
    }
}
