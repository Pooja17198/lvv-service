package com.oracle.pic.networking.lvv.service.schema;

import com.oracle.pic.kiev.ColumnDescription;
import com.oracle.pic.kiev.ColumnSetDescription;
import com.oracle.pic.kiev.DataType;
import com.oracle.pic.networking.lvv.service.utils.KievConstants;
import com.oracle.pic.sfw.kiev.schema.updates.SchemaUpdate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Defines idempotent Kiev schema updates for LvvService. */
public final class ApiSchemaUpdates {

    private static final String PROJECT_ITEMS_BUCKET = "projectItemsBucket";
    private static final String VALIDATION_RESULTS_BUCKET = "cableResultJsonStore";

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

    private static final SchemaUpdate V5_ADD_LAST_VALIDATED_TIME_COLUMN =
            new SchemaUpdate(5, "Add lastValidatedTime column (nullable TIMESTAMP)")
                    .addColumn(
                            "lastValidatedTime",
                            new ColumnDescription<>("lastValidatedTime", DataType.TIMESTAMP, true),
                            VALIDATION_RESULTS_BUCKET);

    public static List<SchemaUpdate> plan() {
        return Arrays.asList(
                V1_ADD_REGION_COLUMN,
                V2_ADD_REGION_INDEX,
                V3_ADD_CM_LINK_COLUMN,
                V4_ADD_CREATED_BY_COLUMN,
                V5_ADD_LAST_VALIDATED_TIME_COLUMN);
    }

    private ApiSchemaUpdates() {}
}
