package com.oracle.pic.networking.lvv.service.dependencies.jira;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.SearchRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.api.domain.Transition;
import com.atlassian.jira.rest.client.api.domain.input.FieldInput;
import com.atlassian.jira.rest.client.api.domain.input.TransitionInput;
import io.atlassian.util.concurrent.Promise;
import java.util.LinkedList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class JiraSDServiceTest {

    @Mock private JiraRestClient mockedJiraRestClient;
    @Mock private IssueRestClient mockedIssueRestClient;
    @Mock private SearchRestClient mockedSearchRestClient;

    private JiraSDService jiraSDService;

    private static final String TEST_ISSUE_ID = "DO-1191815";
    private static final String TEST_RESOLUTION = "Fixed";
    private static final String TEST_COMMENT =
            "Vendor has resolved the issue through Low-voltage Vendor Portal";

    @BeforeEach
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(this.mockedJiraRestClient.getIssueClient()).thenReturn(this.mockedIssueRestClient);
        when(this.mockedJiraRestClient.getSearchClient()).thenReturn(this.mockedSearchRestClient);
        this.jiraSDService = new JiraSDService(this.mockedJiraRestClient);
    }

    @Test
    public void shouldSearchJiraTickets() {
        String jql =
                String.format(
                        "project = \"DO\" AND summary ~ FinalRackValidation AND status = Open AND Building = %s AND Block ~ %s",
                        "PHX1", "21");
        Promise<SearchResult> searchResultPromiseMock = mock();
        SearchResult searchResult = mock();
        when(searchResultPromiseMock.claim()).thenReturn(searchResult);
        when(this.mockedSearchRestClient.searchJql(jql)).thenReturn(searchResultPromiseMock);
        this.jiraSDService.searchJiraSD(jql);
        verify(this.mockedSearchRestClient).searchJql(eq(jql));
    }

    @Test
    public void shouldResolveJiraTicket() {
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

        this.jiraSDService.resolveTicket(TEST_ISSUE_ID, TEST_COMMENT, fieldInputList);

        verify(this.mockedIssueRestClient).getIssue(TEST_ISSUE_ID);
        verify(this.mockedIssueRestClient).getTransitions(issueMock);
        verify(this.mockedIssueRestClient).transition(eq(issueMock), any(TransitionInput.class));
    }
}
