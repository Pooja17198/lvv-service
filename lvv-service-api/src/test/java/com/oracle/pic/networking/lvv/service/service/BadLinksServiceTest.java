package com.oracle.pic.networking.lvv.service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.networking.lvv.service.dependencies.jira.JiraSDService;
import com.oracle.pic.networking.lvv.service.dependencies.notificationservice.NotificationServiceHelper;
import com.oracle.pic.networking.lvv.service.kiev.BadLinks;
import com.oracle.pic.networking.lvv.service.kiev.BadLinksDao;
import com.oracle.pic.networking.lvv.service.kiev.Monitoring;
import com.oracle.pic.networking.lvv.service.kiev.MonitoringDao;
import com.oracle.pic.networking.lvv.service.resources.ResourceModelTransformer;
import com.oracle.pic.networking.lvv.service.utils.BuildingNameMapper;
import com.oracle.pic.networking.lvv.service.utils.GeneralUtils;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BadLinksServiceTest {

    @Mock MonitoringDao monitoringDao;
    @Mock BadLinksDao badLinksDao;
    @Mock JiraSDService jiraSDService;
    @Mock NotificationServiceHelper notificationServiceHelper;
    @Mock MetricsScope scope;
    @Spy ResourceModelTransformer resourceModelTransformer = new ResourceModelTransformer();

    @InjectMocks BadLinksService badLinksService;

    private final String building = "BLD-1";
    private final String expectedBuilding = BuildingNameMapper.getCanonicalNameOrOriginal(building);

    @BeforeEach
    void setUp() {}

    @Test
    void testFresh_returnsDbRows_withoutJiraOrUpsert() {
        Monitoring fresh =
                Monitoring.builder()
                        .building(building)
                        .lastUpdated(Timestamp.from(Instant.now().minusSeconds(60)))
                        .build();
        when(monitoringDao.getRowForBuilding(expectedBuilding)).thenReturn(fresh);

        BadLinks row1 =
                BadLinks.builder()
                        .building(building)
                        .device("devA")
                        .remoteDevice("remA")
                        .jiraTicket("DO-A")
                        .build();
        BadLinks row2 =
                BadLinks.builder()
                        .building(building)
                        .device("devB")
                        .remoteDevice("remB")
                        .jiraTicket("DO-B")
                        .build();
        when(badLinksDao.getBadLinksForBuilding(expectedBuilding)).thenReturn(List.of(row1, row2));

        var result = badLinksService.getBadLinks(building, scope);

        assertEquals(2, result.size());
        verify(jiraSDService, never()).searchJiraSDPaginated(anyString(), anyInt(), anySet());
        verify(monitoringDao, never()).upsertLastUpdated(anyString(), any(Timestamp.class));
        verify(notificationServiceHelper, never()).publish(anyString(), anyString(), anyString());
    }

    @Test
    void test_whenStale_prunesClosedTickets_fetchesNoNewTickets_upserts_andReturnsOpenRows() {
        Monitoring stale =
                Monitoring.builder()
                        .building(building)
                        .lastUpdated(Timestamp.from(Instant.now().minusSeconds(10 * 60)))
                        .build();
        when(monitoringDao.getRowForBuilding(expectedBuilding)).thenReturn(stale);

        BadLinks openRow =
                BadLinks.builder()
                        .building(building)
                        .device("devA")
                        .remoteDevice("remA")
                        .jiraTicket("DO-1")
                        .build();
        BadLinks closedRow =
                BadLinks.builder()
                        .building(building)
                        .device("devB")
                        .remoteDevice("remB")
                        .jiraTicket("DO-2")
                        .build();
        when(badLinksDao.getBadLinksForBuilding(expectedBuilding))
                .thenReturn(List.of(openRow, closedRow), List.of(openRow));

        List<Issue> pruneClosed = mockIssuesWithKeys(List.of("DO-2"));
        List<Issue> noNewIssues = List.of();
        when(jiraSDService.searchJiraSDPaginated(anyString(), anyInt(), any()))
                .thenReturn(pruneClosed, noNewIssues);

        var result = badLinksService.getBadLinks(building, scope);

        verify(badLinksDao)
                .deleteRowsByJiraTickets(
                        eq(expectedBuilding),
                        argThat(ids -> ids.size() == 1 && ids.contains(closedRow.getJiraTicket())));
        verify(badLinksDao, never()).insertRows(eq(expectedBuilding), anyList());
        verify(monitoringDao).upsertLastUpdated(eq(expectedBuilding), any(Timestamp.class));
        verify(notificationServiceHelper, never()).publish(anyString(), anyString(), anyString());
        assertEquals(1, result.size());
    }

    @Test
    void test_whenStale_prunesClosedTickets_fetchesNewTickets_andPublishes() {
        Monitoring stale =
                Monitoring.builder()
                        .building(building)
                        .lastUpdated(Timestamp.from(Instant.now().minusSeconds(10 * 60)))
                        .topicOcid("ocid1.topic")
                        .build();
        when(monitoringDao.getRowForBuilding(expectedBuilding)).thenReturn(stale);

        when(badLinksDao.getBadLinksForBuilding(expectedBuilding))
                .thenReturn(
                        List.of(),
                        List.of(),
                        List.of(
                                BadLinks.builder()
                                        .building(expectedBuilding)
                                        .device("devX")
                                        .remoteDevice("remY")
                                        .jiraTicket("DO-NEW-42")
                                        .build()));

        Issue newIssue = mock(Issue.class);
        when(newIssue.getSummary()).thenReturn("INTERFACE DOWN device:devX remote_device:remY");
        when(newIssue.getKey()).thenReturn("DO-NEW-42");
        when(jiraSDService.searchJiraSDPaginated(anyString(), anyInt(), any()))
                .thenReturn(List.of(newIssue));

        var result = badLinksService.getBadLinks(building, scope);

        verify(badLinksDao)
                .insertRows(
                        eq(expectedBuilding),
                        argThat(
                                rows ->
                                        rows.size() == 1
                                                && "devX".equals(rows.get(0).getDevice())
                                                && "remY".equals(rows.get(0).getRemoteDevice())
                                                && "DO-NEW-42"
                                                        .equals(rows.get(0).getJiraTicket())));
        verify(monitoringDao).upsertLastEmailSentAt(eq(expectedBuilding), any(Timestamp.class));
        verify(monitoringDao).upsertLastUpdated(eq(expectedBuilding), any(Timestamp.class));
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationServiceHelper)
                .publish(
                        eq("ocid1.topic"),
                        eq("New Link Down Alert(s) in " + expectedBuilding),
                        bodyCaptor.capture());
        String body = bodyCaptor.getValue();
        assertTrue(body.contains("Links down detected"));
        assertTrue(body.contains("Detected Time(UTC): "));
        assertTrue(body.contains("Links Down Detected: 1"));
        assertTrue(body.contains("devX"));
        assertTrue(body.contains("remY"));
        assertEquals(1, result.size());
    }

    @Test
    void test_whenStale_withLastEmailSentAt_usesAdditionalTemplate_andPublishes() {
        Timestamp lastEmailSentAt = Timestamp.from(Instant.parse("2026-03-13T11:40:00Z"));
        Monitoring stale =
                Monitoring.builder()
                        .building(building)
                        .lastUpdated(Timestamp.from(Instant.now().minusSeconds(10 * 60)))
                        .lastEmailSentAt(lastEmailSentAt)
                        .topicOcid("ocid1.topic")
                        .build();
        when(monitoringDao.getRowForBuilding(expectedBuilding)).thenReturn(stale);

        when(badLinksDao.getBadLinksForBuilding(expectedBuilding))
                .thenReturn(
                        List.of(),
                        List.of(),
                        List.of(
                                BadLinks.builder()
                                        .building(expectedBuilding)
                                        .device("devX")
                                        .remoteDevice("remY")
                                        .jiraTicket("DO-NEW-88")
                                        .build()));

        Issue newIssue = mock(Issue.class);
        when(newIssue.getSummary()).thenReturn("INTERFACE DOWN device:devX remote_device:remY");
        when(newIssue.getKey()).thenReturn("DO-NEW-88");
        when(jiraSDService.searchJiraSDPaginated(anyString(), anyInt(), any()))
                .thenReturn(List.of(newIssue));

        var result = badLinksService.getBadLinks(building, scope);

        verify(monitoringDao).upsertLastEmailSentAt(eq(expectedBuilding), any(Timestamp.class));
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationServiceHelper)
                .publish(
                        eq("ocid1.topic"),
                        eq("New Link Down Alert(s) in " + expectedBuilding),
                        bodyCaptor.capture());
        String body = bodyCaptor.getValue();
        assertTrue(body.contains("Additional links down detected since last notification."));
        assertTrue(
                body.contains(
                        "Last Email sent at(UTC): "
                                + GeneralUtils.formatTimestamp(lastEmailSentAt)));
        assertTrue(body.contains("Current Detected Time(UTC): "));
        assertTrue(body.contains("Additional Links Down Detected: 1"));
        assertTrue(body.contains("devX"));
        assertTrue(body.contains("remY"));
        assertEquals(1, result.size());
    }

    private List<Issue> mockIssuesWithKeys(List<String> keys) {
        List<Issue> issues = new ArrayList<>();
        for (String k : keys) {
            Issue issue = mock(Issue.class);
            when(issue.getKey()).thenReturn(k);
            issues.add(issue);
        }
        return issues;
    }

    @Test
    void test_upsertLastUpdatedAlwaysAfterInsertRows() {
        // Arrange: stale monitoring record to trigger refresh
        Monitoring stale =
                Monitoring.builder()
                        .building(building)
                        .lastUpdated(Timestamp.from(Instant.now().minusSeconds(10 * 60)))
                        .build();
        when(monitoringDao.getRowForBuilding(expectedBuilding)).thenReturn(stale);

        // No existing rows, so any new rows fetched from Jira will be inserted
        when(badLinksDao.getBadLinksForBuilding(expectedBuilding)).thenReturn(List.of());

        // Return one new issue so newRows.size() > 0 (only stub key; summary not needed for this
        // test)
        Issue newIssue = mock(Issue.class);
        when(newIssue.getKey()).thenReturn("DO-NEW-99");
        when(newIssue.getSummary()).thenReturn("INTERFACE DOWN device:devZ remote_device:remA");
        lenient()
                .when(jiraSDService.searchJiraSDPaginated(anyString(), anyInt(), any()))
                .thenReturn(List.of(newIssue));

        // If notify path is executed, Topic OCID may be required

        // Act
        badLinksService.getBadLinks(building, scope);

        // Assert: verify call order - insertRows happens before upsertLastUpdated
        InOrder inOrder = inOrder(badLinksDao, monitoringDao);
        inOrder.verify(badLinksDao).insertRows(eq(expectedBuilding), anyList());
        inOrder.verify(monitoringDao).upsertLastUpdated(eq(expectedBuilding), any(Timestamp.class));
    }
}
