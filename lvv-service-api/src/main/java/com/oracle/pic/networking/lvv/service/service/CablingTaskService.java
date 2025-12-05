package com.oracle.pic.networking.lvv.service.service;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueField;
import com.atlassian.jira.rest.client.api.domain.IssueFieldId;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.api.domain.input.ComplexIssueInputFieldValue;
import com.atlassian.jira.rest.client.api.domain.input.FieldInput;
import com.google.inject.Inject;
import com.oracle.pic.networking.lvv.service.dependencies.jira.JiraQueries;
import com.oracle.pic.networking.lvv.service.dependencies.jira.JiraSDService;
import com.oracle.pic.networking.lvv.service.kiev.BlockDetails;
import com.oracle.pic.networking.lvv.service.kiev.BlockDetailsDao;
import com.oracle.pic.networking.lvv.service.kiev.ProjectItemDao;
import com.oracle.pic.networking.lvv.service.model.CablingTaskCollection;
import com.oracle.pic.networking.lvv.service.model.InitialCablingTaskDetails;
import com.oracle.pic.networking.lvv.service.model.ValidationFailureTaskDetails;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ToString
public class CablingTaskService {
    private JiraSDService jiraSDService;
    private ProjectItemDao projectItemDao;
    private BlockDetailsDao blockDetailsDao;

    @Inject
    public CablingTaskService(JiraSDService jiraSDService, BlockDetailsDao blockDetailsDao) {
        this.jiraSDService = jiraSDService;
        this.blockDetailsDao = blockDetailsDao;
    }

    public CablingTaskCollection getCablingTasksForProject(String projectId) {
        try {

            List<BlockDetails> blockDetails = blockDetailsDao.getBlockDetailsForProject(projectId);

            List<InitialCablingTaskDetails> initialCablingTaskDetails = new ArrayList<>();
            List<ValidationFailureTaskDetails> validationFailureTaskDetails = new ArrayList<>();

            for (BlockDetails blockItem : blockDetails) {
                CablingTaskCollection cablingTasksForBlock =
                        getCablingTasks(
                                blockItem.getBlock().getBuilding(),
                                blockItem.getBlock().getBlockNumber(),
                                null);
                initialCablingTaskDetails.addAll(cablingTasksForBlock.getInitialCablingTasks());
                validationFailureTaskDetails.addAll(
                        cablingTasksForBlock.getValidationFailureTasks());
            }

            return CablingTaskCollection.builder()
                    .initialCablingTasks(initialCablingTaskDetails)
                    .validationFailureTasks(validationFailureTaskDetails)
                    .build();

        } catch (Exception exception) {
            log.info("Unable to fetch project {}", projectId);
            return CablingTaskCollection.builder().build();
        }
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
                getValidationFailureTaskDetails(
                        cableValidationTickets, building, block, rackSerialNumber);

        SearchResult cableGpuValidationTickets =
                this.searchGpuCableValidationTickets(building, block, rackSerialNumber);
        validationFailureTaskDetailsLinkedList.addAll(
                getValidationFailureTaskDetails(
                        cableGpuValidationTickets, building, block, rackSerialNumber));
        // Search the tickets with initial cabling task
        List<InitialCablingTaskDetails> initialCablingTaskDetailsList = new LinkedList<>();
        SearchResult initialCablingTickets =
                this.searchInitialCablingTickets(building, block, rackSerialNumber);
        for (Issue issue : initialCablingTickets.getIssues()) {
            String rackLocation = null;
            rackSerialNumber = null;
            for (IssueField issueField : issue.getFields()) {
                if (issueField.getName().equals("Rack Location")) {
                    rackLocation = issueField.getValue().toString();
                    break;
                }
            }
            for (IssueField issueField : issue.getFields()) {
                if (issueField.getName().equals("Serial Number")) {
                    rackSerialNumber = issueField.getValue().toString();
                    break;
                }
            }
            InitialCablingTaskDetails initialCablingTaskDetails =
                    new InitialCablingTaskDetails(
                            issue.getKey(), building, block, rackLocation, rackSerialNumber);
            initialCablingTaskDetailsList.add(initialCablingTaskDetails);
        }

        CablingTaskCollection cablingTaskCollection =
                new CablingTaskCollection(
                        initialCablingTaskDetailsList, validationFailureTaskDetailsLinkedList);
        return cablingTaskCollection;
    }

