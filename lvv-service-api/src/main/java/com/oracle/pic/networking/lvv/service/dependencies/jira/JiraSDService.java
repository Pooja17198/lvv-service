package com.oracle.pic.networking.lvv.service.dependencies.jira;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.SearchRestClient;
import com.atlassian.jira.rest.client.api.domain.Comment;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.api.domain.Transition;
import com.atlassian.jira.rest.client.api.domain.input.FieldInput;
import com.atlassian.jira.rest.client.api.domain.input.TransitionInput;
import com.google.inject.Inject;
import java.util.List;

public class JiraSDService {

    private final JiraRestClient jiraRestClient;
    private final IssueRestClient issueRestClient;
    private final SearchRestClient searchRestClient;

    @Inject
    public JiraSDService(JiraRestClient jiraRestClient) {
        this.jiraRestClient = jiraRestClient;
        this.issueRestClient = jiraRestClient.getIssueClient();
        this.searchRestClient = jiraRestClient.getSearchClient();
    }

    public SearchResult searchJiraSD(String jql) {
        SearchResult searchResult = this.searchRestClient.searchJql(jql).claim();
        return searchResult;
    }

    public void resolveTicket(String issueId, String comment, List<FieldInput> fieldInputList) {
        Issue issue = this.getIssue(issueId);
        Iterable<Transition> transitions = this.issueRestClient.getTransitions(issue).claim();
        int transitionId = -1;
        for (Transition transition : transitions) {
            String transitionName = transition.getName();
            if (transitionName.contains("Resolve")) {
                transitionId = transition.getId();
                break;
            }
        }

        TransitionInput transitionInput =
                new TransitionInput(transitionId, fieldInputList, Comment.valueOf(comment));
        this.issueRestClient.transition(issue, transitionInput).claim();
    }

    public Issue getIssue(String issueKey) {
        return this.issueRestClient.getIssue(issueKey).claim();
    }
}
