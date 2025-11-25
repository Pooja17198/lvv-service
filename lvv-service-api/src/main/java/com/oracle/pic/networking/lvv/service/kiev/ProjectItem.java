package com.oracle.pic.networking.lvv.service.kiev;

import static com.oracle.pic.kiev.mapping.annotations.ColumnType.STRING;

import com.oracle.pic.kiev.mapping.annotations.Column;
import com.oracle.pic.kiev.mapping.annotations.HashKey;
import com.oracle.pic.kiev.mapping.annotations.KievEntity;
import com.oracle.pic.kiev.mapping.annotations.KievIndex;
import com.oracle.pic.kiev.mapping.annotations.SequenceColumn;
import com.oracle.pic.networking.lvv.service.utils.KievConstants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;
import lombok.Value;

// This Data Object represents a Project, and the Vendor the project belongs to
// A combination of ProjectItem and BlockDetails will tell us the blocks a Vendor is assigned to
// work on
// The HashKey for the DO is Project ID, which is a user defined value
// It is indexed on Vendor Name, so that we can easily find the list of Projects assigned to a
// Vendor

@Getter
@Setter
@ToString
@Builder(builderClassName = "Builder")
@KievEntity(builderClass = ProjectItem.Builder.class, builderPrefix = "")
@KievIndex(
        name = ProjectItem.VENDOR_COLUMN_NAME,
        columns = {ProjectItem.VENDOR_COLUMN_NAME},
        unique = false)
@KievIndex(
        name = ProjectItem.PROJECT_ID_COLUMN_NAME,
        columns = {ProjectItem.PROJECT_ID_COLUMN_NAME},
        unique = false)
@KievIndex(
        name = ProjectItem.REGION_COLUMN_NAME,
        columns = {ProjectItem.REGION_COLUMN_NAME},
        unique = false)
@KievIndex(
        name = ProjectItem.VENDOR_REGION_INDEX_NAME,
        columns = {ProjectItem.VENDOR_COLUMN_NAME, ProjectItem.REGION_COLUMN_NAME},
        unique = false)
public class ProjectItem {

    public static final String VENDOR_COLUMN_NAME = "vendorsName";
    public static final String CM_LINK_COLUMN_NAME = "cmLink";
    public static final String CREATED_BY_COLUMN_NAME = "createdBy";
    public static final String PROJECT_ID_COLUMN_NAME = "projectIdCol";
    public static final String REGION_COLUMN_NAME = "regionName";
    public static final String VENDOR_REGION_INDEX_NAME = "vendorRegionIdx";

    @HashKey
    @SequenceColumn(sequence = "projectKey")
    Long projectKey;

    @NonNull
    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH, name = PROJECT_ID_COLUMN_NAME)
    private String projectId;

    @NonNull
    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH, name = VENDOR_COLUMN_NAME)
    private String vendorName;

    //    @NonNull
    @Column(type = STRING, length = 64, nullable = true, name = CREATED_BY_COLUMN_NAME)
    private String createdBy;

    //    @NonNull
    @Column(
            type = STRING,
            length = KievConstants.MAX_NAME_LENGTH,
            nullable = true,
            name = CM_LINK_COLUMN_NAME)
    private String cmLink;

    @Column(type = STRING, length = 64, nullable = true, name = REGION_COLUMN_NAME)
    private String regionName;

    @Value
    @KievEntity
    @AllArgsConstructor
    @lombok.Builder(builderClassName = "Builder", toBuilder = true)
    public static class VendorNameIndex {
        @SuppressWarnings("unused")
        VendorNameIndex() {
            vendorName = null;
        }

        @Column(type = STRING, length = 64, name = VENDOR_COLUMN_NAME)
        String vendorName;
    }

    @Value
    @KievEntity
    @AllArgsConstructor
    @lombok.Builder(builderClassName = "Builder", toBuilder = true)
    public static class ProjectIdIndex {
        @SuppressWarnings("unused")
        ProjectIdIndex() {
            projectId = null;
        }

        @Column(type = STRING, length = 64, name = PROJECT_ID_COLUMN_NAME)
        String projectId;
    }

    @Value
    @KievEntity
    @AllArgsConstructor
    @lombok.Builder(builderClassName = "Builder", toBuilder = true)
    public static class RegionNameIndex {
        @SuppressWarnings("unused")
        RegionNameIndex() {
            regionName = null;
        }

        @Column(type = STRING, length = 64, name = REGION_COLUMN_NAME)
        String regionName;
    }

    @Value
    @KievEntity
    @AllArgsConstructor
    @lombok.Builder(builderClassName = "Builder", toBuilder = true)
    public static class VendorRegionIndex {
        @SuppressWarnings("unused")
        VendorRegionIndex() {
            vendorName = null;
            regionName = null;
        }

        @Column(type = STRING, length = 64, name = VENDOR_COLUMN_NAME)
        String vendorName;

        @Column(type = STRING, length = 64, name = REGION_COLUMN_NAME)
        String regionName;
    }
}
