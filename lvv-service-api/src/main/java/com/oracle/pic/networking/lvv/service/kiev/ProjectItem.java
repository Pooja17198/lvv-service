package com.oracle.pic.networking.lvv.service.kiev;

import static com.oracle.pic.kiev.mapping.annotations.ColumnType.STRING;

import com.oracle.pic.kiev.mapping.annotations.Column;
import com.oracle.pic.kiev.mapping.annotations.HashKey;
import com.oracle.pic.kiev.mapping.annotations.KievEntity;
import com.oracle.pic.networking.lvv.service.utils.KievConstants;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder(builderClassName = "Builder")
@KievEntity(builderClass = ProjectItem.Builder.class, builderPrefix = "")
public class ProjectItem {
    @NonNull
    @HashKey
    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH)
    private String projectId;

    @NonNull
    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH)
    private String vendorName;

    @NonNull
    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH)
    private String building;

    @NonNull
    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH)
    private String block;

    @NonNull
    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH)
    private String type;
}
