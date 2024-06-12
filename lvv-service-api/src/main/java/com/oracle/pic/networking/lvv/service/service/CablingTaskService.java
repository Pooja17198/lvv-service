package com.oracle.pic.networking.lvv.service.service;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueField;
import com.atlassian.jira.rest.client.api.domain.IssueFieldId;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.api.domain.input.ComplexIssueInputFieldValue;
import com.atlassian.jira.rest.client.api.domain.input.FieldInput;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.oracle.bmc.model.BmcException;
import com.oracle.pic.networking.lvv.service.dependencies.jira.JiraSDService;
import com.oracle.pic.networking.lvv.service.dependencies.ncp.NcpService;
import com.oracle.pic.networking.lvv.service.model.CablingTaskCollection;
import com.oracle.pic.networking.lvv.service.model.InitialCablingTaskDetails;
import com.oracle.pic.networking.lvv.service.model.ValidationFailureTaskDetails;
import com.oracle.pic.networking.ncp.model.Job;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CablingTaskService {
    private JiraSDService jiraSDService;
    private NcpService ncpService;

    @Inject
    public CablingTaskService(
            JiraSDService jiraSDService, @Named("NcpServiceClient") NcpService ncpService) {
        this.jiraSDService = jiraSDService;
        this.ncpService = ncpService;
    }

    public String getResultFromIssueField(Issue issue) {
        String descRegex = "(\\w+-\\w+-\\w+-\\w+-\\w+)";

        Pattern pattern = Pattern.compile(descRegex);
        Set<String> labels = issue.getLabels();
        Optional<String> ncpField =
                labels.stream()
                        .filter(label -> label.startsWith("NCP_JOB_ID:"))
                        .map(
                                label -> {
                                    Matcher matcher = pattern.matcher(label);
                                    if (matcher.find()) {
                                        return matcher.group(1);
                                    }
                                    return null;
                                })
                        .filter(Objects::nonNull)
                        .findFirst();
        if (ncpField.isPresent()) {
            return getResultFromNcpJob(ncpField.get(), issue.getDescription());
        } else {
            return issue.getDescription();
        }
    }

    public String getResultFromNcpJob(String jobId, String fallbackResult) {
        try {
            Job ncpJobResult = ncpService.getNcpJob(jobId);
            return ncpJobResult.toString();
        } catch (BmcException e) {
            log.error(
                    "Exception occurred while processing ncp job id {}: error {}",
                    jobId,
                    e.toString());
            return fallbackResult;
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
            for (IssueField issueField : issue.getFields()) {
                if (issueField.getName().equals("Rack Location")) {
                    rackLocation = issueField.getValue().toString();
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
            String ticketRackSerialNumber = rackSerialNumber;
            String rackLocation = null;
            for (IssueField issueField : issue.getFields()) {
                if (issueField.getName().equals("Rack Location")) {
                    rackLocation = issueField.getValue().toString();
                    break;
                }
            }
            if (ticketRackSerialNumber == null) {
                for (IssueField issueField : issue.getFields()) {
                    if (issueField.getName().equals("Serial Number")) {
                        ticketRackSerialNumber = issueField.getValue().toString();
                        break;
                    }
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

        // Required fields during resolve tickets
        Issue issue = this.jiraSDService.getIssue(cablingTaskId);
        List<FieldInput> fieldInputList = new LinkedList<>();

        FieldInput resolutionFieldInput =
                new FieldInput(
                        IssueFieldId.RESOLUTION_FIELD,
                        ComplexIssueInputFieldValue.with("name", resolution));
        fieldInputList.add(resolutionFieldInput);

        String rmaFieldId = null;
        String rootCauseCategorizationId = null;
        String serviceTypeId = null;
        for (IssueField issueField : issue.getFields()) {
            if (issueField.getName().equals("RMA")) {
                rmaFieldId = issueField.getId();
            }
            if (issueField.getName().equals("Root Cause Categorization")) {
                rootCauseCategorizationId = issueField.getId();
            }
            if (issueField.getName().equals("Service Type")) {
                serviceTypeId = issueField.getId();
            }
        }

        FieldInput rmaFieldInput =
                new FieldInput(rmaFieldId, ComplexIssueInputFieldValue.with("value", "No"));
        fieldInputList.add(rmaFieldInput);

        FieldInput rootCauseCategorizationFieldInput =
                new FieldInput(
                        rootCauseCategorizationId,
                        ComplexIssueInputFieldValue.with("value", "Vendor"));
        fieldInputList.add(rootCauseCategorizationFieldInput);

        FieldInput serviceTypeFieldInput = new FieldInput(serviceTypeId, "Rack Install");
        fieldInputList.add(serviceTypeFieldInput);

        this.jiraSDService.resolveTicket(cablingTaskId, comment, fieldInputList);
    }

    public String getCableValidationFailureTask(String cablingTaskId) {
        Issue issue = this.jiraSDService.getIssue(cablingTaskId);
        return getResultFromIssueField(issue);
    }

    private SearchResult searchCableValidationTickets(
            String building, String block, String rackSerialNumber) {
        String cableValidationJql =
                String.format(
                        "project = \"DO\" AND summary ~ FinalRackValidation AND status in (\"In Progress\", Open, Pending, Reopened) AND Building = %s AND Block ~ %s",
                        building, block);
        if (rackSerialNumber != null) {
            cableValidationJql =
                    cableValidationJql
                            + String.format(" AND \"Serial Number\" ~ %s", rackSerialNumber);
        }
        return this.jiraSDService.searchJiraSD(cableValidationJql);
    }

    private SearchResult searchGpuCableValidationTickets(
            String building, String block, String rackSerialNumber) {
        String cableValidationJql =
                String.format(
                        "project = \"DO\" AND summary ~ \"NA Cable Validation Failure\" AND status in (Open, \"In Progress\", Reopened, Pending) AND Building = %s AND Block ~ %s",
                        building, block);
        if (rackSerialNumber != null) {
            cableValidationJql =
                    cableValidationJql
                            + String.format(" AND \"Serial Number\" ~ %s", rackSerialNumber);
        }
        return this.jiraSDService.searchJiraSD(cableValidationJql);
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
        return this.jiraSDService.searchJiraSD(initialCablingJql);
    }
}
