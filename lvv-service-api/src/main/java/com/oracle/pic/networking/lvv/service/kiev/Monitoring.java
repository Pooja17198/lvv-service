package com.oracle.pic.networking.lvv.service.kiev;

import static com.oracle.pic.kiev.mapping.annotations.ColumnType.STRING;
import static com.oracle.pic.kiev.mapping.annotations.ColumnType.TIMESTAMP;

import com.oracle.pic.kiev.mapping.annotations.Column;
import com.oracle.pic.kiev.mapping.annotations.HashKey;
import com.oracle.pic.kiev.mapping.annotations.KievEntity;
import com.oracle.pic.networking.lvv.service.utils.KievConstants;
import java.sql.Timestamp;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/**
 * Monitoring represents, per building, the number of links currently down and the last time this
 * value was refreshed from Jira, as well as whether an alert is active (new count increased vs
 * old).
 */
@Getter
@Builder(builderClassName = "Builder")
@KievEntity(builderClass = Monitoring.Builder.class, builderPrefix = "")
@ToString
public class Monitoring {

    public static final String BUILDING_COLUMN_NAME = "buildingNameCol";
    public static final String TOPIC_OCID_COLUMN_NAME = "topicOcid";
    public static final String LAST_EMAIL_SENT_AT_COLUMN_NAME = "lastEmailSentAt";

    @NonNull
    @HashKey
    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH, name = BUILDING_COLUMN_NAME)
    private String building;

    @Column(type = TIMESTAMP, nullable = true)
    private Timestamp lastUpdated;

    @Column(
            type = STRING,
            length = KievConstants.MAX_NAME_LENGTH,
            name = TOPIC_OCID_COLUMN_NAME,
            nullable = true)
    private String topicOcid;

    @Column(type = TIMESTAMP, nullable = true, name = LAST_EMAIL_SENT_AT_COLUMN_NAME)
    private Timestamp lastEmailSentAt;
}
