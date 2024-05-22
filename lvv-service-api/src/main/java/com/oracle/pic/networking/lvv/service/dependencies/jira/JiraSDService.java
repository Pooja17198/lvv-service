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
import com.oracle.pic.commons.metrics.MetricsScope;
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
        try (MetricsScope scope = MetricsScope.create("searchJiraSD")) {
            scope.emit("volume", 1.0);
            SearchResult searchResult = this.searchRestClient.searchJql(jql).claim();
            scope.recordSuccess();
            return searchResult;
        }
    }

    public void resolveTicket(String issueId, String comment, List<FieldInput> fieldInputList) {
        Issue issue = this.getIssue(issueId);
        Iterable<Transition> transitions = this.getTransitions(issue);
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
        this.transition(issue, transitionInput);
    }

    public Issue getIssue(String issueKey) {
        try (MetricsScope scope = MetricsScope.create("JiraGetIssue")) {
            scope.emit("volume", 1.0);
            Issue issue = this.issueRestClient.getIssue(issueKey).claim();
            scope.recordSuccess();
            return issue;
        }
    }

    private Iterable<Transition> getTransitions(Issue issue) {
        try (MetricsScope scope = MetricsScope.create("JiraGetTransitions")) {
            scope.emit("volume", 1.0);
            Iterable<Transition> transitions = this.issueRestClient.getTransitions(issue).claim();
            scope.recordSuccess();
            return transitions;
        }
    }

    private void transition(Issue issue, TransitionInput transitionInput) {
        try (MetricsScope scope = MetricsScope.create("JiraTransition")) {
            scope.emit("volume", 1.0);
            this.issueRestClient.transition(issue, transitionInput).claim();
            scope.recordSuccess();
        }
    }
}
