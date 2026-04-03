package com.oracle.pic.networking.lvv.service.kiev;

import static com.oracle.pic.kiev.mapping.annotations.ColumnType.STRING;

import com.oracle.pic.kiev.mapping.annotations.Column;
import com.oracle.pic.kiev.mapping.annotations.HashKey;
import com.oracle.pic.kiev.mapping.annotations.KievEntity;
import com.oracle.pic.kiev.mapping.annotations.KievIndex;
import com.oracle.pic.networking.lvv.service.utils.KievConstants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;

/**
 * BadLinks is a row-per-link entity storing details of down links for a building. Hash key is a
 * synthetic id = building|device|remoteDevice (single string) to ensure uniqueness. An index on
 * building allows listing all details for a building.
 */
@Getter
@Builder(builderClassName = "Builder")
@KievEntity(builderClass = BadLinks.Builder.class, builderPrefix = "")
@KievIndex(
        name = BadLinks.BUILDING_COLUMN_NAME_IDX,
        columns = {BadLinks.BUILDING_COLUMN_NAME},
        unique = false)
@ToString
public class BadLinks {

    public static final String BUILDING_COLUMN_NAME = "buildingNameCol";
    public static final String JIRA_TICKET_COLUMN_NAME = "jiraTicket";

    public static final String BUILDING_COLUMN_NAME_IDX = "badLinksBuildingColIdx";

    /** Building identifier (indexed for listing) */
    @NonNull
    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH, name = BUILDING_COLUMN_NAME)
    private String building;

    /** Example: iad8-c1-b19-t2-r10:Ethernet22/1 */
    @NonNull
    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH)
    private String device;

    /** Example: iad8-c1-b19-t1-r11:Ethernet19/1 */
    @NonNull
    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH)
    private String remoteDevice;

    /** Jira ticket key (hash key), e.g., DO-12345 */
    @NonNull
    @HashKey
    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH, name = JIRA_TICKET_COLUMN_NAME)
    private String jiraTicket;

    @Value
    @KievEntity
    @AllArgsConstructor
    @lombok.Builder(builderClassName = "Builder", toBuilder = true)
    public static class BuildingIndex {
        @SuppressWarnings("unused")
        BuildingIndex() {
            building = null;
        }

        @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH, name = BUILDING_COLUMN_NAME)
        String building;
    }
}
