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
// import com.oracle.pic.networking.lvv.service.dependencies.ncp.NcpService;
import com.oracle.pic.networking.lvv.service.kiev.BlockDetails;
import com.oracle.pic.networking.lvv.service.kiev.BlockDetailsDao;
import com.oracle.pic.networking.lvv.service.model.CableValidationFailureTasks;
import com.oracle.pic.networking.lvv.service.model.CablingTaskCollection;
import com.oracle.pic.networking.lvv.service.model.GpuLldpFailure;
import com.oracle.pic.networking.lvv.service.model.InitialCablingTaskDetails;
import com.oracle.pic.networking.lvv.service.model.InvalidTransceiverFailure;
import com.oracle.pic.networking.lvv.service.model.LldpFailure;
import com.oracle.pic.networking.lvv.service.model.OpticsFailure;
import com.oracle.pic.networking.lvv.service.model.ValidationFailureTaskDetails;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ToString
public class CablingTaskService {
    private JiraSDService jiraSDService;
    // private NcpService ncpService;
    private BlockDetailsDao blockDetailsDao;

    @Inject
    public CablingTaskService(
            JiraSDService jiraSDService,
            // @Named("NcpServiceClient") NcpService ncpService,
            BlockDetailsDao blockDetailsDao) {
        this.jiraSDService = jiraSDService;
        // this.ncpService = ncpService;
        this.blockDetailsDao = blockDetailsDao;
    }

    public String getResultFromIssueField(Issue issue) {
        //        String descRegex = "(\\w+-\\w+-\\w+-\\w+-\\w+)";
        //
        //        Pattern pattern = Pattern.compile(descRegex);
        //        Set<String> labels = issue.getLabels();
        //        Optional<String> ncpField =
        //                labels.stream()
        //                        .filter(label -> label.startsWith("NCP_JOB_ID:"))
        //                        .map(
        //                                label -> {
        //                                    Matcher matcher = pattern.matcher(label);
        //                                    if (matcher.find()) {
        //                                        return matcher.group(1);
        //                                    }
        //                                    return null;
        //                                })
        //                        .filter(Objects::nonNull)
        //                        .findFirst();
        //        if (ncpField.isPresent()) {
        //            return getResultFromNcpJob(ncpField.get(), issue.getDescription());
        //        }
        return issue.getDescription();
    }

