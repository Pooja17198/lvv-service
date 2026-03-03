package com.oracle.pic.networking.lvv.service.schema;

import com.oracle.pic.kiev.BucketDescription;
import com.oracle.pic.kiev.ColumnDescription;
import com.oracle.pic.kiev.ColumnSetDescription;
import com.oracle.pic.kiev.DataType;
import com.oracle.pic.networking.lvv.service.kiev.BadLinks;
import com.oracle.pic.networking.lvv.service.kiev.Monitoring;
import com.oracle.pic.networking.lvv.service.kiev.ProjectItem;
import com.oracle.pic.networking.lvv.service.utils.KievConstants;
import com.oracle.pic.sfw.kiev.schema.updates.SchemaUpdate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Defines idempotent Kiev schema updates for LvvService. */
public final class ApiSchemaUpdates {

    private static final String PROJECT_ITEMS_BUCKET = "projectItemsBucket";
    private static final String BLOCK_DETAILS_BUCKET = "blockDetailBucket";
    private static final String BAD_LINKS_BUCKET = "badLinksBucket";
    private static final String MONITORING_BUCKET = "monitoringBucket";

    private static final SchemaUpdate V1_ADD_REGION_COLUMN =
            new SchemaUpdate(1, "Add regionName column (nullable STRING(64))")
                    .addColumn(
                            "regionName",
                            new ColumnDescription<>("regionName", DataType.STRING, 64, true),
                            PROJECT_ITEMS_BUCKET);

    private static final SchemaUpdate V2_ADD_REGION_INDEX =
            new SchemaUpdate(2, "Add index on regionName")
                    .addIndex(
                            "regionName",
                            new ColumnSetDescription(
                                    false,
                                    "regionName",
                                    Collections.singletonList(
                                            new ColumnDescription<>(
                                                    "regionName", DataType.STRING, 64, true))),
                            PROJECT_ITEMS_BUCKET);

    private static final SchemaUpdate V3_ADD_CM_LINK_COLUMN =
            new SchemaUpdate(
                            3,
                            "Add cmLink column (nullable STRING("
                                    + KievConstants.MAX_NAME_LENGTH
                                    + "))")
                    .addColumn(
                            "cmLink",
                            new ColumnDescription<>(
                                    "cmLink", DataType.STRING, KievConstants.MAX_NAME_LENGTH, true),
                            PROJECT_ITEMS_BUCKET);

    private static final SchemaUpdate V4_ADD_CREATED_BY_COLUMN =
            new SchemaUpdate(4, "Add createdBy column (nullable STRING(64))")
                    .addColumn(
                            "createdBy",
                            new ColumnDescription<>("createdBy", DataType.STRING, 64, true),
                            PROJECT_ITEMS_BUCKET);

    private static final SchemaUpdate V5_ADD_VENDOR_EMAIL_COLUMN =
            new SchemaUpdate(
                            5,
                            "Add vendorEmail column (nullable STRING("
                                    + KievConstants.MAX_NAME_LENGTH
                                    + "))")
                    .addColumn(
                            ProjectItem.VENDOR_EMAIL_COLUMN_NAME,
                            new ColumnDescription<>(
                                    ProjectItem.VENDOR_EMAIL_COLUMN_NAME,
                                    DataType.STRING,
                                    KievConstants.MAX_NAME_LENGTH,
                                    true),
                            PROJECT_ITEMS_BUCKET);

    //    private static final SchemaUpdate V6_ADD_BLOCKDETAILS_BUILDING_INDEX =
    //            new SchemaUpdate(6, "Add index on building")
    //                    .addIndex(
    //                            BlockDetails.BUILDING_COLUMN_NAME_IDX,
    //                            new ColumnSetDescription(
    //                                    false,
    //                                    BlockDetails.BUILDING_COLUMN_NAME_IDX,
    //                                    Collections.singletonList(
    //                                            new ColumnDescription<>(
    //                                                    BlockDetails.BUILDING_COLUMN_NAME,
    //                                                    DataType.STRING,
    //                                                    KievConstants.MAX_NAME_LENGTH,
    //                                                    false))),
    //                            BLOCK_DETAILS_BUCKET);

    private static final SchemaUpdate V7_ADD_BADLINKS_BUCKET =
            new SchemaUpdate(7, "Create BadLinks bucket, columns, and building index")
                    .addBucket(
                            BAD_LINKS_BUCKET,
                            BucketDescription.builder()
                                    .setName(BAD_LINKS_BUCKET)
                                    .addHashKeyDescription(
                                            new ColumnDescription<>(
                                                    BadLinks.JIRA_TICKET_COLUMN_NAME,
                                                    DataType.STRING,
                                                    KievConstants.MAX_NAME_LENGTH,
                                                    false))
                                    .addColumnDescription(
                                            new ColumnDescription<>(
                                                    BadLinks.BUILDING_COLUMN_NAME,
                                                    DataType.STRING,
                                                    KievConstants.MAX_NAME_LENGTH,
                                                    false))
                                    .addColumnDescription(
                                            new ColumnDescription<>(
                                                    "device",
                                                    DataType.STRING,
                                                    KievConstants.MAX_NAME_LENGTH,
                                                    false))
                                    .addColumnDescription(
                                            new ColumnDescription<>(
                                                    "remoteDevice",
                                                    DataType.STRING,
                                                    KievConstants.MAX_NAME_LENGTH,
                                                    false))
                                    .addIndex(
                                            BadLinks.BUILDING_COLUMN_NAME_IDX,
                                            BadLinks.BUILDING_COLUMN_NAME)
                                    .build());

    private static final SchemaUpdate V8_ADD_MONITORING_BUCKET =
            new SchemaUpdate(8, "Create Monitoring bucket and columns")
                    .addBucket(
                            MONITORING_BUCKET,
                            BucketDescription.builder()
                                    .setName(MONITORING_BUCKET)
                                    .addHashKeyDescription(
                                            new ColumnDescription<>(
                                                    Monitoring.BUILDING_COLUMN_NAME,
                                                    DataType.STRING,
                                                    KievConstants.MAX_NAME_LENGTH,
                                                    false))
                                    .addColumnDescription(
                                            new ColumnDescription<>(
                                                    "lastUpdated", DataType.TIMESTAMP, true))
                                    .addColumnDescription(
                                            new ColumnDescription<>(
                                                    Monitoring.TOPIC_OCID_COLUMN_NAME,
                                                    DataType.STRING,
                                                    KievConstants.MAX_NAME_LENGTH,
                                                    true))
                                    .build());

    public static List<SchemaUpdate> plan() {
        return Arrays.asList(
                V1_ADD_REGION_COLUMN,
                V2_ADD_REGION_INDEX,
                V3_ADD_CM_LINK_COLUMN,
                V4_ADD_CREATED_BY_COLUMN,
                V5_ADD_VENDOR_EMAIL_COLUMN,
                //                V6_ADD_BLOCKDETAILS_BUILDING_INDEX,
                V7_ADD_BADLINKS_BUCKET,
                V8_ADD_MONITORING_BUCKET);
    }

    private ApiSchemaUpdates() {}
}