    public CablingTaskCollection getClosedCablingTasks(
            String building, String block, String rackSerialNumber) {
        // Search the tickets with cable validation task
        log.info(
                "Get closed  cabling tasks building {} block {} rackSerialNumber {}",
                building,
                block,
                rackSerialNumber);

        SearchResult cableValidationTickets =
                this.searchClosedCableValidationTickets(building, block, rackSerialNumber);
        SearchResult initialCablingTickets =
                this.searchClosedInitialCablingTickets(building, block, rackSerialNumber);

        List<ValidationFailureTaskDetails> validationFailureTaskDetailsLinkedList =
                new LinkedList<>();
        for (Issue issue : cableValidationTickets.getIssues()) {
            String rackLocation = null;
            rackSerialNumber = null;
            for (IssueField issueField : issue.getFields()) {
                if (issueField.getName().equals("Rack Location")) {
                    rackLocation = issueField.getValue().toString();
                } else if (issueField.getName().equals("Serial Number")) {
                    rackSerialNumber = issueField.getValue().toString();
                }
                if (rackLocation != null && rackSerialNumber != null) {
                    break;
                }
            }
            ValidationFailureTaskDetails validationFailureTaskDetails =
                    new ValidationFailureTaskDetails(
                            issue.getKey(),
                            building,
                            block,
                            rackLocation,
                            rackSerialNumber,
                            issue.getDescription());
            validationFailureTaskDetailsLinkedList.add(validationFailureTaskDetails);
        }

        List<InitialCablingTaskDetails> initialCablingTaskDetailsList = new LinkedList<>();
        for (Issue issue : initialCablingTickets.getIssues()) {
            String rackLocation = null;
            rackSerialNumber = null;
            for (IssueField issueField : issue.getFields()) {
                if (issueField.getName().equals("Rack Location")) {
                    rackLocation = issueField.getValue().toString();
                } else if (issueField.getName().equals("Serial Number")) {
                    rackSerialNumber = issueField.getValue().toString();
                }
                if (rackLocation != null && rackSerialNumber != null) {
                    break;
                }
            }
            InitialCablingTaskDetails initialCablingTaskDetails =
                    new InitialCablingTaskDetails(
                            issue.getKey(), building, block, rackLocation, rackSerialNumber);
            initialCablingTaskDetailsList.add(initialCablingTaskDetails);
        }

        CablingTaskCollection cablingTaskCollection =
                new CablingTaskCollection(
                        initialCablingTaskDetailsList, validationFailureTaskDetailsLinkedList);
        return cablingTaskCollection;
    }

    private List<ValidationFailureTaskDetails> getValidationFailureTaskDetails(
            SearchResult cableValidationTickets,
            String building,
            String block,
            String rackSerialNumber) {
        List<ValidationFailureTaskDetails> validationFailureTaskDetailsLinkedList =
                new LinkedList<>();
        for (Issue issue : cableValidationTickets.getIssues()) {
            // Populate rack serial number for search requests without rack serial number
            String ticketRackSerialNumber = null;
            String rackLocation = null;
            for (IssueField issueField : issue.getFields()) {
                if (issueField.getName().equals("Rack Location")) {
                    rackLocation = issueField.getValue().toString();
                    break;
                }
            }
            for (IssueField issueField : issue.getFields()) {
                if (issueField.getName().equals("Serial Number")) {
                    ticketRackSerialNumber = issueField.getValue().toString();
                    break;
                }
            }
            if (ticketRackSerialNumber == null) {
                for (IssueField issueField : issue.getFields()) {
                    if (issueField.getName().equals("Asset ID")) {
                        ticketRackSerialNumber = issueField.getValue().toString();
                        break;
                    }
                }
            }
            ValidationFailureTaskDetails validationFailureTaskDetails =
                    new ValidationFailureTaskDetails(
                            issue.getKey(),
                            building,
                            block,
                            rackLocation,
                            ticketRackSerialNumber,
                            issue.getDescription());
            validationFailureTaskDetailsLinkedList.add(validationFailureTaskDetails);
        }
        return validationFailureTaskDetailsLinkedList;
    }

