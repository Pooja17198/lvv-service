package com.oracle.pic.networking.lvv.service.service;

import com.google.inject.Inject;
import com.oracle.pic.networking.lvv.service.dependencies.jira.JiraSDService;
import com.oracle.pic.networking.lvv.service.model.CablingTaskCollection;
import com.oracle.pic.networking.lvv.service.model.InitialCablingTaskDetails;
import com.oracle.pic.networking.lvv.service.model.ValidationFailureTaskDetails;
import java.util.LinkedList;
import java.util.List;

public class CablingTaskService {
    private JiraSDService jiraSDService;

    @Inject
    public CablingTaskService(JiraSDService jiraSDService) {
        this.jiraSDService = jiraSDService;
    }

    public CablingTaskCollection getCablingTasks(
            String building, String block, String rackSerialNumber) {
        this.jiraSDService.searchJiraSD();
        List<InitialCablingTaskDetails> initialCablingTaskDetailsList = new LinkedList<>();
        List<ValidationFailureTaskDetails> validationFailureTaskDetailsLinkedList =
                new LinkedList<>();
        CablingTaskCollection cablingTaskCollection =
                new CablingTaskCollection(
                        initialCablingTaskDetailsList, validationFailureTaskDetailsLinkedList);
        return cablingTaskCollection;
    }
}
