// package com.oracle.pic.networking.lvv.service.service;
//
// import static org.junit.jupiter.api.Assertions.*;
// import static org.mockito.ArgumentMatchers.any;
// import static org.mockito.Mockito.*;
//
// import com.atlassian.jira.rest.client.api.domain.Issue;
// import com.atlassian.jira.rest.client.api.domain.IssueField;
// import com.atlassian.jira.rest.client.api.domain.SearchResult;
// import com.google.common.collect.Lists;
// import com.oracle.pic.commons.exceptions.server.RenderableException;
// import com.oracle.pic.commons.metrics.MetricsScope;
// import com.oracle.pic.networking.lvv.service.dependencies.jira.JiraSDService;
// import com.oracle.pic.networking.lvv.service.dependencies.ncp.NcpClientHelper;
// import com.oracle.pic.networking.lvv.service.kiev.ValidationFailureResult;
// import com.oracle.pic.networking.lvv.service.kiev.ValidationFailureResultDao;
// import com.oracle.pic.networking.ncp.model.Job;
// import java.util.List;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.ExtendWith;
// import org.mockito.InjectMocks;
// import org.mockito.Mock;
// import org.mockito.junit.jupiter.MockitoExtension;
//
// @ExtendWith(MockitoExtension.class)
// public class CablingValidationServiceTest {
//
//    @Mock private NcpClientHelper ncpClientHelper;
//
//    @Mock private JiraSDService jiraSDService;
//
//    @Mock private ValidationFailureResultDao validationFailureResultDao;
//
//    @InjectMocks private CablingValidationService cablingValidationService;
//
//    private Issue issue;
//    private IssueField issueField;
//    private SearchResult searchResult;
//    private Job job;
//    private List<ValidationFailureResult> output;
//
//    @BeforeEach
//    void setup() {
//        issue = mock(Issue.class);
//        issueField = mock(IssueField.class);
//        searchResult = mock(SearchResult.class);
//        job = mock(Job.class);
//        output =
//                Lists.newArrayList(
//                        ValidationFailureResult.builder()
//                                .linkSource(ValidationFailureResult.LinkSource.builder().build())
//                                .rackSerial("rackSerial")
//                                .projectId("proj123")
//                                .build());
//    }
//
//    @Test
//    void testValidateCablingTasksSuccess() throws Exception {
//        // Arrange
//        when(jiraSDService.searchJiraSD(any())).thenReturn(searchResult);
//        when(searchResult.getIssues()).thenReturn(Lists.newArrayList(issue));
//        when(issue.getFields()).thenReturn(Lists.newArrayList(issueField));
//        when(issueField.getName()).thenReturn("Rack Location");
//        when(issueField.getValue()).thenReturn("rackLocation");
//        when(ncpClientHelper.createJob(any(), any(), any())).thenReturn(job);
//        when(ncpClientHelper.pollJobToFetchResult(any(), any(), any(MetricsScope.class)))
//                .thenReturn(true);
//        when(ncpClientHelper.getNcpJobOutput(any())).thenReturn(output);
//
//        // Act
//        Boolean result =
//                cablingValidationService.validateCablingTasks(
//                        "building", "block", "rackSerialNumber",
// Lists.newArrayList("deviceName"));
//
//        // Assert
//        assertTrue(result);
//        verify(ncpClientHelper, times(1)).createJob(any(), any(), any());
//        verify(ncpClientHelper, times(1))
//                .pollJobToFetchResult(any(), any(), any(MetricsScope.class));
//        verify(ncpClientHelper, times(1)).getNcpJobOutput(any());
//        verify(validationFailureResultDao, times(1))
//                .addValidationFailureResultsForRack(any(), any());
//    }
//
//    @Test
//    void testValidateCablingTasksFailure() {
//        // Arrange
//        when(jiraSDService.searchJiraSD(any())).thenReturn(searchResult);
//        when(searchResult.getIssues()).thenReturn(Lists.newArrayList());
//
//        // Act and Assert
//        assertThrows(
//                RenderableException.class,
//                () -> cablingValidationService.validateCablingTasks(
//                        "building", "block", "rackSerialNumber",
//                        Lists.newArrayList("deviceName")));
//    }
//
//    @Test
//    void testValidateCablingTasksPollJobFailure() {
//        // Arrange
//        when(jiraSDService.searchJiraSD(any())).thenReturn(searchResult);
//        when(searchResult.getIssues()).thenReturn(Lists.newArrayList(issue));
//        when(issue.getFields()).thenReturn(Lists.newArrayList(issueField));
//        when(issueField.getName()).thenReturn("Rack Location");
//        when(issueField.getValue()).thenReturn("rackLocation");
//        when(ncpClientHelper.createJob(any(), any(), any())).thenReturn(job);
//        when(ncpClientHelper.pollJobToFetchResult(any(), any(), any(MetricsScope.class)))
//                .thenReturn(false);
//
//        // Act and Assert
//        assertThrows(
//                RenderableException.class,
//                () -> cablingValidationService.validateCablingTasks(
//                        "building", "block", "rackSerialNumber",
//                        Lists.newArrayList("deviceName")));
//    }
//
//    @Test
//    void testValidateCablingTasksException() {
//        // Arrange
//        when(jiraSDService.searchJiraSD(any())).thenThrow(new RuntimeException());
//
//        // Act
//        Boolean result =
//                cablingValidationService.validateCablingTasks(
//                        "building", "block", "rackSerialNumber",
// Lists.newArrayList("deviceName"));
//
//        // Assert
//        assertFalse(result);
//    }
// }
