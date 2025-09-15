package com.oracle.pic.networking.lvv.service.kiev;

import static com.oracle.pic.kiev.mapping.annotations.ColumnType.STRING;

import com.oracle.pic.kiev.mapping.annotations.Column;
import com.oracle.pic.kiev.mapping.annotations.ColumnType;
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

// This Data Object represents a Project, and the Vendor the project belongs to
// A combination of ProjectItem and BlockDetails will tell us the blocks a Vendor is assigned to
// work on
// The HashKey for the DO is Project ID, which is a user defined value
// It is indexed on Vendor Name, so that we can easily find the list of Projects assigned to a
// Vendor

@Getter
@ToString
@Builder(builderClassName = "Builder")
@KievEntity(builderClass = ProjectItem.Builder.class, builderPrefix = "")
@KievIndex(
        name = ProjectItem.VENDOR_COLUMN_NAME,
        columns = {ProjectItem.VENDOR_COLUMN_NAME},
        unique = false)
public class ProjectItem {

    public static final String VENDOR_COLUMN_NAME = "vendorName";

    @NonNull
    @HashKey
    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH)
    private String projectId;

    @NonNull
    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH, name = VENDOR_COLUMN_NAME)
    private String vendorName;

    @Value
    @KievEntity
    @AllArgsConstructor
    @lombok.Builder(builderClassName = "Builder", toBuilder = true)
    public static class VendorNameIndex {
        @SuppressWarnings("unused")
        VendorNameIndex() {
            vendorName = null;
        }

        @Column(type = ColumnType.STRING, length = 64, name = VENDOR_COLUMN_NAME)
        String vendorName;
    }
}
