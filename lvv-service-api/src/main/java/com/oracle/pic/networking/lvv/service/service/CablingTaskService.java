package com.oracle.pic.networking.lvv.service.service;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.google.inject.Inject;
import com.oracle.pic.networking.lvv.service.dependencies.jira.JiraSDService;
import com.oracle.pic.networking.lvv.service.model.CablingTaskCollection;
import com.oracle.pic.networking.lvv.service.model.InitialCablingTaskDetails;
import com.oracle.pic.networking.lvv.service.model.ValidationFailureTaskDetails;
import java.util.LinkedList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CablingTaskService {
    private JiraSDService jiraSDService;

    @Inject
    public CablingTaskService(JiraSDService jiraSDService) {
        this.jiraSDService = jiraSDService;
    }

    public CablingTaskCollection getCablingTasks(
            String building, String block, String rackSerialNumber) {
        // Search the tickets with cable validation task
        log.info(
                "Get cabling tasks building {} block {} rackSerialNumber {}",
                building,
                block,
                rackSerialNumber);
        SearchResult cableValidationTickets =
                this.searchCableValidationTickets(building, block, rackSerialNumber);
        List<ValidationFailureTaskDetails> validationFailureTaskDetailsLinkedList =
                new LinkedList<>();
        for (Issue issue : cableValidationTickets.getIssues()) {
            ValidationFailureTaskDetails validationFailureTaskDetails =
                    new ValidationFailureTaskDetails(
                            building, block, rackSerialNumber, issue.getDescription());
            validationFailureTaskDetailsLinkedList.add(validationFailureTaskDetails);
        }

        // Search the tickets with initial cabling task
        List<InitialCablingTaskDetails> initialCablingTaskDetailsList = new LinkedList<>();
        SearchResult initialCablingTickets =
                this.searchInitialCablingTickets(building, block, rackSerialNumber);
        for (Issue issue : initialCablingTickets.getIssues()) {
            InitialCablingTaskDetails initialCablingTaskDetails =
                    new InitialCablingTaskDetails(building, block, rackSerialNumber);
            initialCablingTaskDetailsList.add(initialCablingTaskDetails);
        }

        CablingTaskCollection cablingTaskCollection =
                new CablingTaskCollection(
                        initialCablingTaskDetailsList, validationFailureTaskDetailsLinkedList);
        return cablingTaskCollection;
    }

    private SearchResult searchCableValidationTickets(
            String building, String block, String rackSerialNumber) {
        String cableValidationJql =
                String.format(
                        "project = \"DO\" AND summary ~ FinalRackValidation AND status = Open AND Building = %s AND Block ~ %s",
                        building, block);
        if (rackSerialNumber != null) {
            cableValidationJql =
                    cableValidationJql
                            + String.format(" AND \"Serial Number\" ~ %s", rackSerialNumber);
        }
        SearchResult searchResult = this.jiraSDService.searchJiraSD(cableValidationJql);
        return searchResult;
    }

    private SearchResult searchInitialCablingTickets(
            String building, String block, String rackSerialNumber) {
        String initialCablingJql =
                String.format(
                        "project = \"DO\" AND summary ~ \"Rack Deployment\" AND status = Open AND Building = %s AND Block ~ %s",
                        building, block);
        if (rackSerialNumber != null) {
            initialCablingJql =
                    initialCablingJql
                            + String.format(" AND \"Serial Number\" ~ %s", rackSerialNumber);
        }
        SearchResult searchResult = this.jiraSDService.searchJiraSD(initialCablingJql);
        return searchResult;
    }
}
