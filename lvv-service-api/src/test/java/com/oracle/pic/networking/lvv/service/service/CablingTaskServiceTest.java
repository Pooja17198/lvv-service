package com.oracle.pic.networking.lvv.service.service;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.oracle.pic.networking.lvv.service.dependencies.jira.JiraSDService;
import com.oracle.pic.networking.lvv.service.model.CablingTaskCollection;
import java.util.LinkedList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CablingTaskServiceTest {

    @Mock private JiraSDService mockedJiraSDService;

    private String building = "PHX1";
    private String block = "15";
    private String rackSerialNumber = "1S7D9XCTO1WWJ102GBN7";
    private String cableValidationTicketDescription = "Ticket Description";

    private CablingTaskService cablingTaskService;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.initMocks(this);
        this.cablingTaskService = new CablingTaskService(this.mockedJiraSDService);
    }

    @Test
    public void shouldGetCableValidationTasks() {
        // Setup cable validation search results
        String cableValidationJql =
                "project = \"DO\" AND summary ~ FinalRackValidation AND status = Open AND Building = PHX1 AND Block ~ 15 AND \"Serial Number\" ~ 1S7D9XCTO1WWJ102GBN7";
        List<Issue> cableValidationTickets = new LinkedList<>();
        Issue cableValidationTicket = mock();
        when(cableValidationTicket.getDescription())
                .thenReturn(this.cableValidationTicketDescription);
        cableValidationTickets.add(cableValidationTicket);
        SearchResult cableValidationSearchResult =
                new SearchResult(0, 1, 1, cableValidationTickets);
        when(this.mockedJiraSDService.searchJiraSD(eq(cableValidationJql)))
                .thenReturn(cableValidationSearchResult);

        // Setup initial cabling search results
        String initialCablingJql =
                "project = \"DO\" AND summary ~ \"Rack Deployment\" AND status = Open AND Building = PHX1 AND Block ~ 15 AND \"Serial Number\" ~ 1S7D9XCTO1WWJ102GBN7";
        List<Issue> initialCablingTickets = new LinkedList<>();
        SearchResult initialCablingSearchResult = new SearchResult(0, 0, 0, initialCablingTickets);
        when(this.mockedJiraSDService.searchJiraSD(initialCablingJql))
                .thenReturn(initialCablingSearchResult);

        CablingTaskCollection cablingTaskCollection =
                this.cablingTaskService.getCablingTasks(
                        this.building, this.block, this.rackSerialNumber);

        verify(this.mockedJiraSDService, times(1)).searchJiraSD(cableValidationJql);
        verify(this.mockedJiraSDService, times(1)).searchJiraSD(initialCablingJql);
        assertEquals(0, cablingTaskCollection.getInitialCablingTasks().size());
        assertEquals(
                this.cableValidationTicketDescription,
                cablingTaskCollection.getValidationFailureTasks().get(0).getFailureReason());
    }
}
