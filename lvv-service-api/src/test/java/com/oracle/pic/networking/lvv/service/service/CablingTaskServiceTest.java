package com.oracle.pic.networking.lvv.service.service;

import com.oracle.pic.networking.lvv.service.dependencies.jira.JiraSDService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CablingTaskServiceTest {

    @Mock private JiraSDService jiraSDService;

    private String building = "PHX1";
    private String block = "15";
    private String rackSerialNumber = "1S7D9XCTO1WWJ102GBN7";

    private CablingTaskService cablingTaskService;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.initMocks(this);
        this.cablingTaskService = new CablingTaskService(this.jiraSDService);
    }

    @Test
    public void getCablingTasks() {
        this.cablingTaskService.getCablingTasks(this.building, this.block, this.rackSerialNumber);
    }
}
