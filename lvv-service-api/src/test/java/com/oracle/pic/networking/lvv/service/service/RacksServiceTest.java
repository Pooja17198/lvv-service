package com.oracle.pic.networking.lvv.service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.networking.autonet.plan.service.model.Device;
import com.oracle.pic.networking.lvv.service.dependencies.jira.JiraSDService;
import com.oracle.pic.networking.lvv.service.dependencies.jira.JiraTicket;
import com.oracle.pic.networking.lvv.service.dependencies.planservice.PlanServiceHelper;
import com.oracle.pic.networking.lvv.service.dependencies.storekeeper.Rack;
import com.oracle.pic.networking.lvv.service.dependencies.storekeeper.StoreKeeperHelper;
import com.oracle.pic.networking.lvv.service.kiev.BlockDetails;
import com.oracle.pic.networking.lvv.service.kiev.BlockDetailsDao;
import com.oracle.pic.networking.lvv.service.kiev.JobStatus;
import com.oracle.pic.networking.lvv.service.kiev.NcpJobDetailsDao;
import com.oracle.pic.networking.lvv.service.kiev.ProjectItemDao;
import com.oracle.pic.networking.lvv.service.model.DeviceDetails;
import com.oracle.pic.networking.lvv.service.model.ProjectRack;
import com.oracle.pic.networking.lvv.service.resources.ResourceModelTransformer;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RacksServiceTest {

    @Mock PlanServiceHelper planServiceHelper;
    @Mock NcpJobDetailsDao ncpJobDetailsDao;
    @Mock CablingValidationService cablingValidationService;
    @Mock ResourceModelTransformer resourceModelTransformer;
    @Mock ProjectItemDao projectItemDao;
    @Mock BlockDetailsDao blockDetailsDao;
    @Mock StoreKeeperHelper storeKeeperHelper;
    @Mock JiraSDService jiraSDService;
    @Mock MetricsScope metricsScope;

    RacksService service;

    final String region = "us-region";
    final String rackNumber = "RACK1";
    final String building = "B2";
    final String rackSerial = "RSN123";

    @BeforeEach
    void setup() {
        service =
                new RacksService(
                        planServiceHelper,
                        ncpJobDetailsDao,
                        cablingValidationService,
                        resourceModelTransformer,
                        projectItemDao,
                        blockDetailsDao,
                        storeKeeperHelper,
                        jiraSDService);
    }

    private Device mockDeviceWithName(String name) {
        Device device = mock(Device.class);
        when(device.getName()).thenReturn(name);
        Map<String, Object> configAttributes = new HashMap<>();
        configAttributes.put("monitoring.interfaces", List.of("Eth0/1"));
        when(device.getConfigAttributes()).thenReturn(configAttributes);

        Map<String, String> conf = new HashMap<>();
        conf.put("device.state", "deployed");
        Map<String, Map<String, String>> state = new HashMap<>();
        state.put("conf", conf);
        when(device.getState()).thenReturn(state);
        return device;
    }

    private Device mockIneligibleDeviceWithName(String name) {
        Device device = mock(Device.class);
        when(device.getConfigAttributes()).thenReturn(Collections.emptyMap());
        return device;
    }

    @Test
    void getDeviceDetailsInRack_devicesPresent_createsJobsForEachDevice_andTransforms() {
        // Arrange
        Device devA = mockDeviceWithName("devA");
        Device devB = mockDeviceWithName("devB");
        List<Device> devices = Arrays.asList(devA, devB);

        when(planServiceHelper.getDeviceListInRack(rackNumber, building, region, metricsScope))
                .thenReturn(devices);

        Map<String, JobStatus> jobStatus = new HashMap<>();
        jobStatus.put("devA", JobStatus.IN_PROGRESS);
        jobStatus.put("devB", JobStatus.COMPLETED);
        when(cablingValidationService.getValidationJobStatus(
                        metricsScope, region, rackSerial, rackNumber, false))
                .thenReturn(jobStatus);

        List<DeviceDetails> expectedDetails = Collections.singletonList(mock(DeviceDetails.class));
        when(resourceModelTransformer.toModel(jobStatus, devices)).thenReturn(expectedDetails);

        ArgumentCaptor<HashMap<String, String>> jobsCaptor = ArgumentCaptor.forClass(HashMap.class);

        // Act
        List<DeviceDetails> result =
                service.getDeviceDetailsInRack(
                        rackSerial, region, rackNumber, building, metricsScope);

        // Assert
        assertNotNull(result);
        assertEquals(expectedDetails, result);

        verify(planServiceHelper).getDeviceListInRack(rackNumber, building, region, metricsScope);

        verify(ncpJobDetailsDao)
                .addUpdateNcpJobDetails(jobsCaptor.capture(), eq(rackSerial), eq(metricsScope));
        Map<String, String> jobsInserted = jobsCaptor.getValue();
        assertEquals(2, jobsInserted.size());
        assertEquals("", jobsInserted.get("devA"));
        assertEquals("", jobsInserted.get("devB"));
        assertFalse(
                jobsInserted.containsKey(rackSerial),
                "rack serial must not be used as a device when devices exist");

        verify(cablingValidationService)
                .getValidationJobStatus(metricsScope, region, rackSerial, rackNumber, false);
        verify(resourceModelTransformer).toModel(jobStatus, devices);
        verifyNoMoreInteractions(resourceModelTransformer, cablingValidationService);
    }

    @Test
    void getDeviceDetailsInRack_noDevices_createsJobForRackSerial_andTransforms() {
        // Arrange
        when(planServiceHelper.getDeviceListInRack(rackNumber, building, region, metricsScope))
                .thenReturn(Collections.emptyList());

        Map<String, JobStatus> jobStatus = new HashMap<>(); // could be empty or anything
        when(cablingValidationService.getValidationJobStatus(
                        metricsScope, region, rackSerial, rackNumber, false))
                .thenReturn(jobStatus);

        List<DeviceDetails> expected = Collections.emptyList();
        when(resourceModelTransformer.toModel(jobStatus, Collections.emptyList()))
                .thenReturn(expected);

        ArgumentCaptor<HashMap<String, String>> jobsCaptor = ArgumentCaptor.forClass(HashMap.class);

        // Act
        List<DeviceDetails> result =
                service.getDeviceDetailsInRack(
                        rackSerial, region, rackNumber, building, metricsScope);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(ncpJobDetailsDao)
                .addUpdateNcpJobDetails(jobsCaptor.capture(), eq(rackSerial), eq(metricsScope));
        Map<String, String> jobsInserted = jobsCaptor.getValue();
        assertEquals(1, jobsInserted.size());
        assertEquals("", jobsInserted.get(rackSerial));

        verify(cablingValidationService)
                .getValidationJobStatus(metricsScope, region, rackSerial, rackNumber, false);
        verify(resourceModelTransformer).toModel(jobStatus, Collections.emptyList());
    }

    @Test
    void getDeviceDetailsInRack_duplicateDeviceNames_deduplicatesJobsMap() {
        // Arrange: two devices with same name
        Device d1 = mockDeviceWithName("dup");
        Device d2 = mockDeviceWithName("dup");
        List<Device> devices = Arrays.asList(d1, d2);

        when(planServiceHelper.getDeviceListInRack(rackNumber, building, region, metricsScope))
                .thenReturn(devices);

        Map<String, JobStatus> jobStatus = new HashMap<>();
        jobStatus.put("dup", JobStatus.IN_PROGRESS);
        when(cablingValidationService.getValidationJobStatus(
                        metricsScope, region, rackSerial, rackNumber, false))
                .thenReturn(jobStatus);

        when(resourceModelTransformer.toModel(jobStatus, devices))
                .thenReturn(Collections.emptyList());

        ArgumentCaptor<HashMap<String, String>> jobsCaptor = ArgumentCaptor.forClass(HashMap.class);

        // Act
        service.getDeviceDetailsInRack(rackSerial, region, rackNumber, building, metricsScope);

        // Assert: only one entry for duplicated name
        verify(ncpJobDetailsDao)
                .addUpdateNcpJobDetails(jobsCaptor.capture(), eq(rackSerial), eq(metricsScope));
        Map<String, String> jobsInserted = jobsCaptor.getValue();
        assertEquals(1, jobsInserted.size());
        assertEquals("", jobsInserted.get("dup"));
    }

    @Test
    void getDeviceDetailsInRack_devicesPresentButNoneEligible_skipsDbSeedJobs() {
        // Arrange
        Device d1 = mockIneligibleDeviceWithName("dev-ineligible-1");
        Device d2 = mockIneligibleDeviceWithName("dev-ineligible-2");
        List<Device> devices = Arrays.asList(d1, d2);
        when(planServiceHelper.getDeviceListInRack(rackNumber, building, region, metricsScope))
                .thenReturn(devices);

        Map<String, JobStatus> emptyStatus = Collections.emptyMap();
        when(cablingValidationService.getValidationJobStatus(
                        metricsScope, region, rackSerial, rackNumber, false))
                .thenReturn(emptyStatus);
        when(resourceModelTransformer.toModel(emptyStatus, devices))
                .thenReturn(Collections.emptyList());

        // Act
        List<DeviceDetails> result =
                service.getDeviceDetailsInRack(
                        rackSerial, region, rackNumber, building, metricsScope);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(ncpJobDetailsDao, never())
                .addUpdateNcpJobDetails(any(HashMap.class), anyString(), any(MetricsScope.class));
        verify(cablingValidationService)
                .getValidationJobStatus(metricsScope, region, rackSerial, rackNumber, false);
        verify(resourceModelTransformer).toModel(emptyStatus, devices);
    }

    @Test
    void getDeviceDetailsInRack_propagatesException_fromPlanService() {
        // Arrange
        RuntimeException boom = new RuntimeException("plan service down");
        when(planServiceHelper.getDeviceListInRack(rackNumber, building, region, metricsScope))
                .thenThrow(boom);

        // Act + Assert
        RuntimeException thrown =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                service.getDeviceDetailsInRack(
                                        rackSerial, region, rackNumber, building, metricsScope));
        assertSame(boom, thrown);

        verify(planServiceHelper).getDeviceListInRack(rackNumber, building, region, metricsScope);
        verifyNoInteractions(ncpJobDetailsDao, cablingValidationService, resourceModelTransformer);
    }

    @Test
    void getDeviceDetailsInRack_allowsNullRegionRackBuilding_andStillCreatesRackJob() {
        // Arrange: explicitly stub for nulls to ensure pass-through of args
        when(planServiceHelper.getDeviceListInRack(
                        isNull(), isNull(), isNull(), same(metricsScope)))
                .thenReturn(Collections.emptyList());

        Map<String, JobStatus> emptyStatus = Collections.emptyMap();
        when(cablingValidationService.getValidationJobStatus(
                        metricsScope, null, rackSerial, null, false))
                .thenReturn(emptyStatus);

        when(resourceModelTransformer.toModel(emptyStatus, Collections.emptyList()))
                .thenReturn(Collections.emptyList());

        ArgumentCaptor<HashMap<String, String>> jobsCaptor = ArgumentCaptor.forClass(HashMap.class);

        // Act
        List<DeviceDetails> result =
                service.getDeviceDetailsInRack(rackSerial, null, null, null, metricsScope);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(planServiceHelper).getDeviceListInRack(null, null, null, metricsScope);
        verify(ncpJobDetailsDao)
                .addUpdateNcpJobDetails(jobsCaptor.capture(), eq(rackSerial), eq(metricsScope));
        Map<String, String> jobsInserted = jobsCaptor.getValue();
        assertEquals(1, jobsInserted.size());
        assertEquals("", jobsInserted.get(rackSerial));

        verify(cablingValidationService)
                .getValidationJobStatus(metricsScope, null, rackSerial, null, false);
        verify(resourceModelTransformer).toModel(emptyStatus, Collections.emptyList());
    }

    @Test
    void getDeviceDetailsInRack_propagatesException_fromTransformer() {
        // Arrange
        Device dev = mockDeviceWithName("devA");
        List<Device> devices = Collections.singletonList(dev);
        when(planServiceHelper.getDeviceListInRack(rackNumber, building, region, metricsScope))
                .thenReturn(devices);

        Map<String, JobStatus> jobStatus = new HashMap<>();
        jobStatus.put("devA", JobStatus.COMPLETED);
        when(cablingValidationService.getValidationJobStatus(
                        metricsScope, region, rackSerial, rackNumber, false))
                .thenReturn(jobStatus);

        RuntimeException boom = new RuntimeException("transformer failed");
        when(resourceModelTransformer.toModel(jobStatus, devices)).thenThrow(boom);

        // Act + Assert
        RuntimeException thrown =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                service.getDeviceDetailsInRack(
                                        rackSerial, region, rackNumber, building, metricsScope));
        assertSame(boom, thrown);

        verify(planServiceHelper).getDeviceListInRack(rackNumber, building, region, metricsScope);
        verify(cablingValidationService)
                .getValidationJobStatus(metricsScope, region, rackSerial, rackNumber, false);
        verify(resourceModelTransformer).toModel(jobStatus, devices);
    }

    @Test
    void getDeviceDetailsInRack_cablingServiceThrows_afterDbInsert_propagates() {
        when(planServiceHelper.getDeviceListInRack(rackNumber, building, region, metricsScope))
                .thenReturn(Collections.emptyList());

        RuntimeException boom = new RuntimeException("cabling failed");
        when(cablingValidationService.getValidationJobStatus(
                        metricsScope, region, rackSerial, rackNumber, false))
                .thenThrow(boom);

        assertSame(
                boom,
                assertThrows(
                        RuntimeException.class,
                        () ->
                                service.getDeviceDetailsInRack(
                                        rackSerial, region, rackNumber, building, metricsScope)));

        verify(ncpJobDetailsDao)
                .addUpdateNcpJobDetails(
                        Mockito.<HashMap<String, String>>any(), eq(rackSerial), eq(metricsScope));
        verifyNoInteractions(resourceModelTransformer);
    }

    @Test
    void getDeviceDetailsInRack_callsDependenciesInOrder() {
        Device dev = mockDeviceWithName("dev1");
        List<Device> devices = Collections.singletonList(dev);
        when(planServiceHelper.getDeviceListInRack(rackNumber, building, region, metricsScope))
                .thenReturn(devices);

        Map<String, JobStatus> status = new HashMap<>();
        status.put("dev1", JobStatus.IN_PROGRESS);
        when(cablingValidationService.getValidationJobStatus(
                        metricsScope, region, rackSerial, rackNumber, false))
                .thenReturn(status);

        when(resourceModelTransformer.toModel(status, devices)).thenReturn(Collections.emptyList());

        service.getDeviceDetailsInRack(rackSerial, region, rackNumber, building, metricsScope);

        InOrder inOrder =
                inOrder(
                        planServiceHelper,
                        ncpJobDetailsDao,
                        cablingValidationService,
                        resourceModelTransformer);
        inOrder.verify(planServiceHelper)
                .getDeviceListInRack(rackNumber, building, region, metricsScope);
        inOrder.verify(ncpJobDetailsDao)
                .addUpdateNcpJobDetails(
                        Mockito.<HashMap<String, String>>any(), eq(rackSerial), eq(metricsScope));
        inOrder.verify(cablingValidationService)
                .getValidationJobStatus(metricsScope, region, rackSerial, rackNumber, false);
        inOrder.verify(resourceModelTransformer).toModel(status, devices);
        inOrder.verifyNoMoreInteractions();
    }

    // -------- Helpers for listProjectRacks tests --------
    private BlockDetails makeBlock(String building, String block, String projectId) {
        return BlockDetails.builder()
                .block(BlockDetails.Block.builder().building(building).blockNumber(block).build())
                .projectId(projectId)
                .build();
    }

    private Rack makeRack(String building, String block, String serial) {
        return Rack.builder()
                .building(building)
                .block(block)
                .rackLocation("L1")
                .rackSerial(serial)
                .rackState("ACTIVE")
                .platformName("PLATFORM-X")
                .build();
    }

    @Test
    void listProjectRacks_projectNotFound_emitsMetric_andThrows() {
        String projectId = "proj-missing";
        when(projectItemDao.getProjectItem(projectId)).thenReturn(null);

        assertThrows(
                RenderableException.class, () -> service.listProjectRacks(projectId, metricsScope));

        verify(metricsScope)
                .emit(
                        com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames
                                .FetchRacks.ProjectNotFound.name(),
                        1.0);
        verifyNoInteractions(
                blockDetailsDao, storeKeeperHelper, jiraSDService, resourceModelTransformer);
    }

    @Test
    void listProjectRacks_noBlocks_emitsMetric_andThrows() {
        String projectId = "proj-empty";
        // minimal non-null project item
        com.oracle.pic.networking.lvv.service.kiev.ProjectItem project =
                com.oracle.pic.networking.lvv.service.kiev.ProjectItem.builder()
                        .projectId(projectId)
                        .vendorName("vendor")
                        .build();
        when(projectItemDao.getProjectItem(projectId)).thenReturn(project);
        when(blockDetailsDao.getBlockDetailsForProject(projectId))
                .thenReturn(Collections.emptyList());

        assertThrows(
                RenderableException.class, () -> service.listProjectRacks(projectId, metricsScope));

        verify(metricsScope)
                .emit(
                        com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames
                                .FetchRacks.NoBlocksInProject.name(),
                        1.0);
        verifyNoInteractions(storeKeeperHelper, jiraSDService, resourceModelTransformer);
    }

    @Test
    void listProjectRacks_blockWithNoRacks_skipsAndReturnsEmpty() {
        String projectId = "proj-noracks";
        com.oracle.pic.networking.lvv.service.kiev.ProjectItem project =
                com.oracle.pic.networking.lvv.service.kiev.ProjectItem.builder()
                        .projectId(projectId)
                        .vendorName("vendor")
                        .build();
        when(projectItemDao.getProjectItem(projectId)).thenReturn(project);
        when(blockDetailsDao.getBlockDetailsForProject(projectId))
                .thenReturn(List.of(makeBlock("B1", "BLK1", projectId)));

        when(jiraSDService.findOpenTicketsBySerialForBlock("B1", "BLK1"))
                .thenReturn(Collections.emptyMap());
        when(storeKeeperHelper.listRacks("BLK1", "B1", metricsScope)).thenReturn(null); // or empty

        List<ProjectRack> result = service.listProjectRacks(projectId, metricsScope);
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(jiraSDService).findOpenTicketsBySerialForBlock("B1", "BLK1");
        verify(storeKeeperHelper).listRacks("BLK1", "B1", metricsScope);
        verifyNoInteractions(resourceModelTransformer);
    }

    @Test
    void listProjectRacks_rackWithMatchingJiraTicket_enablesResolve_andUsesTransformer() {
        String projectId = "proj-ok";
        com.oracle.pic.networking.lvv.service.kiev.ProjectItem project =
                com.oracle.pic.networking.lvv.service.kiev.ProjectItem.builder()
                        .projectId(projectId)
                        .vendorName("vendor")
                        .build();
        when(projectItemDao.getProjectItem(projectId)).thenReturn(project);
        when(blockDetailsDao.getBlockDetailsForProject(projectId))
                .thenReturn(List.of(makeBlock("B1", "BLK1", projectId)));

        // Racks: one with matching serial, one with no match
        Rack r1 = makeRack("B1", "BLK1", "S1");
        Rack r2 = makeRack("B1", "BLK1", "S2");
        when(storeKeeperHelper.listRacks("BLK1", "B1", metricsScope)).thenReturn(List.of(r1, r2));

        // Tickets map: S1 has an open ticket (initially disabled), S2 absent -> default
        JiraTicket open =
                JiraTicket.builder()
                        .ticketId("T-1")
                        .ticketCategory("Cat")
                        .resolveEnabled(false)
                        .resolveDisabledReason("initial")
                        .build();
        Map<String, JiraTicket> blockTickets = new HashMap<>();
        blockTickets.put("S1", open);
        when(jiraSDService.findOpenTicketsBySerialForBlock("B1", "BLK1")).thenReturn(blockTickets);

        ProjectRack pr1 = mock(ProjectRack.class);
        ProjectRack pr2 = mock(ProjectRack.class);
        when(resourceModelTransformer.toModel(any(Rack.class), any(JiraTicket.class)))
                .thenReturn(pr1, pr2);

        List<ProjectRack> result = service.listProjectRacks(projectId, metricsScope);
        assertEquals(2, result.size());
        assertEquals(List.of(pr1, pr2), result);

        // Verify calls
        verify(jiraSDService).findOpenTicketsBySerialForBlock("B1", "BLK1");
        verify(storeKeeperHelper).listRacks("BLK1", "B1", metricsScope);

        // Capture and assert JiraTicket mutation and default creation
        ArgumentCaptor<Rack> rackCap = ArgumentCaptor.forClass(Rack.class);
        ArgumentCaptor<JiraTicket> ticketCap = ArgumentCaptor.forClass(JiraTicket.class);
        verify(resourceModelTransformer, times(2)).toModel(rackCap.capture(), ticketCap.capture());

        List<Rack> racksPassed = rackCap.getAllValues();
        List<JiraTicket> ticketsPassed = ticketCap.getAllValues();

        // For r1 -> matching ticket should be enabled + reason null
        int idxR1 = racksPassed.indexOf(r1);
        assertTrue(idxR1 >= 0);
        JiraTicket t1 = ticketsPassed.get(idxR1);
        assertEquals("T-1", t1.getTicketId());
        assertTrue(t1.isResolveEnabled());
        assertNull(t1.getResolveDisabledReason());

        // For r2 -> default ticket since no open Jira
        int idxR2 = racksPassed.indexOf(r2);
        assertTrue(idxR2 >= 0);
        JiraTicket t2 = ticketsPassed.get(idxR2);
        assertNull(t2.getTicketId());
        assertFalse(t2.isResolveEnabled());
        assertEquals("No open ticket", t2.getResolveDisabledReason());
    }

    @Test
    void listProjectRacks_handlesNullRackSerial_byUsingDefaultTicket() {
        String projectId = "proj-null-serial";
        com.oracle.pic.networking.lvv.service.kiev.ProjectItem project =
                com.oracle.pic.networking.lvv.service.kiev.ProjectItem.builder()
                        .projectId(projectId)
                        .vendorName("vendor")
                        .build();
        when(projectItemDao.getProjectItem(projectId)).thenReturn(project);
        when(blockDetailsDao.getBlockDetailsForProject(projectId))
                .thenReturn(List.of(makeBlock("B2", "BLK2", projectId)));

        Rack rNull = makeRack("B2", "BLK2", null);
        when(storeKeeperHelper.listRacks("BLK2", "B2", metricsScope)).thenReturn(List.of(rNull));
        when(jiraSDService.findOpenTicketsBySerialForBlock("B2", "BLK2"))
                .thenReturn(Collections.emptyMap());

        ProjectRack pr = mock(ProjectRack.class);
        when(resourceModelTransformer.toModel(any(Rack.class), any(JiraTicket.class)))
                .thenReturn(pr);

        List<ProjectRack> result = service.listProjectRacks(projectId, metricsScope);
        assertEquals(1, result.size());

        ArgumentCaptor<JiraTicket> ticketCap = ArgumentCaptor.forClass(JiraTicket.class);
        verify(resourceModelTransformer).toModel(eq(rNull), ticketCap.capture());
        JiraTicket jt = ticketCap.getValue();
        assertFalse(jt.isResolveEnabled());
        assertEquals("No open ticket", jt.getResolveDisabledReason());
    }

    @Test
    void listProjectRacks_multipleBlocks_aggregatesFromEachBlock() {
        String projectId = "proj-multi";
        com.oracle.pic.networking.lvv.service.kiev.ProjectItem project =
                com.oracle.pic.networking.lvv.service.kiev.ProjectItem.builder()
                        .projectId(projectId)
                        .vendorName("vendor")
                        .build();
        when(projectItemDao.getProjectItem(projectId)).thenReturn(project);
        when(blockDetailsDao.getBlockDetailsForProject(projectId))
                .thenReturn(
                        List.of(
                                makeBlock("B1", "BLK1", projectId),
                                makeBlock("B2", "BLK2", projectId)));

        // Block 1
        Rack r1 = makeRack("B1", "BLK1", "S1");
        when(storeKeeperHelper.listRacks("BLK1", "B1", metricsScope)).thenReturn(List.of(r1));
        when(jiraSDService.findOpenTicketsBySerialForBlock("B1", "BLK1"))
                .thenReturn(Collections.emptyMap());

        // Block 2
        Rack r2 = makeRack("B2", "BLK2", "S2");
        when(storeKeeperHelper.listRacks("BLK2", "B2", metricsScope)).thenReturn(List.of(r2));
        when(jiraSDService.findOpenTicketsBySerialForBlock("B2", "BLK2"))
                .thenReturn(Collections.emptyMap());

        ProjectRack pr1 = mock(ProjectRack.class);
        ProjectRack pr2 = mock(ProjectRack.class);
        when(resourceModelTransformer.toModel(any(Rack.class), any(JiraTicket.class)))
                .thenReturn(pr1, pr2);

        List<ProjectRack> result = service.listProjectRacks(projectId, metricsScope);
        assertEquals(2, result.size());
        assertEquals(List.of(pr1, pr2), result);

        verify(jiraSDService).findOpenTicketsBySerialForBlock("B1", "BLK1");
        verify(jiraSDService).findOpenTicketsBySerialForBlock("B2", "BLK2");
        verify(storeKeeperHelper).listRacks("BLK1", "B1", metricsScope);
        verify(storeKeeperHelper).listRacks("BLK2", "B2", metricsScope);
        verify(resourceModelTransformer, times(2)).toModel(any(Rack.class), any(JiraTicket.class));
    }
}
