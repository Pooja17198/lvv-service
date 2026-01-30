package com.oracle.pic.networking.lvv.service.dependencies.jira;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueField;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class JiraSDHelperTest {

    private final JiraSDHelper helper = new JiraSDHelper();

    private IssueField field(String name, Object value) {
        IssueField f = mock(IssueField.class);
        when(f.getName()).thenReturn(name);
        when(f.getValue()).thenReturn(value);
        return f;
    }

    private Issue issueWithFields(List<IssueField> fields) {
        Issue issue = mock(Issue.class);
        when(issue.getFields()).thenReturn(fields);
        return issue;
    }

    @Test
    void extractSerialNumber_nullIssue_returnsNull() {
        assertNull(helper.extractSerialNumber(null));
    }

    @Test
    void extractSerialNumber_nullFields_returnsNull() {
        Issue issue = mock(Issue.class);
        when(issue.getFields()).thenReturn(null);
        assertNull(helper.extractSerialNumber(issue));
    }

    @Test
    void extractSerialNumber_prefersSerialNumber_overAssetId() {
        IssueField serial = field("Serial Number", "SERIAL-123");
        IssueField asset = field("Asset ID", "ASSET-999");
        Issue issue = issueWithFields(Arrays.asList(asset, serial)); // order shouldn't matter

        String result = helper.extractSerialNumber(issue);
        assertEquals("SERIAL-123", result);
    }

    @Test
    void extractSerialNumber_serialNumberPresent_nullValue_fallsBackToAssetId() {
        IssueField serialNull = field("Serial Number", null);
        IssueField asset = field("Asset ID", "ASSET-777");
        Issue issue = issueWithFields(Arrays.asList(serialNull, asset));

        String result = helper.extractSerialNumber(issue);
        assertEquals("ASSET-777", result);
    }

    @Test
    void extractSerialNumber_onlyAssetIdPresent_returnsAssetId() {
        IssueField asset = field("Asset ID", "ASSET-123");
        Issue issue = issueWithFields(List.of(asset));

        String result = helper.extractSerialNumber(issue);
        assertEquals("ASSET-123", result);
    }

    @Test
    void extractSerialNumber_noMatchingFields_returnsNull() {
        IssueField other1 = field("Some Field", "X");
        IssueField other2 = field("Another Field", "Y");
        Issue issue = issueWithFields(Arrays.asList(other1, other2));

        assertNull(helper.extractSerialNumber(issue));
    }

    @Test
    void extractSerialNumber_serialNumberEmptyString_returnsEmptyString_andDoesNotFallback() {
        IssueField serialEmpty = field("Serial Number", "");
        IssueField asset = field("Asset ID", "ASSET-ABC");
        Issue issue = issueWithFields(Arrays.asList(serialEmpty, asset));

        String result = helper.extractSerialNumber(issue);
        assertEquals("", result); // empty string is not null; should be returned
    }

    @Test
    void extractSerialNumber_valueObject_toStringIsUsed() {
        Object complexValue =
                new Object() {
                    @Override
                    public String toString() {
                        return "OBJ-TOSTRING";
                    }
                };
        IssueField serialObject = field("Serial Number", complexValue);
        Issue issue = issueWithFields(List.of(serialObject));

        String result = helper.extractSerialNumber(issue);
        assertEquals("OBJ-TOSTRING", result);
    }

    @Test
    void extractSerialNumber_multipleSerialNumberFields_firstMatchUsed() {
        IssueField serial1 = field("Serial Number", "FIRST");
        IssueField serial2 = field("Serial Number", "SECOND");
        Issue issue = issueWithFields(Arrays.asList(serial1, serial2));

        String result = helper.extractSerialNumber(issue);
        assertEquals("FIRST", result);
    }
}
