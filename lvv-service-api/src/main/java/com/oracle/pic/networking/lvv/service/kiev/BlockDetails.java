package com.oracle.pic.networking.lvv.service.kiev;

import static com.oracle.pic.kiev.mapping.annotations.ColumnType.STRING;

import com.oracle.pic.kiev.mapping.annotations.Column;
import com.oracle.pic.kiev.mapping.annotations.ColumnType;
import com.oracle.pic.kiev.mapping.annotations.HashKey;
import com.oracle.pic.kiev.mapping.annotations.KievEntity;
import com.oracle.pic.kiev.mapping.annotations.KievIndex;
import com.oracle.pic.kiev.mapping.annotations.KievNestedEntity;
import com.oracle.pic.networking.lvv.service.utils.KievConstants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.Value;

// This Data Object represents a Block(in a building), and the project the Block belongs to
// The HashKey is a combination of Block and Building which will be unique all over OCI since
// building implicitly captures the region it belongs to
// It is indexed on a project, so we can find all the blocks that belong to a project

@Getter
@Builder(builderClassName = "Builder")
@KievEntity(builderClass = BlockDetails.Builder.class, builderPrefix = "")
@RequiredArgsConstructor
@KievIndex(
        name = BlockDetails.PROJECT_ID_COLUMN_NAME,
        columns = {BlockDetails.PROJECT_ID_COLUMN_NAME},
        unique = false)
@ToString
public class BlockDetails {

    public static final String PROJECT_ID_COLUMN_NAME = "projectIdColumn";

    @NonNull @HashKey @KievNestedEntity private Block block;

    @Value
    @KievEntity(builderClass = Block.Builder.class, builderPrefix = "")
    @AllArgsConstructor
    @lombok.Builder(builderClassName = "Builder")
    public static class Block {

        @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH, index = 1)
        String building;

        @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH, index = 2)
        String blockNumber;
    }

    @NonNull
    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH, name = PROJECT_ID_COLUMN_NAME)
    private String projectId;

    @Value
    @KievEntity
    @AllArgsConstructor
    @lombok.Builder(builderClassName = "Builder", toBuilder = true)
    public static class ProjectIdIndex {
        @SuppressWarnings("unused")
        ProjectIdIndex() {
            projectId = null;
        }

        @Column(type = ColumnType.STRING, length = 64, name = PROJECT_ID_COLUMN_NAME)
        String projectId;
    }
}
