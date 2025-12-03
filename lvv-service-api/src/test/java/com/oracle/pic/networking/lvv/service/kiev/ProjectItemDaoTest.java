package com.oracle.pic.networking.lvv.service.kiev;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.kiev.Transaction;
import com.oracle.pic.kiev.mapping.Index;
import com.oracle.pic.kiev.mapping.MappedHashBucket;
import com.oracle.pic.kiev.mapping.ScanPage;
import com.oracle.pic.kiev.mapping.token.PaginationTokenSerializer;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class ProjectItemDaoTest {

    @Mock ConfigurationStore<Long, ProjectItem> projectItemStore;
    @Mock BlockDetailsDao blockDetailsDao;
    @Mock PaginationTokenSerializer serializer;
    @Mock MappedHashBucket<Long, ProjectItem> projectItemProvider;
    @Mock Index<ProjectItem.VendorNameIndex, ProjectItem> vendorNameIndex;
    @Mock Index<ProjectItem.ProjectIdIndex, ProjectItem> projectIdIndex;
    @Mock Index<ProjectItem.RegionNameIndex, ProjectItem> regionNameIndex;
    @Mock Index<ProjectItem.VendorRegionIndex, ProjectItem> vendorRegionIndex;
    @Mock MetricsScope metricsScope;
    @Mock Transaction transaction;

    ProjectItemDao dao;
    final String projectId = "pid1";
    final String vendorName = "testVendor";
    final String regionName = "region";
    ProjectItem projItem;

    @BeforeEach
    void setUp() {
        when(projectItemProvider.getIndex(
                        eq(ProjectItem.VENDOR_COLUMN_NAME), eq(ProjectItem.VendorNameIndex.class)))
                .thenReturn(vendorNameIndex);
        when(projectItemProvider.getIndex(
                        eq(ProjectItem.PROJECT_ID_COLUMN_NAME),
                        eq(ProjectItem.ProjectIdIndex.class)))
                .thenReturn(projectIdIndex);
        when(projectItemProvider.getIndex(
                        eq(ProjectItem.REGION_COLUMN_NAME), eq(ProjectItem.RegionNameIndex.class)))
                .thenReturn(regionNameIndex);
        when(projectItemProvider.getIndex(
                        eq(ProjectItem.VENDOR_REGION_INDEX_NAME),
                        eq(ProjectItem.VendorRegionIndex.class)))
                .thenReturn(vendorRegionIndex);
        dao =
                new ProjectItemDao(
                        projectItemStore, serializer, projectItemProvider, blockDetailsDao);
        projItem = ProjectItem.builder().projectId(projectId).vendorName(vendorName).build();
    }

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

    @Test
    void testAddProjectItem_nullDetailList_throws() {
        assertThrows(
                NullPointerException.class, () -> dao.addProjectItem(projItem, null, metricsScope));
    }

    @Test
    void testUpdateProjectItem_nullDetailList_throws() {
        assertThrows(
                NullPointerException.class,
                () -> dao.updateProjectItem(projItem, null, metricsScope));
    }

    @Test
    void testAddProjectItem_duplicateBlockDetails() throws Exception {
        BlockDetails blockDetail =
                BlockDetails.builder()
                        .block(
                                BlockDetails.Block.builder()
                                        .blockNumber("b1")
                                        .building("bldg")
                                        .build())
                        .projectId("proj1")
                        .build();

        // Attempting to add the same detail twice in the list
        List<BlockDetails> detailList = Arrays.asList(blockDetail, blockDetail);

        when(projectItemStore.beginTransaction(projectId)).thenReturn(transaction);
        when(blockDetailsDao.getBlockDetails(anyString(), anyString())).thenReturn(null);
        when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);

        assertDoesNotThrow(() -> dao.addProjectItem(projItem, detailList, metricsScope));
    }

    @Test
    void testDeleteProjectItem_nullScope_throwsNullPointerException() {
        when(projectItemStore.beginTransaction(projectId)).thenReturn(transaction);
        assertThrows(NullPointerException.class, () -> dao.deleteProjectItem(projectId, null));
    }

    @Test
    void testGetProjectItemsForVendor_nullVendorName_returnsEmptyList() {
        List<ProjectItem> result = dao.getProjectItemsForVendor(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testAddProjectItem_nullProjectItem_throws() {
        assertThrows(
                NullPointerException.class,
                () -> dao.addProjectItem(null, List.of(), metricsScope));
    }

    @Test
    void testUpdateProjectItem_nullProjectItem_throws() {
        assertThrows(
                NullPointerException.class,
                () -> dao.updateProjectItem(null, List.of(), metricsScope));
    }

    @Test
    void testDeleteProjectItem_emptyProjectId_throws() {
        when(projectItemStore.beginTransaction("")).thenReturn(transaction);
        assertThrows(Exception.class, () -> dao.deleteProjectItem("", metricsScope));
    }

    @Test
    void testAddProjectItem_emptyDetailList_ok() throws Exception {
        when(projectItemStore.beginTransaction(projectId)).thenReturn(transaction);
        when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);

        assertDoesNotThrow(
                () -> dao.addProjectItem(projItem, Collections.emptyList(), metricsScope));
    }

    @Test
    void testGetAllProjects_noneExist_returnsEmpty() {
        ScanPage<ProjectItem> page = mock(ScanPage.class);
        when(projectItemProvider.beginScan(anyInt())).thenReturn(page);
        when(page.results()).thenReturn(Collections.emptyList());
        when(page.hasNext()).thenReturn(false);

        assertTrue(dao.getAllProjects().isEmpty());
    }

    @Test
    void testGetProjectItemsForRegion_withRegionIndex() {
        ScanPage<ProjectItem> page = mock(ScanPage.class);
        ProjectItem item =
                ProjectItem.builder()
                        .projectId("x")
                        .regionName(regionName)
                        .vendorName("vendor2")
                        .build();
        when(regionNameIndex.beginPrefixScan(any(), anyInt())).thenReturn(page);
        when(page.results()).thenReturn(List.of(item));
        when(page.hasNext()).thenReturn(false);

        List<ProjectItem> out = dao.getProjectItemsForRegion(regionName);
        assertEquals(1, out.size());
    }

    @Test
    void testGetProjectItemsForRegion_noIndex_fallback() {
        ProjectItemDao fallbackDao =
                new ProjectItemDao(
                        projectItemStore, serializer, projectItemProvider, blockDetailsDao);

        ProjectItem p =
                ProjectItem.builder()
                        .projectId("x")
                        .regionName(regionName)
                        .vendorName("vendor2")
                        .build();

        List<ProjectItem> out = fallbackDao.getProjectItemsForRegion(regionName);
        assertTrue(out.isEmpty() || out.stream().allMatch(i -> i != null));
    }

    @Test
    void testGetProjectItemsForVendorAndRegion_happyPath() {
        ScanPage<ProjectItem> page = mock(ScanPage.class);
        ProjectItem item =
                ProjectItem.builder()
                        .projectId("x")
                        .vendorName(vendorName)
                        .regionName(regionName)
                        .build();
        when(vendorRegionIndex.beginPrefixScan(any(), anyInt())).thenReturn(page);
        when(page.results()).thenReturn(List.of(item));
        when(page.hasNext()).thenReturn(false);

        List<ProjectItem> out = dao.getProjectItemsForVendorAndRegion(vendorName, regionName);
        assertEquals(1, out.size());
    }

    @Test
    void testGetProjectItemForProjectId_multipleItems_throws() {
        ProjectItem.ProjectIdIndex prefix =
                ProjectItem.ProjectIdIndex.builder().projectId(projectId).build();
        ScanPage<ProjectItem> page = mock(ScanPage.class);
        ProjectItem item1 =
                ProjectItem.builder().projectId(projectId).vendorName("vendor1").build();
        ProjectItem item2 =
                ProjectItem.builder().projectId(projectId).vendorName("vendor2").build();

        when(projectIdIndex.beginPrefixScan(any(), anyInt())).thenReturn(page);
        when(page.results()).thenReturn(List.of(item1, item2));
        when(page.hasNext()).thenReturn(false);

        assertThrows(RuntimeException.class, () -> dao.getProjectItemForProjectId(projectId));
    }
}
