package com.oracle.pic.networking.lvv.service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueField;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.oracle.pic.commons.exceptions.server.ErrorCode;
import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.networking.lvv.service.dependencies.jira.JiraSDService;
import com.oracle.pic.networking.lvv.service.dependencies.ncp.NcpClientHelper;
import com.oracle.pic.networking.lvv.service.kiev.ValidationFailureResultDao;
import com.oracle.pic.networking.lvv.service.models.ncp.JobType;
import com.oracle.pic.networking.ncp.model.Job;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

class CablingValidationServiceTest {

    @Mock private NcpClientHelper ncpClientHelper;
    @Mock private JiraSDService jiraSDService;
    @Mock private ValidationFailureResultDao validationFailureResultDao;
    @Mock private MetricsScope metricsScope;
    @InjectMocks private CablingValidationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service =
                new CablingValidationService(
                        ncpClientHelper, jiraSDService, validationFailureResultDao);
    }

    // ========= fetchRackLocation (reachable via validateCablingTasks, getValidationJobStatus)
    // ==========

    @Test
    void fetchRackLocation_returnsRackLocation_whenFieldPresent() {
        // Prepare
        String rackSerial = "RSN123";
        IssueField rackLocationField = mock(IssueField.class);
        Job job = mock(Job.class);
        when(rackLocationField.getName()).thenReturn("Rack Location");
        when(rackLocationField.getValue()).thenReturn("LOC-42");
        when(ncpClientHelper.createJob(any(), any(), any(), any(), any())).thenReturn(job);
        when(job.getId()).thenReturn("jobId");

        Issue issue = mock(Issue.class);
        when(issue.getFields()).thenReturn(List.of(rackLocationField));

        SearchResult searchResult = mock(SearchResult.class);
        when(searchResult.getIssues()).thenReturn(List.of(issue));
        when(jiraSDService.searchJiraSD(anyString())).thenReturn(searchResult);

        // Test private via public
        String id =
                service.validateCablingTasks(
                        "us-phx", "bld-a", rackSerial, List.of("dev1"), metricsScope);
        assertNotNull(id);
    }

    @Test
    void fetchRackLocation_returnsNull_whenNoRackLocationField() {
        // Setup without "Rack Location"
        IssueField otherField = mock(IssueField.class);
        when(otherField.getName()).thenReturn("Other Field");
        when(otherField.getValue()).thenReturn("irrelevant");

        Issue issue = mock(Issue.class);
        when(issue.getFields()).thenReturn(List.of(otherField)); // No "Rack Location"
        SearchResult searchResult = mock(SearchResult.class);
        when(searchResult.getIssues()).thenReturn(List.of(issue));
        when(jiraSDService.searchJiraSD(anyString())).thenReturn(searchResult);

        // Should throw exception because rack location is null
        RenderableException ex =
                assertThrows(
                        RenderableException.class,
                        () ->
                                service.validateCablingTasks(
                                        "us-phx", "bld-a", "RSN111", List.of(), metricsScope));
        assertEquals(ErrorCode.IncorrectState, ex.getErrorCode());
    }

    // ========== validateCablingTasks ==========

    @Test
    void validateCablingTasks_populatesPerRackJobType_onEmptyDevices() throws Exception {
        IssueField rackLocationField = mock(IssueField.class);
        when(rackLocationField.getName()).thenReturn("Rack Location");
        when(rackLocationField.getValue()).thenReturn("RACK-1");
        Issue issue = mock(Issue.class);
        when(issue.getFields()).thenReturn(List.of(rackLocationField));
        SearchResult searchResult = mock(SearchResult.class);
        when(searchResult.getIssues()).thenReturn(List.of(issue));
        when(jiraSDService.searchJiraSD(anyString())).thenReturn(searchResult);

        Job job = mock(Job.class);
        when(job.getId()).thenReturn("JOB-42");
        when(ncpClientHelper.createJob(any(), any(), any(), any(), any())).thenReturn(job);

        String jobId = service.validateCablingTasks("reg", "bld", "RSN1", List.of(), metricsScope);
        assertEquals("JOB-42", jobId);
    }

    @Test
    void validateCablingTasks_populatesHealthCheckJobType_whenDevicesPresent() throws Exception {
        IssueField rackLocationField = mock(IssueField.class);
        when(rackLocationField.getName()).thenReturn("Rack Location");
        when(rackLocationField.getValue()).thenReturn("RACKX");
        Issue issue = mock(Issue.class);
        when(issue.getFields()).thenReturn(List.of(rackLocationField));
        SearchResult searchResult = mock(SearchResult.class);
        when(searchResult.getIssues()).thenReturn(List.of(issue));
        when(jiraSDService.searchJiraSD(anyString())).thenReturn(searchResult);

        Job job = mock(Job.class);
        when(job.getId()).thenReturn("JOB-HEX");
        when(ncpClientHelper.createJob(any(), any(), any(), any(), any())).thenReturn(job);

        String id =
                service.validateCablingTasks(
                        "region", "building", "RSN", List.of("devA", "devB"), metricsScope);
        assertEquals("JOB-HEX", id);
    }

    // ========== getValidationJobStatus ==========

    @Test
    void getValidationJobStatus_handlesSuccessAndPerRackValidationJobType() {
        when(ncpClientHelper.fetchJobStatus(any(), any(), any()))
                .thenReturn(Job.State.Succeeded.name());
        IssueField rackLocationField = mock(IssueField.class);
        when(rackLocationField.getName()).thenReturn("Rack Location");
        when(rackLocationField.getValue()).thenReturn("RACKZZ");
        Issue issue = mock(Issue.class);
        when(issue.getFields()).thenReturn(List.of(rackLocationField));
        SearchResult searchResult = mock(SearchResult.class);
        when(searchResult.getIssues()).thenReturn(List.of(issue));
        when(jiraSDService.searchJiraSD(anyString())).thenReturn(searchResult);

        when(ncpClientHelper.getNcpJobOutput(any(), any(), any(), any())).thenReturn(List.of());
        when(ncpClientHelper.getJobType(any(), any())).thenReturn(JobType.PER_RACK_VALIDATION_JOB);

        MetricsScope addResultsScope = mock(MetricsScope.class);
        try (MockedStatic<MetricsScope> metricsScopeMocked =
                Mockito.mockStatic(MetricsScope.class)) {
            metricsScopeMocked
                    .when(() -> MetricsScope.create(anyString()))
                    .thenReturn(addResultsScope);

            String status = service.getValidationJobStatus("JID", metricsScope, "reg", "RSN");
            assertEquals(Job.State.Succeeded.name(), status);
            verify(validationFailureResultDao)
                    .addValidationFailureResultsForRack(
                            any(), any(), eq(addResultsScope), eq("reg"));
            verify(metricsScope).recordSuccess();
        }
    }

    @Test
    void getValidationJobStatus_handlesHealthCheckJobType() {
        when(ncpClientHelper.fetchJobStatus(any(), any(), any()))
                .thenReturn(Job.State.Succeeded.name());
        IssueField rackLocationField = mock(IssueField.class);
        when(rackLocationField.getName()).thenReturn("Rack Location");
        when(rackLocationField.getValue()).thenReturn("RACKZZ");
        Issue issue = mock(Issue.class);
        when(issue.getFields()).thenReturn(List.of(rackLocationField));
        SearchResult searchResult = mock(SearchResult.class);
        when(searchResult.getIssues()).thenReturn(List.of(issue));
        when(jiraSDService.searchJiraSD(anyString())).thenReturn(searchResult);

        when(ncpClientHelper.getNcpJobOutput(any(), any(), any(), any())).thenReturn(List.of());
        when(ncpClientHelper.getJobType(any(), any())).thenReturn(JobType.HEALTH_CHECK);

        MetricsScope addResultsScope = mock(MetricsScope.class);
        try (MockedStatic<MetricsScope> metricsScopeMocked =
                Mockito.mockStatic(MetricsScope.class)) {
            metricsScopeMocked
                    .when(() -> MetricsScope.create(anyString()))
                    .thenReturn(addResultsScope);

            String status = service.getValidationJobStatus("JID", metricsScope, "region", "RSN");
            assertEquals(Job.State.Succeeded.name(), status);
            verify(validationFailureResultDao)
                    .updateValidationFailureResultsForDevices(
                            any(), eq(addResultsScope), eq("region"));
            verify(metricsScope).recordSuccess();
        }
    }

    @Test
    void getValidationJobStatus_throwsRenderableException_onFailureStatus() {
        when(ncpClientHelper.fetchJobStatus(any(), any(), any()))
                .thenReturn(Job.State.Failed.name());
        IssueField rackLocationField = mock(IssueField.class);
        when(rackLocationField.getName()).thenReturn("Rack Location");
        when(rackLocationField.getValue()).thenReturn("RAK");
        Issue issue = mock(Issue.class);
        when(issue.getFields()).thenReturn(List.of(rackLocationField));
        SearchResult searchResult = mock(SearchResult.class);
        when(searchResult.getIssues()).thenReturn(List.of(issue));
        when(jiraSDService.searchJiraSD(anyString())).thenReturn(searchResult);

        RenderableException ex =
                assertThrows(
                        RenderableException.class,
                        () -> service.getValidationJobStatus("JID", metricsScope, "reg", "serial"));
        assertEquals(ErrorCode.ExternalServerInvalidResponse, ex.getErrorCode());
    }

    @Test
    void getValidationJobStatus_returnsOtherStatus_whenOtherThanSucceededOrFailed() {
        when(ncpClientHelper.fetchJobStatus(any(), any(), any())).thenReturn("InProgress");
        IssueField rackLocationField = mock(IssueField.class);
        when(rackLocationField.getName()).thenReturn("Rack Location");
        when(rackLocationField.getValue()).thenReturn("XYZ");
        Issue issue = mock(Issue.class);
        when(issue.getFields()).thenReturn(List.of(rackLocationField));
        SearchResult searchResult = mock(SearchResult.class);
        when(searchResult.getIssues()).thenReturn(List.of(issue));
        when(jiraSDService.searchJiraSD(anyString())).thenReturn(searchResult);

        String ret = service.getValidationJobStatus("JID", metricsScope, "reg", "RSN");
        assertEquals("InProgress", ret);
    }

    // ========== searchInitialCablingTickets ==========
    @Test
    void searchInitialCablingTickets_throwsOnNullSerial() {
        RenderableException ex =
                assertThrows(
                        RenderableException.class,
                        () ->
                                service.validateCablingTasks(
                                        "r", "b", null, List.of(), metricsScope));
        assertEquals(ErrorCode.InvalidParameter, ex.getErrorCode());
    }
}
