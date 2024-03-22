package com.oracle.pic.networking.lvv.service.dependencies.jira;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class JiraSDConfig {
    @NonNull private String jiraSDEndpoint;
    @NonNull private String usernameSecretPath;
    @NonNull private String passwordSecretPath;
}
