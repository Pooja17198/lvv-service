package com.oracle.pic.networking.lvv.service.schema;

import com.oracle.pic.kiev.ColumnDescription;
import com.oracle.pic.kiev.ColumnSetDescription;
import com.oracle.pic.kiev.DataType;
import com.oracle.pic.sfw.kiev.schema.updates.SchemaUpdate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Defines idempotent Kiev schema updates for LvvService. */
public final class ApiSchemaUpdates {

    private static final String PROJECT_ITEMS_BUCKET = "projectItemsBucket";

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

    private static final SchemaUpdate V3_ADD_VENDOR_REGION_INDEX =
            new SchemaUpdate(3, "Add composite index on (vendorsName, regionName)")
                    .addIndex(
                            "vendorRegionIdx",
                            new ColumnSetDescription(
                                    false,
                                    "vendorRegionIdx",
                                    Arrays.asList(
                                            new ColumnDescription<>(
                                                    "vendorsName", DataType.STRING, 255, true),
                                            new ColumnDescription<>(
                                                    "regionName", DataType.STRING, 64, true))),
                            PROJECT_ITEMS_BUCKET);

    public static List<SchemaUpdate> plan() {
        return Arrays.asList(V1_ADD_REGION_COLUMN, V2_ADD_REGION_INDEX, V3_ADD_VENDOR_REGION_INDEX);
    }

    private ApiSchemaUpdates() {}
}
