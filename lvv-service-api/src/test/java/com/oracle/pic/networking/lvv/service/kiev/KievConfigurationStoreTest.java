package com.oracle.pic.networking.lvv.service.kiev;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import com.oracle.pic.identity.authorization.sdk.AuthorizationRequest;
import com.oracle.pic.kiev.Transaction;
import com.oracle.pic.kiev.mapping.MappedDataStore;
import com.oracle.pic.kiev.mapping.MappedHashBucket;
import com.oracle.pic.kiev.mapping.token.PaginationTokenSerializer;
import com.oracle.pic.networking.lvv.service.auth.AuthHelper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class KievConfigurationStoreTest {
    private KievConfigurationStore<String, ProjectItem> kievConfigurationStore;

    @Mock private AuthHelper mockAuthorizationHelper;

    @Mock private MappedDataStore mockMappedDataStore;
    @Mock private MappedHashBucket<String, ProjectItem> mockMappedHashBucket;
    @Mock private PaginationTokenSerializer mockPaginationToken;
    @Mock private Transaction mockTransaction;
    ProjectItem projectItem1;

    @BeforeEach
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        doNothing()
                .when(mockAuthorizationHelper)
                .authorize(any(AuthorizationRequest.class), anyString());
        this.kievConfigurationStore =
                new KievConfigurationStore<>(
                        mockMappedDataStore, mockMappedHashBucket, mockPaginationToken);
        projectItem1 =
                ProjectItem.builder()
                        .projectId("project1")
                        .vendorName("vendor1")
                        .type("cabling")
                        .block("block1")
                        .building("building1")
                        .build();

        when(mockMappedHashBucket.insert(mockTransaction, projectItem1)).thenReturn(projectItem1);
        when(mockMappedHashBucket.put(mockTransaction, projectItem1)).thenReturn(projectItem1);
        when(mockMappedHashBucket.get(projectItem1.getProjectId()))
                .thenReturn(Optional.ofNullable(projectItem1));
    }

    @Test
    public void getItemTest() throws Exception {
        ProjectItem result = kievConfigurationStore.getItem(projectItem1.getProjectId());
        compareProject(projectItem1, result);
    }

    @Test
    public void createItemTest() throws Exception {
        ProjectItem result = kievConfigurationStore.createItem(mockTransaction, projectItem1);
        compareProject(projectItem1, result);
    }

    @Test
    public void updateItemTest() throws Exception {
        ProjectItem result = kievConfigurationStore.updateItem(mockTransaction, projectItem1);
        compareProject(projectItem1, result);
    }

    void compareProject(ProjectItem project1, ProjectItem project2) {
        assertEquals(project1.getProjectId(), project2.getProjectId());
        assertEquals(project1.getVendorName(), project2.getVendorName());
        assertEquals(project1.getType(), project2.getType());
        assertEquals(project1.getBlock(), project2.getBlock());
        assertEquals(project1.getBuilding(), project2.getBuilding());
    }
}
