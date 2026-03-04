package com.oracle.pic.networking.lvv.service.dependencies.jira;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.SearchRestClient;
import com.atlassian.jira.rest.client.api.domain.Comment;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.api.domain.Transition;
import com.atlassian.jira.rest.client.api.domain.input.FieldInput;
import com.atlassian.jira.rest.client.api.domain.input.IssueInput;
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder;
import com.atlassian.jira.rest.client.api.domain.input.TransitionInput;
import com.google.inject.Inject;
import com.oracle.pic.commons.metrics.MetricsScope;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JiraSDService {

    private final JiraSDHelper jiraSDHelper;
    private final IssueRestClient issueRestClient;
    private final SearchRestClient searchRestClient;

    @Inject
    public JiraSDService(JiraRestClient jiraRestClient, JiraSDHelper jiraSDHelper) {
        this.issueRestClient = jiraRestClient.getIssueClient();
        this.searchRestClient = jiraRestClient.getSearchClient();
        this.jiraSDHelper = jiraSDHelper;
    }

    public SearchResult searchJiraSD(String jql) {
        try (MetricsScope scope = MetricsScope.create("searchJiraSD")) {
            scope.emit("volume", 1.0);
            SearchResult searchResult = this.searchRestClient.searchJql(jql).claim();
            scope.recordSuccess();
            return searchResult;
        }
    }

    public void transitionTicket(
            String issueId,
            String transitionState,
            String comment,
            List<FieldInput> fieldInputList) {
        Issue issue = this.getIssue(issueId);
        Iterable<Transition> transitions = this.getTransitions(issue);
        int transitionId = -1;

        log.info("Trying to fetch {} transition", transitionState);
        for (Transition transition : transitions) {
            String transitionName = transition.getName();
            log.info("Transition name: {}", transitionName);
            if (transitionName.contains(transitionState)) {
                transitionId = transition.getId();
                break;
            }
        }

        log.info("Fetched {} transition. ID: {}", transitionState, transitionId);

        TransitionInput transitionInput;

        if (comment == null || fieldInputList == null) {
            transitionInput = new TransitionInput(transitionId);
        } else {
            transitionInput =
                    new TransitionInput(transitionId, fieldInputList, Comment.valueOf(comment));
        }

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

    /**
     * Updates fields (like labels) on a Jira issue after transition.
     *
     * @param issueKey Key of the Jira issue to update.
     * @param fieldInputList List of FieldInput to update.
     */
    public void updateIssueFields(String issueKey, List<FieldInput> fieldInputList) {
        try (MetricsScope scope = MetricsScope.create("JiraUpdateIssueFields")) {
            scope.emit("volume", 1.0);
            IssueInputBuilder inputBuilder = new IssueInputBuilder();
            for (FieldInput fieldInput : fieldInputList) {
                inputBuilder.setFieldInput(fieldInput);
            }
            IssueInput issueInput = inputBuilder.build();
            this.issueRestClient.updateIssue(issueKey, issueInput).claim();
            scope.recordSuccess();
        }
    }

    public Map<String, JiraTicket> findOpenTicketsBySerialForBlock(String building, String block) {

        if (building == null || building.isBlank() || block == null || block.isBlank()) {
            return Collections.emptyMap();
        }

        Map<String, JiraTicket> openTicketBySerial = new HashMap<>();

        for (JiraTicketCategory category : JiraTicketCategory.values()) {
            String jql = String.format(JiraQueries.JQL + category.getJqlSuffix(), building, block);
            SearchResult result;
            try {
                result = searchJiraSD(jql);
            } catch (RuntimeException e) {
                log.warn("Runtime Exception while running jql {} {}", jql, e.getMessage());
                continue;
            }

            if (result == null || result.getIssues() == null) {
                log.info("No tickets found for building {} block {}", building, block);
                continue;
            }

            for (Issue issue : result.getIssues()) {

                log.info(
                        "Found ticket {} for building {} block {}",
                        issue.getKey(),
                        building,
                        block);

                String serial = jiraSDHelper.extractSerialNumber(issue);
                if (serial == null) {
                    log.info("No rack serial found for ticket {}", issue.getKey());
                    continue;
                }

                JiraTicket jiraTicket =
                        JiraTicket.builder()
                                .ticketId(issue.getKey())
                                .ticketCategory(category.getTicketType())
                                .build();
                openTicketBySerial.put(serial, jiraTicket);
            }
        }

        return openTicketBySerial;
    }
}
