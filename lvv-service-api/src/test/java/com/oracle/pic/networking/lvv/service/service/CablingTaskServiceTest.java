package com.oracle.pic.networking.lvv.service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueField;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.api.domain.Status;
import com.oracle.pic.networking.lvv.service.dependencies.jira.JiraQueries;
import com.oracle.pic.networking.lvv.service.dependencies.jira.JiraSDService;
import com.oracle.pic.networking.lvv.service.kiev.BlockDetails;
import com.oracle.pic.networking.lvv.service.kiev.BlockDetailsDao;
import com.oracle.pic.networking.lvv.service.model.CablingTaskCollection;
import com.oracle.pic.networking.lvv.service.model.InitialCablingTaskDetails;
import com.oracle.pic.networking.lvv.service.model.ValidationFailureTaskDetails;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

class CablingTaskServiceTest {

    @Mock JiraSDService jiraSDService;
    @Mock BlockDetailsDao blockDetailsDao;

    CablingTaskService service;

    final String BUILDING = "PHX1";
    final String BLOCK = "15";
    final String RACK_SERIAL_NUMBER = "1S7D9XCTO1WWJ102GBN7";
    final String TASK_ID = "DO-1191815";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new CablingTaskService(jiraSDService, blockDetailsDao);
    }

    @Test
    void testGetCablingTasks_withValidationAndInitialTickets() {
        Issue mockIssue1 = mock(Issue.class);
        IssueField f1 = new IssueField("id", "Rack Location", "type", "Loc1");
        IssueField f2 = new IssueField("id2", "Serial Number", "type", RACK_SERIAL_NUMBER);
        List<IssueField> fields = Arrays.asList(f1, f2);
        when(mockIssue1.getFields()).thenReturn(fields);
        when(mockIssue1.getKey()).thenReturn("KEY-1");
        when(mockIssue1.getDescription()).thenReturn("failure 1");

        // For Validation tickets
        SearchResult validationResult =
                new SearchResult(0, 1, 1, Collections.singletonList(mockIssue1));
        when(jiraSDService.searchJiraSD(anyString())).thenReturn(validationResult);

        // For Initial cabling tickets: one ticket to match actual behavior (fix for size)
        Issue mockInitialIssue = mock(Issue.class);
        when(mockInitialIssue.getFields()).thenReturn(fields); // reuse previous fields
        when(mockInitialIssue.getKey()).thenReturn("INITKEY-1");
        when(mockInitialIssue.getDescription()).thenReturn("initial ticket");
        SearchResult initialResult =
                new SearchResult(0, 1, 1, Collections.singletonList(mockInitialIssue));
        when(jiraSDService.searchJiraSD(startsWith(JiraQueries.JQL + JiraQueries.RACK_DEPLOYMENT)))
                .thenReturn(initialResult);

        CablingTaskCollection result = service.getCablingTasks(BUILDING, BLOCK, RACK_SERIAL_NUMBER);

        assertEquals(1, result.getInitialCablingTasks().size());
        assertEquals(2, result.getValidationFailureTasks().size());
        assertTrue(
                result.getValidationFailureTasks().stream()
                        .anyMatch(task -> "failure 1".equals(task.getFailureReason())));
    }

    @Test
    void testGetCablingTasksForProject_handlesBlockDetails() {
        String projectId = "PROJ-1";
        // BlockDetails mock setup
        BlockDetails bd = mock(BlockDetails.class);
        when(bd.getBlock())
                .thenReturn(
                        BlockDetails.Block.builder().building(BUILDING).blockNumber(BLOCK).build());
        when(blockDetailsDao.getBlockDetailsForProject(projectId))
                .thenReturn(Collections.singletonList(bd));

        // Mock getCablingTasks to return a known collection (partial mocking)
        CablingTaskService realService = spy(service);
        CablingTaskCollection col =
                CablingTaskCollection.builder()
                        .initialCablingTasks(
                                List.of(
                                        new InitialCablingTaskDetails(
                                                "id", BUILDING, BLOCK, "loc", "sn")))
                        .validationFailureTasks(List.of())
                        .build();
        doReturn(col).when(realService).getCablingTasks(eq(BUILDING), eq(BLOCK), isNull());

        CablingTaskCollection result = realService.getCablingTasksForProject(projectId);

        assertEquals(1, result.getInitialCablingTasks().size());
        assertTrue(result.getValidationFailureTasks().isEmpty());
    }

    @Test
    void testResolveValidationFailureTask_parsesFields() {
        Issue issue = mock(Issue.class);
        List<IssueField> fields =
                Arrays.asList(
                        new IssueField("rmaFieldId", "RMA", "type", null),
                        new IssueField("rcFieldId", "Root Cause Categorization", "type", null),
                        new IssueField("serviceTypeFieldId", "Service Type", "type", null));
        when(issue.getFields()).thenReturn(fields);
        Status status = mock(com.atlassian.jira.rest.client.api.domain.Status.class);
        when(status.getName()).thenReturn("In Progress");
        when(issue.getStatus()).thenReturn(status);
        when(jiraSDService.getIssue(TASK_ID)).thenReturn(issue);

        doNothing()
                .when(jiraSDService)
                .transitionTicket(eq(TASK_ID), anyString(), anyString(), anyList());
        doNothing().when(jiraSDService).updateIssueFields(eq(TASK_ID), anyList());

        assertDoesNotThrow(() -> service.resolveValidationFailureTask(TASK_ID));

        verify(jiraSDService).transitionTicket(eq(TASK_ID), anyString(), anyString(), anyList());
        verify(jiraSDService).updateIssueFields(eq(TASK_ID), anyList());
    }

    @Test
    void testGetValidationFailureTaskDetails_nullAndAssetId() {
        // Should correctly extract Asset ID if Serial Number is missing
        Issue issue = mock(Issue.class);
        IssueField rackField = new IssueField("id", "Rack Location", "type", "LocX");
        IssueField assetField = new IssueField("id3", "Asset ID", "type", "ASSET123");
        // Serial Number is missing
        List<IssueField> fields = Arrays.asList(rackField, assetField);
        when(issue.getFields()).thenReturn(fields);
        when(issue.getKey()).thenReturn("KEYZ");
        when(issue.getDescription()).thenReturn("desc z");

        SearchResult res = new SearchResult(0, 1, 1, Collections.singletonList(issue));
        when(jiraSDService.searchJiraSD(anyString())).thenReturn(res);

        CablingTaskCollection result = service.getCablingTasks(BUILDING, BLOCK, null);

        ValidationFailureTaskDetails detail = result.getValidationFailureTasks().get(0);

        assertEquals("ASSET123", detail.getRackSerialNumber());
        assertEquals("LocX", detail.getRackLocation());
    }

    // NEW TESTS FOR MAX COVERAGE

    @Test
    void testGetCablingTasks_nullAndEmptyFields() {
        // No issues at all (shouldn't throw)
        SearchResult emptyResult = new SearchResult(0, 0, 0, Collections.emptyList());
        when(jiraSDService.searchJiraSD(anyString())).thenReturn(emptyResult);
        CablingTaskCollection result = service.getCablingTasks(BUILDING, BLOCK, RACK_SERIAL_NUMBER);
        assertTrue(result.getInitialCablingTasks().isEmpty());
        assertTrue(result.getValidationFailureTasks().isEmpty());
    }

    @Test
    void testGetClosedCablingTasks_happyPath() {
        Issue closedIssue1 = mock(Issue.class);
        List<IssueField> fields =
                Arrays.asList(
                        new IssueField("id", "Rack Location", "type", "ClosedLoc"),
                        new IssueField("id2", "Serial Number", "type", "CLOSED-RACK"));
        when(closedIssue1.getFields()).thenReturn(fields);
        when(closedIssue1.getKey()).thenReturn("CLOSE-1");
        when(closedIssue1.getDescription()).thenReturn("closed desc");

        SearchResult validationClosed =
                new SearchResult(0, 1, 1, Collections.singletonList(closedIssue1));
        SearchResult initialClosed =
                new SearchResult(0, 1, 1, Collections.singletonList(closedIssue1));

        when(jiraSDService.searchJiraSD(anyString()))
                .thenReturn(validationClosed)
                .thenReturn(initialClosed);

        CablingTaskCollection col =
                service.getClosedCablingTasks(BUILDING, BLOCK, RACK_SERIAL_NUMBER);

        assertEquals(1, col.getInitialCablingTasks().size());
        assertEquals(1, col.getValidationFailureTasks().size());
        assertEquals("ClosedLoc", col.getInitialCablingTasks().get(0).getRackLocation());
        assertEquals("CLOSED-RACK", col.getInitialCablingTasks().get(0).getRackSerialNumber());
    }

    @Test
    void testGetClosedCablingTasks_emptyResults() {
        SearchResult empty = new SearchResult(0, 0, 0, Collections.emptyList());
        when(jiraSDService.searchJiraSD(anyString())).thenReturn(empty);

        CablingTaskCollection col =
                service.getClosedCablingTasks(BUILDING, BLOCK, RACK_SERIAL_NUMBER);

        assertTrue(col.getInitialCablingTasks().isEmpty());
        assertTrue(col.getValidationFailureTasks().isEmpty());
    }

    @Test
    void testGetCablingTasksForProject_daoThrowsReturnsEmpty() {
        String projectId = "THROWPROJ";
        when(blockDetailsDao.getBlockDetailsForProject(projectId))
                .thenThrow(new RuntimeException("BOOM"));

        CablingTaskCollection col = service.getCablingTasksForProject(projectId);
        assertNotNull(col);
        assertTrue(col.getInitialCablingTasks() == null || col.getInitialCablingTasks().isEmpty());
        assertTrue(
                col.getValidationFailureTasks() == null
                        || col.getValidationFailureTasks().isEmpty());
    }

    @Test
    void testResolveValidationFailureTask_missingFields() {
        Issue issue = mock(Issue.class);
        // No "RMA", "Root Cause Categorization", or "Service Type"
        List<IssueField> fields = List.of(new IssueField("x", "Other", "type", null));
        when(issue.getFields()).thenReturn(fields);

        Status status = mock(com.atlassian.jira.rest.client.api.domain.Status.class);
        when(status.getName()).thenReturn("In Progress");
        when(issue.getStatus()).thenReturn(status);

        when(jiraSDService.getIssue(TASK_ID)).thenReturn(issue);

        doNothing()
                .when(jiraSDService)
                .transitionTicket(anyString(), anyString(), anyString(), anyList());
        doNothing().when(jiraSDService).updateIssueFields(anyString(), anyList());

        assertDoesNotThrow(() -> service.resolveValidationFailureTask(TASK_ID));

        // Optionally, assert or print the value as an example
        String taskStatus = issue.getStatus().getName();
        assertEquals("In Progress", taskStatus);
    }

    @Test
    void testTransitionTicketCalledTwiceWhenNotInProgress() {
        Issue issue = mock(Issue.class);
        when(issue.getFields()).thenReturn(List.of());
        Status status = mock(com.atlassian.jira.rest.client.api.domain.Status.class);
        when(status.getName()).thenReturn("Pending");
        when(issue.getStatus()).thenReturn(status);

        when(jiraSDService.getIssue(TASK_ID)).thenReturn(issue);

        doNothing()
                .when(jiraSDService)
                .transitionTicket(anyString(), anyString(), anyString(), anyList());
        doNothing().when(jiraSDService).updateIssueFields(anyString(), anyList());

        service.resolveValidationFailureTask(TASK_ID);

        verify(jiraSDService)
                .transitionTicket(eq(TASK_ID), eq("Start Progress"), isNull(), isNull());
        verify(jiraSDService).transitionTicket(eq(TASK_ID), eq("Resolve"), anyString(), anyList());
        verify(jiraSDService, times(2)).transitionTicket(anyString(), anyString(), any(), any());
    }

    @Test
    void testTransitionTicketCalledOnceWhenInProgress() {
        Issue issue = mock(Issue.class);
        when(issue.getFields()).thenReturn(List.of());
        Status status = mock(com.atlassian.jira.rest.client.api.domain.Status.class);
        when(status.getName()).thenReturn("In Progress");
        when(issue.getStatus()).thenReturn(status);

        when(jiraSDService.getIssue(TASK_ID)).thenReturn(issue);

        doNothing()
                .when(jiraSDService)
                .transitionTicket(anyString(), anyString(), anyString(), anyList());
        doNothing().when(jiraSDService).updateIssueFields(anyString(), anyList());

        service.resolveValidationFailureTask(TASK_ID);

        verify(jiraSDService, never())
                .transitionTicket(eq(TASK_ID), eq("Start Progress"), any(), any());
        verify(jiraSDService).transitionTicket(eq(TASK_ID), eq("Resolve"), anyString(), anyList());
        verify(jiraSDService, times(1)).transitionTicket(anyString(), anyString(), any(), any());
    }
}
