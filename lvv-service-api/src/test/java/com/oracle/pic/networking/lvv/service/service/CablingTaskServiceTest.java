package com.oracle.pic.networking.lvv.service.service;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueField;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.oracle.bmc.model.BmcException;
import com.oracle.pic.networking.lvv.service.dependencies.jira.JiraSDService;
import com.oracle.pic.networking.lvv.service.dependencies.ncp.NcpService;
import com.oracle.pic.networking.lvv.service.model.CablingTaskCollection;
import com.oracle.pic.networking.ncp.model.Job;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class CablingTaskServiceTest {

    @Mock private JiraSDService mockedJiraSDService;
    @Mock private NcpService mockedNcpService;
    @Mock private Issue mockIssue;

    private static final String BUILDING = "PHX1";
    private static final String BLOCK = "15";
    private static final String RACK_SERIAL_NUMBER = "1S7D9XCTO1WWJ102GBN7";
    private static final String CABLE_VALIDATION_TICKET_DESCRIPTION = "Ticket Description";
    private static final String TASK_ID = "DO-1191815";
    private static final String NCPJOB_ID = "e760441d-4dd7-4925-b3b4-3f90fa40283e";

    private CablingTaskService cablingTaskService;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.initMocks(this);
        this.cablingTaskService =
                new CablingTaskService(this.mockedJiraSDService, this.mockedNcpService);
    }

    @Test
    public void shouldGetCableValidationTasks() {
        // Setup cable validation search results
        String cableValidationJql =
                "project = \"DO\" AND summary ~ FinalRackValidation AND status = Open AND Building = PHX1 AND Block ~ 15 AND \"Serial Number\" ~ 1S7D9XCTO1WWJ102GBN7";
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

        // Setup initial cabling search results
        String initialCablingJql =
                "project = \"DO\" AND summary ~ \"Rack Deployment\" AND status = Open AND Building = PHX1 AND Block ~ 15 AND \"Serial Number\" ~ 1S7D9XCTO1WWJ102GBN7";
        List<Issue> initialCablingTickets = new LinkedList<>();
        SearchResult initialCablingSearchResult = new SearchResult(0, 0, 0, initialCablingTickets);
        when(this.mockedJiraSDService.searchJiraSD(initialCablingJql))
                .thenReturn(initialCablingSearchResult);

        CablingTaskCollection cablingTaskCollection =
                this.cablingTaskService.getCablingTasks(BUILDING, BLOCK, RACK_SERIAL_NUMBER);

        verify(this.mockedJiraSDService, times(1)).searchJiraSD(cableValidationJql);
        verify(this.mockedJiraSDService, times(1)).searchJiraSD(initialCablingJql);
        assertEquals(0, cablingTaskCollection.getInitialCablingTasks().size());
        assertEquals(
                CABLE_VALIDATION_TICKET_DESCRIPTION,
                cablingTaskCollection.getValidationFailureTasks().get(0).getFailureReason());
    }

    /*
    // TODO: unit test for this function
    @Test
    public void shouldResolveValidationFailureTask() {
        Issue issueMock = mock();
        when(this.mockedJiraSDService.getIssue(eq(TASK_ID))).thenReturn(issueMock);
        doNothing()
                .when(this.mockedJiraSDService)
                .resolveTicket(eq(TASK_ID), anyString(), anyString(), anyList());
        this.cablingTaskService.resolveValidationFailureTask(TASK_ID);
        verify(this.mockedJiraSDService).resolveTicket(eq(TASK_ID), anyString(), anyString(), anyList());
    }
     */

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

    @Test
    public void shouldGetValidationFailureTasks() {
        String cableValidationJql =
                "project = \"DO\" AND summary ~ FinalRackValidation AND status = Open AND Building = PHX1 AND Block ~ 15 AND \"Serial Number\" ~ 1S7D9XCTO1WWJ102GBN7";
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
                "project = \"DO\" AND summary ~ \"Rack Deployment\" AND status = Open AND Building = PHX1 AND Block ~ 15 AND \"Serial Number\" ~ 1S7D9XCTO1WWJ102GBN7";
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
        when(this.mockedNcpService.getNcpJob(anyString())).thenReturn(expectedJob);
        CablingTaskCollection cablingTaskCollection =
                this.cablingTaskService.getCablingTasks(BUILDING, BLOCK, RACK_SERIAL_NUMBER);

        verify(this.mockedJiraSDService, times(1)).searchJiraSD(cableValidationJql);
        verify(this.mockedJiraSDService, times(1)).searchJiraSD(initialCablingJql);
        assertEquals(
                NCPJOB_ID,
                cablingTaskCollection.getValidationFailureTasks().get(0).getFailureReason());
    }

    @Test
    void testGetResultFromNcpJobException() {
        String jobId = "sampleJobId";
        String fallbackResult = "fallbackResult";

        Mockito.when(mockedNcpService.getNcpJob(Mockito.anyString())).thenThrow(BmcException.class);

        CablingTaskService cablingTaskService =
                new CablingTaskService(this.mockedJiraSDService, this.mockedNcpService);

        String result = cablingTaskService.getResultFromNcpJob(jobId, fallbackResult);

        assertEquals(fallbackResult, result);
    }
}
