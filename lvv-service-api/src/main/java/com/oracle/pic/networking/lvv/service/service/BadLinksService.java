package com.oracle.pic.networking.lvv.service.service;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.networking.lvv.service.dependencies.jira.JiraQueries;
import com.oracle.pic.networking.lvv.service.dependencies.jira.JiraSDService;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import com.oracle.pic.networking.lvv.service.dependencies.notificationservice.NotificationServiceHelper;
import com.oracle.pic.networking.lvv.service.kiev.BadLinks;
import com.oracle.pic.networking.lvv.service.kiev.BadLinksDao;
import com.oracle.pic.networking.lvv.service.kiev.Monitoring;
import com.oracle.pic.networking.lvv.service.kiev.MonitoringDao;
import com.oracle.pic.networking.lvv.service.model.BadLinkDetail;
import com.oracle.pic.networking.lvv.service.resources.ResourceModelTransformer;
import com.oracle.pic.networking.lvv.service.utils.BadLinksTitleParser;
import com.oracle.pic.networking.lvv.service.utils.BuildingNameMapper;
import com.oracle.pic.networking.lvv.service.utils.GeneralUtils;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@ToString
public class BadLinksService {
    private static final Duration STALENESS = Duration.ofMinutes(5);
    // JIRA_TICKET_BATCH_SIZE determines the batch size (used while checking which jira tickets are
    // still open)
    private static final int JIRA_TICKET_BATCH_SIZE = 50;
    // JIRA_QUERY_PAGE_SIZE determines page size of the result of jira jql query (used while
    // checking for new tickets)
    private static final int JIRA_QUERY_PAGE_SIZE = 100;

    private static final Set<String> FIELD_SUMMARY = Set.of("summary");

    private final MonitoringDao monitoringDao;
    private final BadLinksDao badLinksDao;
    private final JiraSDService jiraSDService;
    private final ResourceModelTransformer resourceModelTransformer;
    private final NotificationServiceHelper notificationServiceHelper;

    @Inject
    public BadLinksService(
            MonitoringDao monitoringDao,
            BadLinksDao badLinksDao,
            JiraSDService jiraSDService,
            ResourceModelTransformer resourceModelTransformer,
            NotificationServiceHelper notificationServiceHelper) {
        this.monitoringDao = monitoringDao;
        this.badLinksDao = badLinksDao;
        this.jiraSDService = jiraSDService;
        this.resourceModelTransformer = resourceModelTransformer;
        this.notificationServiceHelper = notificationServiceHelper;
    }

    public List<BadLinkDetail> getBadLinks(@NonNull String building, MetricsScope scope) {

        building = BuildingNameMapper.getCanonicalNameOrOriginal(building);
        log.info("GetBadLinks details for building {}", building);

        Monitoring monitoringRecord = monitoringDao.getRowForBuilding(building);
        Instant now = Instant.now();

        boolean stale = isStale(monitoringRecord, now);

        if (!stale) {
            // Fresh (<5 minutes): return data from DB (BadLinks details rows)
            log.info("Current time - Last Updated < 5 minutes");

            List<BadLinks> rowsFromDB = badLinksDao.getBadLinksForBuilding(building);

            log.info("Found {} rows for building {}", rowsFromDB.size(), building);

            return rowsFromDB.stream()
                    .map(resourceModelTransformer::toModel)
                    .collect(Collectors.toList());
        } else {
            log.info("Current time - Last Updated >= 5 minutes");

            // Prune DB to only still-open Jira tickets (selective delete)
            pruneRowsForClosedTickets(building, monitoringRecord, scope);

            // fetch new tickets from jira and store them in the DB
            fetchNewTicketsFromJiraAndInsertInDB(building, monitoringRecord, now, scope);

            // update the lastUpdated in DB (this should occur after
            // fetchNewTicketsFromJiraAndInsertInDB runs successfully so that if it fails then
            // it will throw a renderable exception and the api flow will return from there only,
            // and
            // we won't update the lastUpdated and in the next api call we will
            // fetch the tickets from the previous lastUpdated, this way we won't miss tickets
            monitoringDao.upsertLastUpdated(building, Timestamp.from(now));

            // get the rows which are finally present in DB (after pruning and fetching new rows)
            List<BadLinks> rowsInDB = badLinksDao.getBadLinksForBuilding(building);
            log.info("Returning {} existing Bad Links for building {}", rowsInDB.size(), building);

            return rowsInDB.stream()
                    .map(resourceModelTransformer::toModel)
                    .collect(Collectors.toList());
        }
    }