    //    public String getResultFromNcpJob(String jobId, String fallbackResult) {
    //        try {
    //            Job ncpJobResult = ncpService.getNcpJob(jobId);
    //            return ncpJobResult.toString();
    //        } catch (BmcException e) {
    //            log.error(
    //                    "Exception occurred while processing ncp job id {}: error {}",
    //                    jobId,
    //                    e.toString());
    //            return fallbackResult;
    //        }
    //    }

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
            switch (issueField.getName()) {
                case "RMA":
                    rmaFieldId = issueField.getId();
                    break;
                case "Root Cause Categorization":
                    rootCauseCategorizationId = issueField.getId();
                    break;
                case "Service Type":
                    serviceTypeId = issueField.getId();
                    break;
                default:
                    break;
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

    public CableValidationFailureTasks getCableValidationFailureTask(String cablingTaskId) {
        Issue issue = this.jiraSDService.getIssue(cablingTaskId);
        // TODO: Get result from NCP
        // return getResultFromIssueField(issue);
        return getResultFromIssueDescription(issue);
    }

    private CableValidationFailureTasks getResultFromIssueDescription(Issue issue) {
        List<LldpFailure> lldpFailureList = new LinkedList<>();
        List<OpticsFailure> opticsFailureList = new LinkedList<>();
        List<InvalidTransceiverFailure> invalidTransceiverFailureList = new LinkedList<>();
        List<GpuLldpFailure> gpuLldpFailureList = new LinkedList<>();
        String devicesUnreachable = "";
        String description = issue.getDescription();
        CableValidationFailureTasks cableValidationFailureTasks = null;
        assert description != null;
        if (description.contains("Validation Status: Failure")) {
            Scanner scanner = new Scanner(new StringReader(description));
            while (scanner.hasNext()) {
                String line = scanner.nextLine();
                if (line.contains("LLDP Failures")) {
                    lldpFailureList.addAll(this.getLldpFailureList(line));
                }
                if (line.contains("*Failed:* test_optics")) {
                    line = scanner.nextLine();
                    opticsFailureList.addAll(this.getOpticsFailureList(line));
                }
                if (line.contains("*Failed:* test_invalid_transceiver")) {
                    line = scanner.nextLine();
                    invalidTransceiverFailureList.addAll(
                            this.getInvalidTransceiverFailureList(line));
                }
            }
            cableValidationFailureTasks =
                    CableValidationFailureTasks.builder()
                            .lldpFailures(lldpFailureList)
                            .opticsFailures(opticsFailureList)
                            .invalidTransceiverFailures(invalidTransceiverFailureList)
                            .build();
        } else if (description.contains("Validation Status: devicesunreachable")) {
            Scanner scanner = new Scanner(new StringReader(description));
            while (scanner.hasNext()) {
                String line = scanner.nextLine();
                if (line.contains("Validation Status: devicesunreachable")) {
                    devicesUnreachable = scanner.nextLine();
                }
            }
            cableValidationFailureTasks =
                    CableValidationFailureTasks.builder()
                            .deviceUnreachableFailures(devicesUnreachable)
                            .build();
        } else if (description.contains("Compute Product Validation")) {
            Scanner scanner = new Scanner(new StringReader(description));
            while (scanner.hasNext()) {
                String line = scanner.nextLine();
                if (line.contains("needs to be connected to")) {
                    gpuLldpFailureList.addAll(this.getGpuLldpFailureList(line, scanner.nextLine()));
                }
            }
            cableValidationFailureTasks =
                    CableValidationFailureTasks.builder()
                            .gpuLldpFailures(gpuLldpFailureList)
                            .build();
        }

        return cableValidationFailureTasks;
    }

    private List<LldpFailure> getLldpFailureList(String line) {
        List<LldpFailure> lldpFailureList = new LinkedList<>();
        String currentOriginString = "current_origin\": \"";
        int startIndex = line.indexOf(currentOriginString);
        while (startIndex != -1) {
            int endIndex = line.indexOf("\"", startIndex + currentOriginString.length());
            String currentOrigin =
                    line.substring(startIndex + currentOriginString.length(), endIndex);

            String currentDestinationString = "current_destination\": \"";
            startIndex = line.indexOf(currentDestinationString, endIndex + 1);
            endIndex = line.indexOf("\"", startIndex + currentDestinationString.length());
            String currentDestination =
                    line.substring(startIndex + currentDestinationString.length(), endIndex);

            String expectedDestinationString = "expected_destination\": \"";
            startIndex = line.indexOf(expectedDestinationString, endIndex + 1);
            endIndex = line.indexOf("\"", startIndex + expectedDestinationString.length());
            String expectedDestination =
                    line.substring(startIndex + expectedDestinationString.length(), endIndex);

            LldpFailure lldpFailure =
                    new LldpFailure(currentOrigin, currentDestination, expectedDestination);
            lldpFailureList.add(lldpFailure);

            startIndex = line.indexOf(currentOriginString, endIndex + 1);
        }
        return lldpFailureList;
    }

    private List<GpuLldpFailure> getGpuLldpFailureList(String line1, String line2) {
        List<GpuLldpFailure> gpuLldpFailures = new LinkedList<>();
        String connectString1 = "needs to be connected to";
        String connectString2 = "Currently connected to";
        String source = line1.substring(0, line1.indexOf(connectString1) - 1);
        String expectedDestination =
                line1.substring(line1.indexOf(connectString1) + connectString1.length() + 1);
        String currentDestination = line2.substring(connectString2.length() + 1);
        GpuLldpFailure gpuLldpFailure =
                GpuLldpFailure.builder()
                        .currentOrigin(source)
                        .expectedDestination(expectedDestination)
                        .currentDestination(currentDestination)
                        .build();
        gpuLldpFailures.add(gpuLldpFailure);
        return gpuLldpFailures;
    }

    private List<OpticsFailure> getOpticsFailureList(String line) {
        List<OpticsFailure> opticsFailureList = new LinkedList<>();
        String deviceString = "device\": \"";
        int startIndex = line.indexOf(deviceString);
        while (startIndex != -1) {
            int endIndex = line.indexOf("\"", startIndex + deviceString.length());
            String device = line.substring(startIndex + deviceString.length(), endIndex);

            String intfNameString = "intf_name\": \"";
            startIndex = line.indexOf(intfNameString, endIndex + 1);
            endIndex = line.indexOf("\"", startIndex + intfNameString.length());
            String intfName = line.substring(startIndex + intfNameString.length(), endIndex);

            String inputPowerString = "input_power\": \"";
            startIndex = line.indexOf(inputPowerString, endIndex + 1);
            endIndex = line.indexOf("\"", startIndex + inputPowerString.length());
            String inputPower = line.substring(startIndex + inputPowerString.length(), endIndex);

            String outputPowerString = "output_power\": \"";
            startIndex = line.indexOf(outputPowerString, endIndex + 1);
            endIndex = line.indexOf("\"", startIndex + outputPowerString.length());
            String outputPower = line.substring(startIndex + outputPowerString.length(), endIndex);

            String devicePhysString = "device_phys\": \"";
            startIndex = line.indexOf(devicePhysString, endIndex + 1);
            endIndex = line.indexOf("\"", startIndex + devicePhysString.length());
            String devicePhys = line.substring(startIndex + devicePhysString.length(), endIndex);

            OpticsFailure opticsFailure =
                    new OpticsFailure(device, intfName, inputPower, outputPower, devicePhys);
            opticsFailureList.add(opticsFailure);

            startIndex = line.indexOf(deviceString, endIndex + 1);
        }
        return opticsFailureList;
    }

    private List<InvalidTransceiverFailure> getInvalidTransceiverFailureList(String line) {
        List<InvalidTransceiverFailure> invalidTransceiverFailureList = new LinkedList<>();
        InvalidTransceiverFailure invalidTransceiverFailure = new InvalidTransceiverFailure(line);
        invalidTransceiverFailureList.add(invalidTransceiverFailure);
        return invalidTransceiverFailureList;
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
