package com.oracle.pic.networking.lvv.service.kiev;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import com.oracle.pic.identity.authorization.sdk.AuthorizationRequest;
import com.oracle.pic.kiev.Transaction;
import com.oracle.pic.kiev.mapping.token.PaginationTokenSerializer;
import com.oracle.pic.networking.lvv.service.auth.AuthHelper;
import com.oracle.pic.networking.lvv.service.utils.PaginationToken;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class KievManagerTest {
    private KievManager kievManager;

    @Mock private AuthHelper mockAuthorizationHelper;

    @Mock private ConfigurationStore<String, ProjectItem> mockProjectItemStore;
    @Mock private PaginationTokenSerializer mockPaginationToken;
    @Mock private PaginationToken mockPagination;
    @Mock public Transaction mockTransaction;
    ProjectItem projectItem1;
    ProjectItem projectItem2;

    @BeforeEach
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        doNothing()
                .when(mockAuthorizationHelper)
                .authorize(any(AuthorizationRequest.class), anyString());
        this.kievManager = new KievManager(mockProjectItemStore, mockPaginationToken);
        projectItem1 =
                ProjectItem.builder()
                        .projectId("project1")
                        .vendorName("vendor1")
                        .type("cabling")
                        .block("block1")
                        .building("building1")
                        .build();
        projectItem2 =
                ProjectItem.builder()
                        .projectId("project2")
                        .vendorName("vendor1")
                        .type("cabling")
                        .block("block2")
                        .building("building1")
                        .build();

        List<ProjectItem> mockList = new ArrayList<>();
        mockList.add(projectItem1);
        mockList.add(projectItem2);
        ScanResult<ProjectItem> scanResult =
                ScanResult.<ProjectItem>builder().results(mockList).build();

        when(mockProjectItemStore.beginTransaction(any(String.class))).thenReturn(mockTransaction);
        when(mockProjectItemStore.getItem(projectItem1.getProjectId())).thenReturn(projectItem1);
        when(mockProjectItemStore.updateItem(mockTransaction, projectItem1))
                .thenReturn(projectItem1);
        when(mockProjectItemStore.createItem(mockTransaction, projectItem2))
                .thenReturn(projectItem2);

        when(mockProjectItemStore.scanBucket(eq(50), any(), any(), any())).thenReturn(scanResult);
    }

    @Test
    public void addProjectItemTest() throws Exception {
        ProjectItem result = kievManager.addProjectItem(projectItem1);
        compareProject(projectItem1, result);
        result = kievManager.addProjectItem(projectItem2);
        compareProject(projectItem2, result);
    }

    @Test
    public void getProjectItemTest() throws Exception {
        ProjectItem result = kievManager.getProjectItem(projectItem1.getProjectId());
        compareProject(projectItem1, result);
    }

    @Test
    public void getAllProjectTest() throws Exception {
        List<ProjectItem> result = kievManager.getAllProjectItem(mockPagination, "vendor1");
        assertEquals(result.size(), 2);
    }

    void compareProject(ProjectItem project1, ProjectItem project2) {
        assertEquals(project1.getProjectId(), project2.getProjectId());
        assertEquals(project1.getVendorName(), project2.getVendorName());
        assertEquals(project1.getType(), project2.getType());
        assertEquals(project1.getBlock(), project2.getBlock());
        assertEquals(project1.getBuilding(), project2.getBuilding());
    }
}
