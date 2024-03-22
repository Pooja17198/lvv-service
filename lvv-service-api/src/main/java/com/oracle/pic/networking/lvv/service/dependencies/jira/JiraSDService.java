package com.oracle.pic.networking.lvv.service.dependencies.jira;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.SearchRestClient;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.google.inject.Inject;

public class JiraSDService {

    private final JiraRestClient jiraRestClient;

    private final SearchRestClient searchRestClient;

    @Inject
    public JiraSDService(JiraRestClient jiraRestClient) {
        this.jiraRestClient = jiraRestClient;
        this.searchRestClient = jiraRestClient.getSearchClient();
    }

    public SearchResult searchJiraSD(String jql) {
        SearchResult searchResult = this.searchRestClient.searchJql(jql).claim();
        return searchResult;
    }
}
