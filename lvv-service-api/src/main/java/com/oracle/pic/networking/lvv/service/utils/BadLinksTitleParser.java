package com.oracle.pic.networking.lvv.service.utils;

import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility to parse Jira issue titles for bad link details. Expected format (prefix can be any
 * string): Some String device:<device> remote_device:<remoteDevice>
 *
 * <p>Example: INTERFACE DOWN device:iad8-c1-b19-t2-r10:Ethernet22/1
 * remote_device:iad8-c1-b19-t1-r11:Ethernet19/1
 *
 * <p>Returns a String[2] = { device, remoteDevice } when matched, otherwise null.
 */
@Slf4j
public final class BadLinksTitleParser {
    private BadLinksTitleParser() {}

    private static final Pattern PATTERN =
            Pattern.compile(
                    "device:([^\\s]+)\\s+remote_device:([^\\s]+)", Pattern.CASE_INSENSITIVE);

    public static String[] parse(String summary) {
        log.info("Parsing summary {}", summary);
        if (summary == null || summary.isBlank()) {
            return null;
        }
        Matcher matcher = PATTERN.matcher(summary);
        if (matcher.find()) {
            log.info("device {}, remoteDevice {}", matcher.group(1), matcher.group(2));
            return new String[] {matcher.group(1), matcher.group(2)};
        }
        try (MetricsScope scope =
                MetricsScope.create(MetricNames.MetricScopeNames.BAD_LINKS.name())) {
            log.error("Could not parse summary {}, returning null", summary);
            scope.emit(MetricNames.BadLinks.ErrorParsingSummary.name(), 1.0);
            scope.recordSuccess();
        }
        return null;
    }
}
