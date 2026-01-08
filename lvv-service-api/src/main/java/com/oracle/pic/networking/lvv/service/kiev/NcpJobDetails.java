package com.oracle.pic.networking.lvv.service.kiev;

import static com.oracle.pic.kiev.mapping.annotations.ColumnType.ENUM_STRING;
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
import lombok.Setter;
import lombok.ToString;
import lombok.Value;

// This Data Object represents the NCP Job details we have triggered. There's one job triggered for
// each device
// Job Type - HEALTH_CHECK Job
// It encompasses 4 kinds of status:
//     1) IN_PROGRESS - Job is in progress, and we are waiting for results
//     2) COMPLETED - Job is completed
//     3) FAILED - Job Failed due to an external error
//     4) DEVICE_UNREACHABLE - Device is unreachable by NCP to run validations
// For each device only the last triggered job ID is stored
// The DO is Indexed on RackSerial, so that we can easily get the list of devices that belong to
// rack

@Getter
@Setter
@Builder(builderClassName = "Builder")
@KievEntity(builderClass = NcpJobDetails.Builder.class, builderPrefix = "")
@KievIndex(
        name = NcpJobDetails.RACK_SERIAL_COLUMN_NAME,
        columns = {NcpJobDetails.RACK_SERIAL_COLUMN_NAME},
        unique = false)
@ToString
public class NcpJobDetails {

    public static final String RACK_SERIAL_COLUMN_NAME = "rackSerialNum_Col";

    @NonNull
    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH, name = RACK_SERIAL_COLUMN_NAME)
    private String rackSerial;

    @NonNull
    @HashKey
    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH)
    private String deviceName;

    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH, nullable = true)
    private String jobId;

    @Column(type = ENUM_STRING, length = KievConstants.MAX_NAME_LENGTH)
    private JobStatus jobStatus;

    @Value
    @KievEntity
    @AllArgsConstructor
    @lombok.Builder(builderClassName = "Builder", toBuilder = true)
    public static class RackSerialIndex {
        @SuppressWarnings("unused")
        RackSerialIndex() {
            rackSerial = null;
        }

        @Column(type = STRING, length = 64, name = RACK_SERIAL_COLUMN_NAME)
        String rackSerial;
    }
}
