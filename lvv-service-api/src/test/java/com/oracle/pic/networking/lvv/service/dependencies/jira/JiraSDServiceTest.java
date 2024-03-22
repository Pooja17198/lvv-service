package com.oracle.pic.networking.lvv.service.dependencies.jira;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.SearchRestClient;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import io.atlassian.util.concurrent.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class JiraSDServiceTest {

    @Mock private JiraRestClient mockedJiraRestClient;

    @Mock private SearchRestClient mockedSearchRestClient;

    private JiraSDService jiraSDService;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.initMocks(this);
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
}
