package com.oracle.pic.networking.lvv.service.service;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueField;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.oracle.pic.networking.lvv.service.dependencies.jira.JiraQueries;
import com.oracle.pic.networking.lvv.service.dependencies.jira.JiraSDService;
// import com.oracle.pic.networking.lvv.service.dependencies.ncp.NcpService;
import com.oracle.pic.networking.lvv.service.kiev.BlockDetailsDao;
import com.oracle.pic.networking.lvv.service.model.CableValidationFailureTasks;
import com.oracle.pic.networking.lvv.service.model.CablingTaskCollection;
import com.oracle.pic.networking.ncp.model.Job;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class CablingTaskServiceTest {

    @Mock private JiraSDService mockedJiraSDService;
    // @Mock private NcpService mockedNcpService;
    @Mock private Issue mockIssue;
    @Mock private BlockDetailsDao blockDetailsDao;

    private static final String BUILDING = "PHX1";
    private static final String BLOCK = "15";
    private static final String RACK_SERIAL_NUMBER = "1S7D9XCTO1WWJ102GBN7";
    private static final String CABLE_VALIDATION_TICKET_DESCRIPTION = "Ticket Description";
    private static final String GPU_CABLE_VALIDATION_TICKET_DESCRIPTION = "GPU Ticket Description";
    private static final String TASK_ID = "DO-1191815";
    private static final String NCPJOB_ID = "e760441d-4dd7-4925-b3b4-3f90fa40283e";

    private static final String TICKET_DESCRIPTION =
            "One or more of the validation tests have failures.\n"
                    + "Please resolve issue then close ticket to retry rack validation.\n"
                    + "If the validation results are empty, message us on #oci_nw_automation.\n"
                    + "Validation Progress: completed\n"
                    + "Validation Status: Failure\n"
                    + "Validation Results: \n"
                    + "{panel:title=phx1-c1-b8-t0-r26-u1}\n"
                    + "{color:green}*Status: Success*{color}\n"
                    + "==== Test Results ====\n"
                    + "{color:green}*Passed:* test_snmp_reachability, test_lldp, test_power, test_fans, test_optics, test_firmware_version{color}\n"
                    + "{panel}\n"
                    + "{panel:title=phx1-c1-b8-t0-r26}\n"
                    + "{color:red}*Status: Failure*{color}\n"
                    + "==== Test Results ====\n"
                    + "{color:red}*Failed:* test_lldp{color}\n"
                    + "{noformat}Failed: {\"message\": \"LLDP Failures: \\nTotal error count: 4\", \"errors_object\": [{\"current_origin\": \"phx1-c1-b8-t0-r26:Ethernet1/13:phx1:10641:42\", \"current_destination\": \"Unknown:Unknown:Unknown\", \"expected_destination\": \"phx1-c1-b8-t1-r5:Ethernet20/1:phx1:1753:14\"}, {\"current_origin\": \"phx1-c1-b8-t0-r26:Ethernet1/14:phx1:10641:42\", \"current_destination\": \"Unknown:Unknown:Unknown\", \"expected_destination\": \"phx1-c1-b8-t1-r6:Ethernet20/1:phx1:1753:15\"}, {\"current_origin\": \"phx1-c1-b8-t0-r26:Ethernet1/15:phx1:10641:42\", \"current_destination\": \"Unknown:Unknown:Unknown\", \"expected_destination\": \"phx1-c1-b8-t1-r7:Ethernet20/1:phx1:1753:16\"}, {\"current_origin\": \"phx1-c1-b8-t0-r26:Ethernet1/16:phx1:10641:42\", \"current_destination\": \"Unknown:Unknown:Unknown\", \"expected_destination\": \"phx1-c1-b8-t1-r8:Ethernet20/1:phx1:1753:17\"}]}{noformat}\n"
                    + "{color:red}*Failed:* test_optics{color}\n"
                    + "{noformat}Failed: {\"message\": \"Optics have 4.0 < channel power < -6.0 \\n Please reseat the cable or replace the bad cable\" \"errors_object\": [{\"device\": \"phx1-c1-b8-t0-r26\", \"intf_name\": \"Ethernet1/13\", \"input_power\": \"-100.0\", \"output_power\": \"2.16\", \"device_phys\": \"phx1:10641:42\"},  {\"device\": \"phx1-c1-b8-t0-r26\", \"intf_name\": \"Ethernet1/14\", \"input_power\": \"-30.0\", \"output_power\": \"2.65\", \"device_phys\": \"phx1:10641:42\",  {\"device\": \"phx1-c1-b8-t0-r26\", \"intf_name\": \"Ethernet1/15\", \"input_power\": \"-30.0\", \"output_power\": \"2.82\", \"device_phys\": \"phx1:10641:42\",  {\"device\": \"phx1-c1-b8-t0-r26\", \"intf_name\": \"Ethernet1/16\", \"input_power\": \"-100.0\", \"output_power\": \"2.04\", \"device_phys\": \"phx1:10641:42\"}]}{noformat}"
                    + "{color:red}*Failed:* test_interfaces{color}\n"
                    + "{noformat}Failed: Device phx1-c1-b8-t0-r26 interfaces are not enabled or up: ['Ethernet1/13', 'Ethernet1/14', 'Ethernet1/15', 'Ethernet1/16']{noformat}\n"
                    + "{color:green}*Passed:* test_snmp_reachability, test_power, test_fans, test_firmware_version, test_bgp{color}\n"
                    + "{panel}\n"
                    + "Workflow Definition: rack_validation_workflow v5.11\n"
                    + "Workflow GUID: 450176c8-e246-4cf0-a633-a61382a2f454 \n"
                    + "        ----\n"
                    + "\n"
                    + "        ||Serial|2409XL8010|\n"
                    + "        ||Building|phx9|\n"
                    + "        ||Rack|10641|\n"
                    + "        ||Rack Type|COM_NVME_E4-2C_ORT_9336_RACK.01|\n"
                    + "        ||*Links*| [*Atlas*|https://atlas.oci.oraclecorp.com/assets/sk-cb813531-f469-4e03-9dcb-6077c349fb4b] - [*History*|https://jira-sd.mc1.oracleiaas.com/issues/?jql=project%20%3D%20%22DO%22%20AND%20text%20~%202409XL8010%20ORDER%20BY%20created%20DESC]|\n"
                    + "\n"
                    + "        \n"
                    + "        ----\n"
                    + "\n"
                    + "        ||Serial|2409XL801L|\n"
                    + "        ||Building|phx1|\n"
                    + "        ||Rack|4107|\n"
                    + "        ||Rack Type|COM_NVME_E4-2C_ORT_9336_RACK.01|\n"
                    + "        ||*Links*| [*Atlas*|https://atlas.oci.oraclecorp.com/assets/sk-11a40a4b-4e73-430e-876a-392b4a3166e7] - [*History*|https://jira-sd.mc1.oracleiaas.com/issues/?jql=project%20%3D%20%22DO%22%20AND%20text%20~%202409XL801L%20ORDER%20BY%20created%20DESC]|\n";

    private static final String GPU_TICKET_DESCRIPTION =
            "Links that seems wrongly cabled:\n"
                    + "\n"
                    + "mlx5_14 bd:00.0\n"
                    + "Rack:5818 IOB slot:14 Port 1 Cable Serial Number: 24042A110549 needs to be connected to Rack:5810 Switch iad32-q2-b18-t0-r6 Elevation:33 Port Ethernet32/1\n"
                    + "Currently connected to Rack:5810 Switch iad32-q2-b18-t0-r6 Elevation:33 Port Ethernet32/5\n"
                    + "\n"
                    + "mlx5_15 bd:00.1\n"
                    + "Rack:5818 IOB slot:14 Port 2 Cable Serial Number: 24042A110548 needs to be connected to Rack:5810 Switch iad32-q2-b18-t0-r6 Elevation:33 Port Ethernet32/5\n"
                    + "Currently connected to Rack:5810 Switch iad32-q2-b18-t0-r6 Elevation:33 Port Ethernet32/1\n"
                    + "CHS/DO is authorized by Compute to work on this host in the allocated state in the holding pool with the CPV instance.\n"
                    + "This is a non-terminating repair.\n"
                    + "{panel:title=Compute Product Validation|borderStyle=hidden|titleBGColor=#8ebbfa|bgColor=#a8ccff}\n"
                    + "*Job ID:* 3a9026bc-45d0-4db0-a8d4-1dd76b63f945\n"
                    + "*Test Name:* cable_validation\n"
                    + "*Asset ID:* 2350XLG0C3\n"
                    + "*Need help?:* Submit a ticket to the HPC queue\n"
                    + "{panel}";
    private CablingTaskService cablingTaskService;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.initMocks(this);
        this.cablingTaskService =
                new CablingTaskService(this.mockedJiraSDService, this.blockDetailsDao);
    }

    @Test
    public void shouldGetCableValidationTasks() {
        // Setup cable validation search results
        String cableValidationJql =
                String.format(
                        JiraQueries.JQL + JiraQueries.FINAL_VALIDATION + JiraQueries.SERIAL_NUMBER,
                        BUILDING,
                        BLOCK,
                        RACK_SERIAL_NUMBER);
        List<Issue> cableValidationTickets = new LinkedList<>();
        Issue cableValidationTicket = mock();
        when(cableValidationTicket.getDescription())
                .thenReturn(CABLE_VALIDATION_TICKET_DESCRIPTION);
        IssueField issueField = new IssueField("id", "name", "type", RACK_SERIAL_NUMBER);
        List<IssueField> issueFields = new LinkedList<>();
        issueFields.add(issueField);
        when(cableValidationTicket.getFields()).thenReturn(issueFields);
        cableValidationTickets.add(cableValidationTicket);
        SearchResult cableValidationSearchResult =
                new SearchResult(0, 1, 1, cableValidationTickets);
        when(this.mockedJiraSDService.searchJiraSD(eq(cableValidationJql)))
                .thenReturn(cableValidationSearchResult);

        String gpuCableValidationJql =
                String.format(
                        JiraQueries.JQL + JiraQueries.GPU_VALIDATION + JiraQueries.SERIAL_NUMBER,
                        BUILDING,
                        BLOCK,
                        RACK_SERIAL_NUMBER);
        List<Issue> gpuCableValidationTickets = new LinkedList<>();
        Issue gpuCableValidationTicket = mock();
        when(gpuCableValidationTicket.getDescription())
                .thenReturn(GPU_CABLE_VALIDATION_TICKET_DESCRIPTION);
        IssueField gpuIssueField = new IssueField("id", "name", "type", RACK_SERIAL_NUMBER);
        List<IssueField> gpuIssueFields = new LinkedList<>();
        gpuIssueFields.add(gpuIssueField);
        when(gpuCableValidationTicket.getFields()).thenReturn(gpuIssueFields);
        gpuCableValidationTickets.add(gpuCableValidationTicket);
        SearchResult gpuCableValidationSearchResult =
                new SearchResult(0, 1, 1, gpuCableValidationTickets);
        when(this.mockedJiraSDService.searchJiraSD(eq(gpuCableValidationJql)))
                .thenReturn(gpuCableValidationSearchResult);

        // Setup initial cabling search results
        String initialCablingJql =
                String.format(
                        JiraQueries.JQL + JiraQueries.RACK_DEPLOYMENT + JiraQueries.SERIAL_NUMBER,
                        BUILDING,
                        BLOCK,
                        RACK_SERIAL_NUMBER);
        List<Issue> initialCablingTickets = new LinkedList<>();
        SearchResult initialCablingSearchResult = new SearchResult(0, 0, 0, initialCablingTickets);
        when(this.mockedJiraSDService.searchJiraSD(initialCablingJql))
                .thenReturn(initialCablingSearchResult);

        CablingTaskCollection cablingTaskCollection =
                this.cablingTaskService.getCablingTasks(BUILDING, BLOCK, RACK_SERIAL_NUMBER);

        verify(this.mockedJiraSDService, times(1)).searchJiraSD(cableValidationJql);
        verify(this.mockedJiraSDService, times(1)).searchJiraSD(initialCablingJql);
        verify(this.mockedJiraSDService, times(1)).searchJiraSD(gpuCableValidationJql);
        assertEquals(0, cablingTaskCollection.getInitialCablingTasks().size());
        assertEquals(
                CABLE_VALIDATION_TICKET_DESCRIPTION,
                cablingTaskCollection.getValidationFailureTasks().get(0).getFailureReason());
        assertEquals(
                GPU_CABLE_VALIDATION_TICKET_DESCRIPTION,
                cablingTaskCollection.getValidationFailureTasks().get(1).getFailureReason());
    }

    @Test
    public void shouldGetClosedCableValidationTasks() {
        String closedCableValidationJql =
                String.format(
                        JiraQueries.JQL_CLOSED
                                + JiraQueries.FINAL_VALIDATION
                                + JiraQueries.SERIAL_NUMBER,
                        BUILDING,
                        BLOCK,
                        RACK_SERIAL_NUMBER);
        List<Issue> closedCableValidationTickets = new LinkedList<>();
        Issue closedCableValidationTicket = mock();
        when(closedCableValidationTicket.getDescription())
                .thenReturn(CABLE_VALIDATION_TICKET_DESCRIPTION);
        IssueField issueField = new IssueField("id", "name", "type", RACK_SERIAL_NUMBER);
        List<IssueField> issueFields = new LinkedList<>();
        issueFields.add(issueField);
        when(closedCableValidationTicket.getFields()).thenReturn(issueFields);
        closedCableValidationTickets.add(closedCableValidationTicket);
        SearchResult closedCableValidationSearchResult =
                new SearchResult(0, 1, 1, closedCableValidationTickets);
        when(this.mockedJiraSDService.searchJiraSD(eq(closedCableValidationJql)))
                .thenReturn(closedCableValidationSearchResult);

        // Setup initial cabling search results
        String closedInitialCablingJql =
                String.format(
                        JiraQueries.JQL_CLOSED
                                + JiraQueries.RACK_DEPLOYMENT
                                + JiraQueries.SERIAL_NUMBER,
                        BUILDING,
                        BLOCK,
                        RACK_SERIAL_NUMBER);
        List<Issue> closedInitialCablingTickets = new LinkedList<>();
        SearchResult closedInitialCablingSearchResult =
                new SearchResult(0, 0, 0, closedInitialCablingTickets);
        when(this.mockedJiraSDService.searchJiraSD(closedInitialCablingJql))
                .thenReturn(closedInitialCablingSearchResult);

        CablingTaskCollection cablingTaskCollection =
                this.cablingTaskService.getClosedCablingTasks(BUILDING, BLOCK, RACK_SERIAL_NUMBER);

        verify(this.mockedJiraSDService, times(1)).searchJiraSD(closedCableValidationJql);
        verify(this.mockedJiraSDService, times(1)).searchJiraSD(closedInitialCablingJql);
        assertEquals(0, cablingTaskCollection.getInitialCablingTasks().size());
        assertEquals(
                CABLE_VALIDATION_TICKET_DESCRIPTION,
                cablingTaskCollection.getValidationFailureTasks().get(0).getFailureReason());
    }

    @Test
    public void shouldResolveValidationFailureTask() {
        Issue issueMock = mock();
        when(this.mockedJiraSDService.getIssue(eq(TASK_ID))).thenReturn(issueMock);

        IssueField issueFieldRma = new IssueField("rmaFieldId", "RMA", "type", null);
        IssueField issueFieldRootCauseCategorization =
                new IssueField(
                        "rootCauseCategorizationId", "Root Cause Categorization", "type", null);
        IssueField issueFieldServiceType =
                new IssueField("serviceTypeFieldId", "Service Type", "type", null);
        List<IssueField> issueFields = new LinkedList<>();
        issueFields.add(issueFieldRma);
        issueFields.add(issueFieldRootCauseCategorization);
        issueFields.add(issueFieldServiceType);
        when(issueMock.getFields()).thenReturn(issueFields);

        doNothing()
                .when(this.mockedJiraSDService)
                .resolveTicket(eq(TASK_ID), anyString(), anyList());
        this.cablingTaskService.resolveValidationFailureTask(TASK_ID);
        verify(this.mockedJiraSDService).resolveTicket(eq(TASK_ID), anyString(), anyList());
    }

    @Test
    public void shouldGetCablingTasks() {
        Issue mockIssue = Mockito.mock(Issue.class);
        List<Issue> mockIssues = new LinkedList<>();
        mockIssues.add(mockIssue);

        SearchResult mockResult = new SearchResult(0, 1, 1, mockIssues);
        when(this.mockedJiraSDService.searchJiraSD(anyString())).thenReturn(mockResult);

        CablingTaskCollection cablingTaskCollection =
                this.cablingTaskService.getCablingTasks(BUILDING, BLOCK, RACK_SERIAL_NUMBER);

        assertEquals(1, cablingTaskCollection.getInitialCablingTasks().size());
        assertEquals(2, cablingTaskCollection.getValidationFailureTasks().size());
    }

    @Test
    public void shouldGetClosedCablingTasks() {
        Issue mockIssue = Mockito.mock(Issue.class);
        List<Issue> mockIssues = new LinkedList<>();
        mockIssues.add(mockIssue);

        SearchResult mockResult = new SearchResult(0, 1, 1, mockIssues);
        when(this.mockedJiraSDService.searchJiraSD(anyString())).thenReturn(mockResult);

        CablingTaskCollection cablingTaskCollection =
                this.cablingTaskService.getClosedCablingTasks(BUILDING, BLOCK, RACK_SERIAL_NUMBER);

        assertEquals(1, cablingTaskCollection.getInitialCablingTasks().size());
        assertEquals(1, cablingTaskCollection.getValidationFailureTasks().size());
    }

    @Test
    public void shouldGetCableValidationFailureTask() {
        Issue mockIssue = mock();
        when(this.mockedJiraSDService.getIssue(TASK_ID)).thenReturn(mockIssue);
        when(mockIssue.getDescription()).thenReturn(TICKET_DESCRIPTION);
        CableValidationFailureTasks cableValidationFailureTasks =
                this.cablingTaskService.getCableValidationFailureTask(TASK_ID);
        cableValidationFailureTasks.getOpticsFailures();
        assertEquals(4, cableValidationFailureTasks.getLldpFailures().size());
        assertEquals(4, cableValidationFailureTasks.getOpticsFailures().size());
    }

    @Test
    public void shouldGetGpuCableValidationFailureTask() {
        Issue mockIssue = mock();
        when(this.mockedJiraSDService.getIssue(TASK_ID)).thenReturn(mockIssue);
        when(mockIssue.getDescription()).thenReturn(GPU_TICKET_DESCRIPTION);
        CableValidationFailureTasks cableValidationFailureTasks =
                this.cablingTaskService.getCableValidationFailureTask(TASK_ID);
        assertEquals(2, cableValidationFailureTasks.getGpuLldpFailures().size());
    }

    // TODO: Uncomment when LVV can get job results from NCP
    /*
    @Test
    public void shouldGetCableValidationFailureTask() {
        String expectedDetails = "details";
        Set<String> labels = new HashSet<>();
        labels.add("NCP_JOB_ID:" + NCPJOB_ID);
        Issue mockIssue = mock(Issue.class);
        when(mockIssue.getLabels()).thenReturn(labels);
        Job mockJob = mock(Job.class);
        when(mockJob.toString()).thenReturn(expectedDetails);
        when(this.mockedJiraSDService.getIssue(anyString())).thenReturn(mockIssue);
        when(this.mockedNcpService.getNcpJob(anyString())).thenReturn(mockJob);

        String actualIssueDetails =
                this.cablingTaskService.getCableValidationFailureTask(NCPJOB_ID);

        verify(this.mockedNcpService, times(1)).getNcpJob(anyString());
        assertEquals(expectedDetails, actualIssueDetails);
    }
     */

    @Test
    public void shouldGetValidationFailureTasks() {
        String cableValidationJql =
                String.format(
                        JiraQueries.JQL + JiraQueries.FINAL_VALIDATION + JiraQueries.SERIAL_NUMBER,
                        BUILDING,
                        BLOCK,
                        RACK_SERIAL_NUMBER);
        List<Issue> cableValidationTickets = new LinkedList<>();
        Issue cableValidationTicket = mock(Issue.class);
        when(cableValidationTicket.getDescription())
                .thenReturn(CABLE_VALIDATION_TICKET_DESCRIPTION);
        IssueField issueFieldNcpID = new IssueField("NCP-JOBID", "name", "type", NCPJOB_ID);
        List<IssueField> issueFields = new LinkedList<>();
        issueFields.add(issueFieldNcpID);
        when(cableValidationTicket.getField("NCP-JOBID")).thenReturn(issueFieldNcpID);
        cableValidationTickets.add(cableValidationTicket);
        SearchResult cableValidationSearchResult =
                new SearchResult(0, 1, 1, cableValidationTickets);
        when(this.mockedJiraSDService.searchJiraSD(eq(cableValidationJql)))
                .thenReturn(cableValidationSearchResult);

        String initialCablingJql =
                String.format(
                        JiraQueries.JQL + JiraQueries.RACK_DEPLOYMENT + JiraQueries.SERIAL_NUMBER,
                        BUILDING,
                        BLOCK,
                        RACK_SERIAL_NUMBER);
        List<Issue> initialCablingTickets = new LinkedList<>();
        SearchResult initialCablingSearchResult = new SearchResult(0, 0, 0, initialCablingTickets);
        when(this.mockedJiraSDService.searchJiraSD(initialCablingJql))
                .thenReturn(initialCablingSearchResult);
        Issue mockIssue = Mockito.mock(Issue.class);
        when(mockIssue.getDescription()).thenReturn(NCPJOB_ID);
        List<Issue> mockIssues = new LinkedList<>();
        mockIssues.add(mockIssue);

        SearchResult mockResult = new SearchResult(0, 1, 1, mockIssues);
        when(this.mockedJiraSDService.searchJiraSD(anyString())).thenReturn(mockResult);
        Job expectedJob =
                Job.builder().id(NCPJOB_ID).endDate(new Date()).state(Job.State.Succeeded).build();
        // when(this.mockedNcpService.getNcpJob(anyString())).thenReturn(expectedJob);
        CablingTaskCollection cablingTaskCollection =
                this.cablingTaskService.getCablingTasks(BUILDING, BLOCK, RACK_SERIAL_NUMBER);

        verify(this.mockedJiraSDService, times(1)).searchJiraSD(cableValidationJql);
        verify(this.mockedJiraSDService, times(1)).searchJiraSD(initialCablingJql);
        assertEquals(
                NCPJOB_ID,
                cablingTaskCollection.getValidationFailureTasks().get(0).getFailureReason());
    }
}