    public void resolveValidationFailureTask(String cablingTaskId) {
        String resolution = "Fixed";
        String comment = "Vendor has resolved the issue through Low-voltage Vendor Portal";
        String labelString = "lvv-portal-resolved";

        log.info("Fetching issue for cablingTaskId: {}", cablingTaskId);

        // Required fields during resolve tickets
        Issue issue = this.jiraSDService.getIssue(cablingTaskId);

        String taskStatus = issue.getStatus().getName();
        log.info("Issue status: {}", taskStatus);

        List<FieldInput> fieldInputList = new LinkedList<>();

        FieldInput resolutionFieldInput =
                new FieldInput(
                        IssueFieldId.RESOLUTION_FIELD,
                        ComplexIssueInputFieldValue.with("name", resolution));
        fieldInputList.add(resolutionFieldInput);

        String rmaFieldId = null;
        String rootCauseCategorizationId = null;
        String serviceTypeId = null;

        log.info("Fetching IssueFields");
        for (IssueField issueField : issue.getFields()) {
            switch (issueField.getName()) {
                case "RMA":
                    log.info("RMA field");
                    rmaFieldId = issueField.getId();
                    break;
                case "Root Cause Categorization":
                    log.info("Root Cause Categorization field");
                    rootCauseCategorizationId = issueField.getId();
                    break;
                case "Service Type":
                    log.info("Service Type");
                    serviceTypeId = issueField.getId();
                    break;
                default:
                    break;
            }
        }

        // Add fields only if their IDs were found, else log a warning
        if (rmaFieldId != null) {
            FieldInput rmaFieldInput =
                    new FieldInput(rmaFieldId, ComplexIssueInputFieldValue.with("value", "No"));
            fieldInputList.add(rmaFieldInput);
        } else {
            log.warn(
                    "Jira issue {} missing field 'RMA', will not set this field during transition",
                    cablingTaskId);
        }

        if (rootCauseCategorizationId != null) {
            FieldInput rootCauseCategorizationFieldInput =
                    new FieldInput(
                            rootCauseCategorizationId,
                            ComplexIssueInputFieldValue.with("value", "Vendor"));
            fieldInputList.add(rootCauseCategorizationFieldInput);
        } else {
            log.warn(
                    "Jira issue {} missing field 'Root Cause Categorization', will not set this field during transition",
                    cablingTaskId);
        }

        if (serviceTypeId != null) {
            FieldInput serviceTypeFieldInput = new FieldInput(serviceTypeId, "Rack Install");
            fieldInputList.add(serviceTypeFieldInput);
        } else {
            log.warn(
                    "Jira issue {} missing field 'Service Type', will not set this field during transition",
                    cablingTaskId);
        }

        if (!Objects.equals(taskStatus, "In Progress")) {
            log.info("Ticket not In Progress. Putting it to In Progress");
            this.jiraSDService.transitionTicket(cablingTaskId, "Start Progress", null, null);
        }

        log.info("Resolving ticket");

        this.jiraSDService.transitionTicket(cablingTaskId, "Resolve", comment, fieldInputList);

        // After resolution, add label in a separate update (requires updateIssueFields in
        // JiraSDService)
        FieldInput labelsFieldInput =
                new FieldInput("labels", new ArrayList<>(List.of(labelString)));
        List<FieldInput> labelFieldInputs = new ArrayList<>(List.of(labelsFieldInput));
        this.jiraSDService.updateIssueFields(cablingTaskId, labelFieldInputs);
    }

    private SearchResult searchCableValidationTickets(
            String building, String block, String rackSerialNumber) {
        String cableValidationJql =
                String.format(JiraQueries.JQL + JiraQueries.FINAL_VALIDATION, building, block);
        if (rackSerialNumber != null) {
            cableValidationJql += String.format(JiraQueries.SERIAL_NUMBER, rackSerialNumber);
        }
        return this.jiraSDService.searchJiraSD(cableValidationJql);
    }

    private SearchResult searchGpuCableValidationTickets(
            String building, String block, String rackSerialNumber) {
        String cableValidationJql =
                String.format(JiraQueries.JQL + JiraQueries.GPU_VALIDATION, building, block);
        if (rackSerialNumber != null) {
            cableValidationJql += String.format(JiraQueries.SERIAL_NUMBER, rackSerialNumber);
        }
        return this.jiraSDService.searchJiraSD(cableValidationJql);
    }

    private SearchResult searchInitialCablingTickets(
            String building, String block, String rackSerialNumber) {
        String initialCablingJql =
                String.format(JiraQueries.JQL + JiraQueries.RACK_DEPLOYMENT, building, block);
        if (rackSerialNumber != null) {
            initialCablingJql += String.format(JiraQueries.SERIAL_NUMBER, rackSerialNumber);
        }
        return this.jiraSDService.searchJiraSD(initialCablingJql);
    }

    private SearchResult searchClosedCableValidationTickets(
            String building, String block, String rackSerialNumber) {
        String cableValidationJql =
                String.format(
                        JiraQueries.JQL_CLOSED + JiraQueries.FINAL_VALIDATION, building, block);
        if (rackSerialNumber != null) {
            cableValidationJql += String.format(JiraQueries.SERIAL_NUMBER, rackSerialNumber);
        }
        return this.jiraSDService.searchJiraSD(cableValidationJql);
    }

    private SearchResult searchClosedInitialCablingTickets(
            String building, String block, String rackSerialNumber) {
        String initialCablingJql =
                String.format(
                        JiraQueries.JQL_CLOSED + JiraQueries.RACK_DEPLOYMENT, building, block);
        if (rackSerialNumber != null) {
            initialCablingJql += String.format(JiraQueries.SERIAL_NUMBER, rackSerialNumber);
        }
        return this.jiraSDService.searchJiraSD(initialCablingJql);
    }
}