    private boolean isStale(Monitoring monitoringRecord, Instant now) {
        log.info("Calculating staleness with lastUpdated {} and now {}", monitoringRecord, now);
        if (monitoringRecord == null) {
            return true;
        }
        Timestamp lastUpdated = monitoringRecord.getLastUpdated();
        if (lastUpdated == null) {
            return true;
        }
        return lastUpdated.toInstant().plus(STALENESS).isBefore(now);
    }

    private void fetchNewTicketsFromJiraAndInsertInDB(
            String building, Monitoring monitoringRecord, Instant now, MetricsScope scope) {

        String createdOnOrAfter =
                GeneralUtils.formatTimestamp(
                        monitoringRecord == null ? null : monitoringRecord.getLastUpdated());
        String jql =
                String.format(JiraQueries.NEW_BAD_LINKS_JQL_TEMPLATE, building, createdOnOrAfter);

        List<Issue> newIssues =
                jiraSDService.searchJiraSDPaginated(jql, JIRA_QUERY_PAGE_SIZE, FIELD_SUMMARY);

        if (newIssues.isEmpty()) {
            log.info("Jira result does not contain any issues");
            return;
        }

        List<BadLinks> fetchedNewRowsFromJira = new ArrayList<>();
        Set<String> alreadySeenTickets = new HashSet<>();
        try {
            for (Issue issue : newIssues) {
                String summary = issue != null ? issue.getSummary() : null;
                String ticket = issue != null ? issue.getKey() : null;
                if (ticket == null
                        || summary == null
                        || summary.isBlank()
                        || !alreadySeenTickets.add(ticket)) {
                    continue;
                }
                String[] parsed = BadLinksTitleParser.parse(summary);
                if (parsed == null || parsed.length != 2) {
                    log.error("Parsing Error for ticket: {}", ticket);
                } else {
                    String device = parsed[0].trim();
                    String remoteDevice = parsed[1].trim();
                    fetchedNewRowsFromJira.add(
                            BadLinks.builder()
                                    .building(building)
                                    .device(device)
                                    .remoteDevice(remoteDevice)
                                    .jiraTicket(ticket)
                                    .build());
                }
            }
        } catch (Exception e) {
            log.error("Failed building rows from Jira result", e);
        }
        log.info(
                "Built {} rows from Jira result: {}",
                fetchedNewRowsFromJira.size(),
                fetchedNewRowsFromJira);

        // Build set of existing jira tickets in DB to avoid duplicate inserts
        List<BadLinks> existingRowsInDB = badLinksDao.getBadLinksForBuilding(building);
        Set<String> existingTicketsInDB = new HashSet<>();
        for (BadLinks b : existingRowsInDB) {
            existingTicketsInDB.add(b.getJiraTicket());
        }

        // Filter: keep only tickets not already present in DB;
        // This de-duplication is needed because there may be a case when the ticket is already
        // present in the database
        // Eg: When during insertion of new tickets in DB the batch fails then we won't update
        // lastUpdated and in the next /badLinks api call we will fetch the same tickets
        List<BadLinks> newRows = new ArrayList<>();
        for (BadLinks fetchedRowFromJira : fetchedNewRowsFromJira) {
            if (!existingTicketsInDB.contains(fetchedRowFromJira.getJiraTicket())) {
                newRows.add(fetchedRowFromJira);
            }
        }

        if (!newRows.isEmpty()) {
            badLinksDao.insertRows(building, newRows);
            notifyVendors(
                    newRows,
                    building,
                    now,
                    (monitoringRecord == null ? null : monitoringRecord.getTopicOcid()),
                    scope);
        }
    }

