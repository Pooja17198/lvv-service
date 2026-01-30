package com.oracle.pic.networking.lvv.service.dependencies.jira;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueField;
import com.google.inject.Inject;
import java.util.Objects;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public class JiraSDHelper {

    private String extractField(Issue issue, String field) {
        return StreamSupport.stream(issue.getFields().spliterator(), false)
                .filter(f -> field.equals(f.getName()))
                .map(IssueField::getValue)
                .filter(Objects::nonNull)
                .map(Object::toString)
                .findFirst()
                .orElse(null);
    }

    public String extractSerialNumber(Issue issue) {
        if (issue == null || issue.getFields() == null) {
            return null;
        }

        String serial = extractField(issue, "Serial Number");

        if (serial != null) {
            return serial;
        }

        return extractField(issue, "Asset ID");
    }
}
