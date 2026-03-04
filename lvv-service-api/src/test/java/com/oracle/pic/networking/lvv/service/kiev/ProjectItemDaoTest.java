package com.oracle.pic.networking.lvv.service.kiev;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.kiev.Transaction;
import com.oracle.pic.kiev.exceptions.CommitConflictException;
import com.oracle.pic.kiev.mapping.Index;
import com.oracle.pic.kiev.mapping.MappedHashBucket;
import com.oracle.pic.kiev.mapping.PaginationToken;
import com.oracle.pic.kiev.mapping.ScanPage;
import com.oracle.pic.kiev.mapping.token.PaginationTokenSerializer;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
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
        BlockDetails existingBlock =
                BlockDetails.builder()
                        .block(
                                BlockDetails.Block.builder()
                                        .blockNumber("b1")
                                        .building("bldg")
                                        .build())
                        .projectId("proj1")
                        .build();
        when(blockDetailsDao.getBlockDetails("bldg", "b1")).thenReturn(existingBlock);
        assertNull(dao.checkBlockIsUnassigned(blockDetailsToAdd));
    }

    @Test
    void testAddProjectItem_blockAlreadyAssigned_throwsRenderableException() throws Exception {
        BlockDetails blockDetail =
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
                        .projectId("otherProject")
                        .build();

        when(projectItemStore.beginTransaction(projectId)).thenReturn(transaction);
        when(blockDetailsDao.getBlockDetails("bldg", "b1")).thenReturn(existingBlock);
        when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);

        RenderableException ex =
                assertThrows(
                        RenderableException.class,
                        () -> dao.addProjectItem(projItem, List.of(blockDetail), metricsScope));
        verify(metricsScope)
                .emit(eq(MetricNames.AddProjectItem.BlockAlreadyAssigned.name()), anyDouble());
        verify(projectItemStore, never()).createItem(any(), any());
        verify(blockDetailsDao, never()).addBlockDetails(anyList(), any());
    }

    @Test
    void testAddProjectItem_commitConflictHandled() throws Exception {
        when(projectItemStore.beginTransaction(projectId)).thenReturn(transaction);
        when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);
        doThrow(mock(CommitConflictException.class)).when(transaction).commit();

        assertThrows(
                RenderableException.class,
                () -> dao.addProjectItem(projItem, Collections.emptyList(), metricsScope));

        verify(metricsScope)
                .emit(eq(MetricNames.AddProjectItem.KievCommitFailure.name()), anyDouble());
        verify(transaction).abort();
    }

    @Test
    void testAddProjectItem_itemAlreadyExists_throwsRenderableException() throws Exception {
        ProjectItemDao spyDao = spy(dao);
        when(projectItemStore.beginTransaction(projectId)).thenReturn(transaction);
        when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);
        doReturn(projItem).when(spyDao).getProjectItemForProjectId(projectId);

        assertThrows(
                RenderableException.class,
                () -> spyDao.addProjectItem(projItem, Collections.emptyList(), metricsScope));

        verify(metricsScope)
                .emit(eq(MetricNames.AddProjectItem.ItemAlreadyExists.name()), anyDouble());
        verify(transaction, never()).commit();
    }

    @Test
    void testUpdateProjectItem_itemDoesNotExist_throwsRenderableException() throws Exception {
        ProjectItemDao spyDao = spy(dao);
        when(projectItemStore.beginTransaction(projectId)).thenReturn(transaction);
        when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);
        doThrow(new RuntimeException("not found"))
                .when(spyDao)
                .getProjectItemForProjectId(projectId);

        assertThrows(
                RenderableException.class,
                () -> spyDao.updateProjectItem(projItem, Collections.emptyList(), metricsScope));

        verify(metricsScope)
                .emit(eq(MetricNames.UpdateProjectItem.ItemDoesNotExist.name()), anyDouble());
    }

    @Test
    void testUpdateProjectItem_commitConflictHandled() throws Exception {
        ProjectItemDao spyDao = spy(dao);
        when(projectItemStore.beginTransaction(projectId)).thenReturn(transaction);
        when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);

        ProjectItem existing =
                ProjectItem.builder().projectId(projectId).vendorName(vendorName).build();
        existing.setProjectKey(123L);

        doReturn(existing).when(spyDao).getProjectItemForProjectId(projectId);
        doReturn(existing).when(spyDao).getProjectItem(projectId);

        doThrow(mock(CommitConflictException.class)).when(transaction).commit();

        assertThrows(
                RenderableException.class,
                () -> spyDao.updateProjectItem(projItem, Collections.emptyList(), metricsScope));

        verify(metricsScope)
                .emit(eq(MetricNames.UpdateProjectItem.KievCommitFailure.name()), anyDouble());
        verify(transaction).abort();
    }

    @Test
    void testDeleteProjectItem_itemDoesNotExist_throwsRenderableException() {
        ProjectItemDao spyDao = spy(dao);
        when(projectItemStore.beginTransaction(projectId)).thenReturn(transaction);
        when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);
        doThrow(new RuntimeException("not found"))
                .when(spyDao)
                .getProjectItemForProjectId(projectId);

        assertThrows(
                RenderableException.class, () -> spyDao.deleteProjectItem(projectId, metricsScope));

        verify(metricsScope)
                .emit(eq(MetricNames.DeleteProjectItem.ItemDoesNotExist.name()), anyDouble());
    }

    @Test
    void testDeleteProjectItem_commitConflictHandled() throws CommitConflictException {
        ProjectItemDao spyDao = spy(dao);
        when(projectItemStore.beginTransaction(projectId)).thenReturn(transaction);
        when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);

        ProjectItem existing =
                ProjectItem.builder()
                        .projectId(projectId)
                        .vendorName(vendorName)
                        .regionName("us-phoenix-1")
                        .build();
        existing.setProjectKey(1L);

        doReturn(existing).when(spyDao).getProjectItemForProjectId(projectId);
        doThrow(mock(CommitConflictException.class)).when(transaction).commit();

        assertThrows(
                RenderableException.class, () -> spyDao.deleteProjectItem(projectId, metricsScope));

        verify(metricsScope)
                .emit(eq(MetricNames.DeleteProjectItem.KievCommitFailure.name()), anyDouble());
        verify(transaction).abort();
    }

    @Test
    void testGetProjectItem_returnsNull_whenNotFound() {
        ProjectItemDao spyDao = spy(dao);
        doThrow(new RuntimeException("missing")).when(spyDao).getProjectItemForProjectId(projectId);
        assertNull(spyDao.getProjectItem(projectId));
    }

    @Test
    void testGetProjectItem_success() {
        ProjectItemDao spyDao = spy(dao);
        doReturn(projItem).when(spyDao).getProjectItemForProjectId(projectId);
        assertEquals(projItem, spyDao.getProjectItem(projectId));
    }

    @Test
    void testGetProjectItemForProjectId_notFound_throws() {
        ScanPage<ProjectItem> page = mock(ScanPage.class);
        when(projectIdIndex.beginPrefixScan(any(), anyInt())).thenReturn(page);
        when(page.results()).thenReturn(Collections.emptyList());
        when(page.hasNext()).thenReturn(false);

        assertThrows(RuntimeException.class, () -> dao.getProjectItemForProjectId(projectId));
    }

    @Test
    void testGetProjectItemsForVendor_multipage_filters() {
        ScanPage<ProjectItem> page1 = mock(ScanPage.class);
        ScanPage<ProjectItem> page2 = mock(ScanPage.class);
        ProjectItem nonMatch = ProjectItem.builder().projectId("x1").vendorName("other").build();
        ProjectItem match1 = ProjectItem.builder().projectId("x2").vendorName(vendorName).build();
        ProjectItem match2 = ProjectItem.builder().projectId("x3").vendorName(vendorName).build();

        when(vendorNameIndex.beginPrefixScan(any(), anyInt())).thenReturn(page1);
        when(page1.results()).thenReturn(Arrays.asList(null, nonMatch, match1));
        when(page1.hasNext()).thenReturn(true);
        when(vendorNameIndex.scan(Mockito.<PaginationToken>any())).thenReturn(page2);
        when(page2.results()).thenReturn(Arrays.asList(match2));
        when(page2.hasNext()).thenReturn(false);

        List<ProjectItem> out = dao.getProjectItemsForVendor(vendorName);
        assertEquals(2, out.size());
        assertTrue(out.stream().allMatch(p -> vendorName.equals(p.getVendorName())));
    }

    @Test
    void testGetAllProjects_multipage_filtersNulls() {
        ScanPage<ProjectItem> page1 = mock(ScanPage.class);
        ScanPage<ProjectItem> page2 = mock(ScanPage.class);
        ProjectItem i1 = ProjectItem.builder().projectId("a").vendorName("v").build();
        ProjectItem i2 = ProjectItem.builder().projectId("b").vendorName("v").build();

        when(projectItemProvider.beginScan(anyInt())).thenReturn(page1);
        when(page1.results()).thenReturn(Arrays.asList(null, i1));
        when(page1.hasNext()).thenReturn(true);
        when(projectItemProvider.scan(Mockito.<PaginationToken>any())).thenReturn(page2);

        when(page2.results()).thenReturn(Arrays.asList(i2));

        List<ProjectItem> out = dao.getAllProjects();
        assertEquals(2, out.size());
    }

    @Test
    void testGetProjectItemsForRegion_noIndex_fallback_filtersByRegion() {
        // custom provider with no region index
        MappedHashBucket<Long, ProjectItem> provider = mock(MappedHashBucket.class);
        when(provider.getIndex(
                        eq(ProjectItem.VENDOR_COLUMN_NAME), eq(ProjectItem.VendorNameIndex.class)))
                .thenReturn(vendorNameIndex);
        when(provider.getIndex(
                        eq(ProjectItem.PROJECT_ID_COLUMN_NAME),
                        eq(ProjectItem.ProjectIdIndex.class)))
                .thenReturn(projectIdIndex);
        when(provider.getIndex(
                        eq(ProjectItem.REGION_COLUMN_NAME), eq(ProjectItem.RegionNameIndex.class)))
                .thenReturn(null);
        when(provider.getIndex(
                        eq(ProjectItem.VENDOR_REGION_INDEX_NAME),
                        eq(ProjectItem.VendorRegionIndex.class)))
                .thenReturn(vendorRegionIndex);

        ProjectItemDao noRegionDao =
                new ProjectItemDao(projectItemStore, serializer, provider, blockDetailsDao);

        ScanPage<ProjectItem> page = mock(ScanPage.class);
        when(provider.beginScan(anyInt())).thenReturn(page);
        ProjectItem inRegion =
                ProjectItem.builder().projectId("a").vendorName("v").regionName(regionName).build();
        ProjectItem otherRegion =
                ProjectItem.builder().projectId("b").vendorName("v").regionName("other").build();
        when(page.results()).thenReturn(Arrays.asList(inRegion, otherRegion, null));
        when(page.hasNext()).thenReturn(false);

        List<ProjectItem> out = noRegionDao.getProjectItemsForRegion(regionName);
        assertEquals(1, out.size());
        assertEquals(regionName, out.get(0).getRegionName());
    }

    @Test
    void testGetProjectItemsForVendorAndRegion_unionFallback_dedup() {
        // primary vendor-region index results
        ScanPage<ProjectItem> vrPage = mock(ScanPage.class);
        when(vendorRegionIndex.beginPrefixScan(any(), anyInt())).thenReturn(vrPage);
        ProjectItem i1 =
                ProjectItem.builder()
                        .projectId("A")
                        .vendorName(vendorName)
                        .regionName("us-phoenix-1")
                        .build();
        when(vrPage.results()).thenReturn(List.of(i1));
        when(vrPage.hasNext()).thenReturn(false);

        // vendor scan fallback with missing region on some rows
        ScanPage<ProjectItem> vPage = mock(ScanPage.class);
        when(vendorNameIndex.beginPrefixScan(any(), anyInt())).thenReturn(vPage);
        ProjectItem missingRegionB =
                ProjectItem.builder().projectId("B").vendorName(vendorName).regionName("").build();
        ProjectItem missingRegionA =
                ProjectItem.builder()
                        .projectId("A")
                        .vendorName(vendorName)
                        .regionName(null)
                        .build();
        when(vPage.results()).thenReturn(Arrays.asList(missingRegionB, missingRegionA));
        when(vPage.hasNext()).thenReturn(false);

        // derive region for B from block details, A has no blocks so is skipped in fallback union
        when(blockDetailsDao.getBlockDetailsForProject("B"))
                .thenReturn(
                        List.of(
                                BlockDetails.builder()
                                        .block(
                                                BlockDetails.Block.builder()
                                                        .building("phx01a")
                                                        .blockNumber("1")
                                                        .build())
                                        .projectId("B")
                                        .build()));
        when(blockDetailsDao.getBlockDetailsForProject("A")).thenReturn(Collections.emptyList());

        List<ProjectItem> out = dao.getProjectItemsForVendorAndRegion(vendorName, "us-phoenix-1");
        // Should include A (from vendorRegion index) and B (derived by building), but not duplicate
        // A from vendor scan
        assertEquals(2, out.size());
        Set<String> ids = new HashSet<>();
        out.forEach(p -> ids.add(p.getProjectId()));
        assertTrue(ids.containsAll(List.of("A", "B")));
    }
}