    private void pruneRowsForClosedTickets(
            String building, Monitoring monitoringRecord, MetricsScope scope) {
        log.info("Starting to prune details of closed tickets for building {}", building);

        // Load existing rows and compute which jira tickets remain open in Jira
        List<BadLinks> rowsInDB = badLinksDao.getBadLinksForBuilding(building);

        if (rowsInDB.isEmpty()) {
            log.info("No bad links found for building {}", building);
            return;
        }

        final List<String> ticketsInDB = new ArrayList<>(rowsInDB.size());
        for (BadLinks badLinks : rowsInDB) {
            ticketsInDB.add(badLinks.getJiraTicket());
        }

        log.info(
                "Found {} existing tickets for building {} in the database",
                ticketsInDB.size(),
                building);

        String updatedOnOrAfter =
                GeneralUtils.formatTimestamp(
                        monitoringRecord == null ? null : monitoringRecord.getLastUpdated());

        // Check which tickets are now closed in jira by querying jira using
        // JiraQueries.CLOSED_BAD_LINKS_JQL_TEMPLATE jql
        Set<String> closedTicketsFromJira = new HashSet<>();
        for (int startIndexOfBatch = 0;
                startIndexOfBatch < ticketsInDB.size();
                startIndexOfBatch += JIRA_TICKET_BATCH_SIZE) {
            List<String> batch =
                    ticketsInDB.subList(
                            startIndexOfBatch,
                            Math.min(
                                    startIndexOfBatch + JIRA_TICKET_BATCH_SIZE,
                                    ticketsInDB.size()));
            String jiraTicketsBatchStr = String.join(", ", batch);
            String jqlBatch =
                    String.format(
                            JiraQueries.CLOSED_BAD_LINKS_JQL_TEMPLATE,
                            jiraTicketsBatchStr,
                            updatedOnOrAfter);

            List<Issue> closedIssuesFromJira =
                    jiraSDService.searchJiraSDPaginated(jqlBatch, JIRA_QUERY_PAGE_SIZE, null);

            log.info(
                    "Fetched {} closed jira issues (batch {}-{}): ",
                    closedIssuesFromJira.size(),
                    startIndexOfBatch,
                    startIndexOfBatch + batch.size() - 1);

            for (Issue issue : closedIssuesFromJira) {
                if (issue != null && issue.getKey() != null) {
                    closedTicketsFromJira.add(issue.getKey());
                    log.info("Ticket: {}", issue.getKey());
                }
            }
        }

        if (!closedTicketsFromJira.isEmpty()) {
            badLinksDao.deleteRowsByJiraTickets(building, new ArrayList<>(closedTicketsFromJira));
        }
    }

    private void notifyVendors(
            List<BadLinks> rows,
            String building,
            Instant now,
            String topicOcid,
            MetricsScope scope) {
        if (topicOcid == null) {
            scope.emit(MetricNames.BadLinks.NotificationTopicNotFound.name(), 1.0);
            log.error(
                    "Notification Topic does not exist for building {}, skipping notification",
                    building);
            return;
        }
        // Fixed column widths for device and remoteDevice
        final int COLUMN_WIDTH = 36;
        // How many spaces to add before the "Ticket:" line beyond the prefix length
        final int INDENT_BEFORE_TICKET = 3;
        final int MAX_ROWS_TO_SEND = 500;

        String spaces = " ".repeat(INDENT_BEFORE_TICKET);
        StringBuilder body = new StringBuilder();
        body.append("New Link Down Alert(s) in")
                .append(" -- ")
                .append(building)
                .append(" -- ")
                .append(rows.size())
                .append(" more link(s) are down")
                .append("\n\n");
        body.append("Generated Time (UTC): ")
                .append(GeneralUtils.formatTimestamp(Timestamp.from(now)))
                .append("\n\n\n");

        int limit = Math.min(rows.size(), MAX_ROWS_TO_SEND);
        for (int i = 0; i < limit; i++) {
            BadLinks badLink = rows.get(i);

            body.append(i + 1)
                    .append(". ")
                    .append(fitPaddingRight(badLink.getDevice(), COLUMN_WIDTH))
                    .append("  <==>  ")
                    .append(badLink.getRemoteDevice())
                    .append("\n");

            body.append(spaces)
                    .append("Ticket: ")
                    .append("https://jira-sd.mc1.oracleiaas.com/browse/")
                    .append(badLink.getJiraTicket())
                    .append("\n\n");
        }

        if (rows.size() > limit) {
            body.append("(").append(rows.size() - limit).append(" more...)\n");
        }

        String subject = String.format("New Link Down Alert(s) in %s", building);
        notificationServiceHelper.publish(topicOcid, subject, body.toString());
    }

    /** Truncate with "..." if longer than width, then pad spaces on the right to exactly width. */
    private static String fitPaddingRight(String str, int width) {
        if (str == null) {
            str = "";
        }
        String out = str;
        if (out.length() > width) {
            out = out.substring(0, width - 3) + "...";
        }
        int padding = width - out.length();
        if (padding > 0) {
            out = out + " ".repeat(padding);
        }
        return out;
    }
}
