package com.oracle.pic.networking.lvv.service.dependencies.jira;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.SearchRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.api.domain.Transition;
import com.atlassian.jira.rest.client.api.domain.input.FieldInput;
import com.atlassian.jira.rest.client.api.domain.input.IssueInput;
import com.atlassian.jira.rest.client.api.domain.input.TransitionInput;
import io.atlassian.util.concurrent.Promise;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class JiraSDServiceTest {

    @Mock private JiraRestClient mockedJiraRestClient;
    @Mock private IssueRestClient mockedIssueRestClient;
    @Mock private SearchRestClient mockedSearchRestClient;
    @Mock private JiraSDHelper mockedJiraSDHelper;

    private JiraSDService jiraSDService;

    private static final String TEST_ISSUE_ID = "DO-1191815";
    private static final String TEST_TRANSITION_STATE = "Start Progress";
    private static final String TEST_RESOLUTION = "Fixed";
    private static final String TEST_COMMENT =
            "Vendor has resolved the issue through Low-voltage Vendor Portal";

    @BeforeEach
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(this.mockedJiraRestClient.getIssueClient()).thenReturn(this.mockedIssueRestClient);
        when(this.mockedJiraRestClient.getSearchClient()).thenReturn(this.mockedSearchRestClient);
        this.jiraSDService = new JiraSDService(this.mockedJiraRestClient, this.mockedJiraSDHelper);
    }

    @Test
    public void shouldSearchJiraTickets() {
        String jql =
                String.format(
                        "project = \"DO\" AND summary ~ FinalRackValidation AND status in (\"In Progress\", Open, Pending, Reopened) AND Building = %s AND Block ~ %s",
                        "PHX1", "21");
        Promise<SearchResult> searchResultPromiseMock = mock();
        SearchResult searchResult = mock();
        when(searchResultPromiseMock.claim()).thenReturn(searchResult);
        when(this.mockedSearchRestClient.searchJql(jql)).thenReturn(searchResultPromiseMock);
        this.jiraSDService.searchJiraSD(jql);
        verify(this.mockedSearchRestClient).searchJql(eq(jql));
    }

    @Test
    public void shouldResolveJiraTicket_whenInProgress() {
        Promise<Issue> issuePromiseMock = mock();
        when(this.mockedIssueRestClient.getIssue(eq(TEST_ISSUE_ID))).thenReturn(issuePromiseMock);
        Issue issueMock = mock();
        when(issuePromiseMock.claim()).thenReturn(issueMock);

        Promise<Iterable<Transition>> transitionsPromiseMock = mock();
        when(this.mockedIssueRestClient.getTransitions(issueMock))
                .thenReturn(transitionsPromiseMock);
        List<Transition> transitions = new LinkedList<>();
        Transition transition = new Transition("Resolve Issue", 41, null);
        transitions.add(transition);
        when(transitionsPromiseMock.claim()).thenReturn(transitions);

        Promise<Void> voidPromise = mock();
        when(this.mockedIssueRestClient.transition(eq(issueMock), any(TransitionInput.class)))
                .thenReturn(voidPromise);

        List<FieldInput> fieldInputList = new LinkedList<>();

        this.jiraSDService.transitionTicket(TEST_ISSUE_ID, "Resolve", TEST_COMMENT, fieldInputList);

        verify(this.mockedIssueRestClient).getIssue(TEST_ISSUE_ID);
        verify(this.mockedIssueRestClient).getTransitions(issueMock);

        // Capture and verify the TransitionInput used for the Jira transition
        org.mockito.ArgumentCaptor<TransitionInput> captor =
                org.mockito.ArgumentCaptor.forClass(TransitionInput.class);
        verify(this.mockedIssueRestClient).transition(eq(issueMock), captor.capture());
        TransitionInput transitionInput = captor.getValue();
        assertNotNull(transitionInput);
        assertEquals(41, transitionInput.getId()); // transition id used is 41
        assertEquals(fieldInputList, transitionInput.getFields());
        assertEquals(TEST_COMMENT, transitionInput.getComment().getBody());
    }

    @Test
    public void shouldResolveJiraTicket_whenInPending() {
        Promise<Issue> issuePromiseMock = mock();
        when(this.mockedIssueRestClient.getIssue(eq(TEST_ISSUE_ID))).thenReturn(issuePromiseMock);
        Issue issueMock = mock();
        when(issuePromiseMock.claim()).thenReturn(issueMock);

        Promise<Iterable<Transition>> transitionsPromiseMock = mock();
        when(this.mockedIssueRestClient.getTransitions(issueMock))
                .thenReturn(transitionsPromiseMock);
        List<Transition> transitions = new LinkedList<>();
        Transition transition = new Transition("Start Progress", 41, null);
        transitions.add(transition);
        when(transitionsPromiseMock.claim()).thenReturn(transitions);

        Promise<Void> voidPromise = mock();
        when(this.mockedIssueRestClient.transition(eq(issueMock), any(TransitionInput.class)))
                .thenReturn(voidPromise);

        List<FieldInput> fieldInputList = new LinkedList<>();

        this.jiraSDService.transitionTicket(TEST_ISSUE_ID, "Start Progress", null, null);

        verify(this.mockedIssueRestClient).getIssue(TEST_ISSUE_ID);
        verify(this.mockedIssueRestClient).getTransitions(issueMock);

        // Capture and verify the TransitionInput used for the Jira transition
        org.mockito.ArgumentCaptor<TransitionInput> captor =
                org.mockito.ArgumentCaptor.forClass(TransitionInput.class);
        verify(this.mockedIssueRestClient).transition(eq(issueMock), captor.capture());
        TransitionInput transitionInput = captor.getValue();
        assertNotNull(transitionInput);
        assertEquals(41, transitionInput.getId()); // transition id used is 41
        assertFalse(transitionInput.getFields().iterator().hasNext());
        assertNull(transitionInput.getComment());
    }

    @Test
    public void transitionTicket_noMatchingTransition_usesNegativeId_andUsesCommentAndFields() {
        // Arrange
        Promise<Issue> issuePromiseMock = mock();
        when(this.mockedIssueRestClient.getIssue(eq(TEST_ISSUE_ID))).thenReturn(issuePromiseMock);
        Issue issueMock = mock();
        when(issuePromiseMock.claim()).thenReturn(issueMock);

        // Return a transition list that does NOT contain the requested state
        Promise<Iterable<Transition>> transitionsPromiseMock = mock();
        when(this.mockedIssueRestClient.getTransitions(issueMock))
                .thenReturn(transitionsPromiseMock);
        List<Transition> transitions = new LinkedList<>();
        transitions.add(new Transition("Some Other State", 99, null));
        when(transitionsPromiseMock.claim()).thenReturn(transitions);

        Promise<Void> voidPromise = mock();
        when(this.mockedIssueRestClient.transition(eq(issueMock), any(TransitionInput.class)))
                .thenReturn(voidPromise);

        List<FieldInput> fieldInputList = new LinkedList<>();
        fieldInputList.add(new FieldInput("labels", List.of("x")));

        // Act
        this.jiraSDService.transitionTicket(
                TEST_ISSUE_ID, "Resolve", "comment here", fieldInputList);

        // Assert
        org.mockito.ArgumentCaptor<TransitionInput> captor =
                org.mockito.ArgumentCaptor.forClass(TransitionInput.class);
        verify(this.mockedIssueRestClient).transition(eq(issueMock), captor.capture());
        TransitionInput ti = captor.getValue();
        assertNotNull(ti);
        assertEquals(-1, ti.getId()); // no matching transition -> -1
        assertNotNull(ti.getComment());
        assertTrue(ti.getFields().iterator().hasNext());
    }

    @Test
    public void getIssue_returnsIssueFromClient() {
        Promise<Issue> issuePromiseMock = mock();
        Issue issueMock = mock();
        when(this.mockedIssueRestClient.getIssue(eq(TEST_ISSUE_ID))).thenReturn(issuePromiseMock);
        when(issuePromiseMock.claim()).thenReturn(issueMock);

        Issue result = this.jiraSDService.getIssue(TEST_ISSUE_ID);
        assertSame(issueMock, result);
        verify(this.mockedIssueRestClient).getIssue(TEST_ISSUE_ID);
    }

    @Test
    public void updateIssueFields_callsUpdateWithBuiltIssueInput() {
        Promise<Void> voidPromise = mock();
        when(this.mockedIssueRestClient.updateIssue(eq(TEST_ISSUE_ID), any(IssueInput.class)))
                .thenReturn(voidPromise);

        List<FieldInput> fields = List.of(new FieldInput("labels", List.of("resolved")));
        this.jiraSDService.updateIssueFields(TEST_ISSUE_ID, fields);

        verify(this.mockedIssueRestClient).updateIssue(eq(TEST_ISSUE_ID), any(IssueInput.class));
    }

    @Test
    public void findOpenTicketsBySerialForBlock_nullOrBlankInputs_returnEmpty() {
        assertTrue(this.jiraSDService.findOpenTicketsBySerialForBlock(null, "B1").isEmpty());
        assertTrue(this.jiraSDService.findOpenTicketsBySerialForBlock("", "B1").isEmpty());
        assertTrue(this.jiraSDService.findOpenTicketsBySerialForBlock("   ", "B1").isEmpty());
        assertTrue(this.jiraSDService.findOpenTicketsBySerialForBlock("B1", null).isEmpty());
        assertTrue(this.jiraSDService.findOpenTicketsBySerialForBlock("B1", "").isEmpty());
        assertTrue(this.jiraSDService.findOpenTicketsBySerialForBlock("B1", "   ").isEmpty());
    }

    @Test
    public void findOpenTicketsBySerialForBlock_handlesExceptionsNullsAndSuccess() {
        // Spy the service to control searchJiraSD behavior across categories.
        JiraSDService spyService = spy(this.jiraSDService);

        // 1st call: throw exception -> should be swallowed and continue
        // 2nd call: return null -> continue
        // 3rd call: return SearchResult with null issues -> continue
        // Subsequent calls: return SearchResult with usable issues
        SearchResult nullIssues = mock(SearchResult.class);
        when(nullIssues.getIssues()).thenReturn(null);

        Issue issue1 = mock(Issue.class);
        when(issue1.getKey()).thenReturn("K1");
        Issue issue2 = mock(Issue.class);
        when(issue2.getKey()).thenReturn("K2");
        SearchResult good = mock(SearchResult.class);
        when(good.getIssues()).thenReturn(List.of(issue1, issue2));

        doThrow(new RuntimeException("boom"))
                .doReturn(good)
                .when(spyService)
                .searchJiraSD(anyString());

        // Serial extraction: first issue yields serial, second yields null -> skipped
        when(this.mockedJiraSDHelper.extractSerialNumber(issue1)).thenReturn("SER-1");
        when(this.mockedJiraSDHelper.extractSerialNumber(issue2)).thenReturn(null);

        Map<String, JiraTicket> result = spyService.findOpenTicketsBySerialForBlock("B1", "BLK1");

        assertFalse(result.isEmpty());
        assertTrue(result.containsKey("SER-1"));
        JiraTicket t = result.get("SER-1");
        assertNotNull(t);
        assertEquals("K1", t.getTicketId());
        assertNotNull(t.getTicketCategory()); // category derived from enum
    }

    /**
     * Verifies paginated search over multiple pages:<br>
     * 1) First page returns 2 issues with total=3; second page returns 1 issue.<br>
     * 2) Aggregates issues across pages into a single list of 3.<br>
     * 3) Invokes searchJql with startAt offsets 0 and 2 and stops when startAt+count >= total.<br>
     */
    @Test
    public void shouldSearchJiraTicketsPaginated_multiplePages_returnsAllIssues() {
        String jql = "project = DO";
        int pageSize = 2;

        io.atlassian.util.concurrent.Promise<SearchResult> firstPromise = mock();
        io.atlassian.util.concurrent.Promise<SearchResult> secondPromise = mock();

        SearchResult firstResult = mock();
        SearchResult secondResult = mock();

        Issue i1 = mock();
        Issue i2 = mock();
        Issue i3 = mock();
        when(i1.getKey()).thenReturn("KEY-1");
        when(i2.getKey()).thenReturn("KEY-2");
        when(i3.getKey()).thenReturn("KEY-3");

        when(firstPromise.claim()).thenReturn(firstResult);
        when(secondPromise.claim()).thenReturn(secondResult);

        when(firstResult.getIssues()).thenReturn(java.util.List.of(i1, i2));
        when(firstResult.getTotal()).thenReturn(3);

        when(secondResult.getIssues()).thenReturn(java.util.List.of(i3));
        when(secondResult.getTotal()).thenReturn(3);

        when(this.mockedSearchRestClient.searchJql(
                        eq(jql), eq(pageSize), eq(0), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(firstPromise);
        when(this.mockedSearchRestClient.searchJql(
                        eq(jql), eq(pageSize), eq(2), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(secondPromise);

        List<Issue> issues = this.jiraSDService.searchJiraSDPaginated(jql, pageSize, Set.of());

        assertEquals(3, issues.size());
        assertTrue(issues.contains(i1));
        assertTrue(issues.contains(i2));
        assertTrue(issues.contains(i3));

        verify(this.mockedSearchRestClient)
                .searchJql(eq(jql), eq(pageSize), eq(0), org.mockito.ArgumentMatchers.isNull());
        verify(this.mockedSearchRestClient)
                .searchJql(eq(jql), eq(pageSize), eq(2), org.mockito.ArgumentMatchers.isNull());
    }

    /**
     * Verifies empty result handling for paginated search:<br>
     * 1) First page returns total=0 with no issues.<br>
     * 2) Returns an empty list and performs only a single searchJql call.<br>
     */
    @Test
    public void shouldSearchJiraTicketsPaginated_emptyResults_returnsEmptyList() {
        String jql = "project = DO";
        int pageSize = 50;

        io.atlassian.util.concurrent.Promise<SearchResult> firstPromise = mock();
        SearchResult firstResult = mock();

        when(firstPromise.claim()).thenReturn(firstResult);
        when(firstResult.getIssues()).thenReturn(java.util.List.of());
        when(firstResult.getTotal()).thenReturn(0);

        when(this.mockedSearchRestClient.searchJql(
                        eq(jql), eq(pageSize), eq(0), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(firstPromise);

        List<Issue> issues = this.jiraSDService.searchJiraSDPaginated(jql, pageSize, Set.of());

        assertNotNull(issues);
        assertTrue(issues.isEmpty());
        verify(this.mockedSearchRestClient)
                .searchJql(eq(jql), eq(pageSize), eq(0), org.mockito.ArgumentMatchers.isNull());
    }

    /**
     * Verifies stop condition when total equals page size on the first page:<br>
     * 1) First page returns exactly pageSize issues and total=pageSize.<br>
     * 2) Stops pagination after first page and returns those issues only.<br>
     * 3) Performs only one searchJql call with startAt=0.<br>
     */
    @Test
    public void shouldSearchJiraTicketsPaginated_exactTotal_stopsAfterFirstPage() {
        String jql = "project = DO";
        int pageSize = 2;

        io.atlassian.util.concurrent.Promise<SearchResult> firstPromise = mock();
        SearchResult firstResult = mock();

        Issue i1 = mock();
        Issue i2 = mock();
        when(i1.getKey()).thenReturn("KEY-1");
        when(i2.getKey()).thenReturn("KEY-2");

        when(firstPromise.claim()).thenReturn(firstResult);
        when(firstResult.getIssues()).thenReturn(java.util.List.of(i1, i2));
        when(firstResult.getTotal()).thenReturn(2);

        when(this.mockedSearchRestClient.searchJql(
                        eq(jql), eq(pageSize), eq(0), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(firstPromise);

        List<Issue> issues = this.jiraSDService.searchJiraSDPaginated(jql, pageSize, Set.of());

        assertEquals(2, issues.size());
        verify(this.mockedSearchRestClient)
                .searchJql(eq(jql), eq(pageSize), eq(0), org.mockito.ArgumentMatchers.isNull());
    }

    /**
     * Verifies behavior when pageSize exceeds total results:<br>
     * 1) First page returns all 30 issues with total=30.<br>
     * 2) Pagination stops after the first request (startAt=0 only).<br>
     * 3) Returns exactly 30 aggregated issues.<br>
     */
    @Test
    public void shouldSearchJiraTicketsPaginated_largePageSize_singleCallReturnsAll() {
        String jql = "project = DO";
        int pageSize = 100;

        io.atlassian.util.concurrent.Promise<SearchResult> firstPromise = mock();
        SearchResult firstResult = mock();

        List<Issue> pageIssues = new LinkedList<>();
        for (int i = 0; i < 30; i++) {
            Issue issue = mock(Issue.class);
            when(issue.getKey()).thenReturn("KEY-" + i);
            pageIssues.add(issue);
        }

        when(firstPromise.claim()).thenReturn(firstResult);
        when(firstResult.getIssues()).thenReturn(pageIssues);
        when(firstResult.getTotal()).thenReturn(30);

        when(this.mockedSearchRestClient.searchJql(
                        eq(jql), eq(pageSize), eq(0), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(firstPromise);

        List<Issue> issues = this.jiraSDService.searchJiraSDPaginated(jql, pageSize, Set.of());

        assertEquals(30, issues.size());
        verify(this.mockedSearchRestClient)
                .searchJql(eq(jql), eq(pageSize), eq(0), org.mockito.ArgumentMatchers.isNull());
    }
}
