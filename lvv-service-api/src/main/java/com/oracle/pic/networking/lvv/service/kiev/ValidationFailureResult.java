package com.oracle.pic.networking.lvv.service.kiev;

import static com.oracle.pic.kiev.mapping.annotations.ColumnType.STRING;
import static com.oracle.pic.kiev.mapping.annotations.ColumnType.TIMESTAMP;

import com.oracle.pic.kiev.mapping.annotations.Column;
import com.oracle.pic.kiev.mapping.annotations.ColumnType;
import com.oracle.pic.kiev.mapping.annotations.HashKey;
import com.oracle.pic.kiev.mapping.annotations.KievEntity;
import com.oracle.pic.kiev.mapping.annotations.KievIndex;
import com.oracle.pic.kiev.mapping.annotations.KievNestedEntity;
import com.oracle.pic.networking.lvv.service.utils.KievConstants;
import java.sql.Timestamp;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;
import lombok.Value;

// This Data Object represents the Link Validation Failures we get after running the NCP
// HEALTH_CHECK Job
// It encompasses 3 kinds of errors:
//     1) LLDP Error - Whether Links on a rack are cabled to the right destination device or is
// there a mismatch
//     2) Optic Error - The Tx(Output) Power, and Rx(Input) Power of a link, which should be in the
// pre-defined acceptable range.
//     3) PSU Error - Whether the links are not cabled tight enough
// The HashKey of this DO, is teh Link Source which is a combination of Device Name, and the port on
// the device from which the link starts
// The DO is Indexed on RackSerial, so that we can easily get the list of links that belong to rack

@Getter
@Setter
@Builder(builderClassName = "Builder")
@KievEntity(builderClass = ValidationFailureResult.Builder.class, builderPrefix = "")
@KievIndex(
        name = ValidationFailureResult.RACK_SERIAL_COLUMN_NAME,
        columns = {ValidationFailureResult.RACK_SERIAL_COLUMN_NAME},
        unique = false)
@ToString
public class ValidationFailureResult {

    public static final String RACK_SERIAL_COLUMN_NAME = "rackSlNumCol";

    @NonNull
    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH, name = RACK_SERIAL_COLUMN_NAME)
    private String rackSerial;

    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH)
    private String deviceARack;

    @NonNull @HashKey @KievNestedEntity private LinkSource linkSource;

    @Value
    @KievEntity(builderClass = LinkSource.Builder.class, builderPrefix = "")
    @AllArgsConstructor
    @lombok.Builder(builderClassName = "Builder")
    public static class LinkSource {

        @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH, index = 1)
        String deviceAName;

        @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH, index = 2)
        String deviceAPort;
    }

    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH, nullable = true)
    private String deviceBRack;

    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH, nullable = true)
    private String deviceBName;

    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH, nullable = true)
    private String deviceBPort;

    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH, nullable = true)
    private String deviceBRackExpected;

    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH, nullable = true)
    private String deviceBNameExpected;

    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH, nullable = true)
    private String deviceBPortExpected;

    @Column(type = ColumnType.ENUM_STRING, length = KievConstants.MAX_NAME_LENGTH, nullable = true)
    private LldpStatus lldpStatus;

    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH, nullable = true)
    private String txPower;

    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH, nullable = true)
    private String rxPower;

    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH, nullable = true)
    private String psuFailure;

    @Column(type = ColumnType.ENUM_STRING, length = KievConstants.MAX_NAME_LENGTH, nullable = true)
    private LinkStatus linkStatus;

    @Column(type = TIMESTAMP)
    private Timestamp lastValidatedTime;

    @Value
    @KievEntity
    @AllArgsConstructor
    @lombok.Builder(builderClassName = "Builder", toBuilder = true)
    public static class RackSerialIndex {
        @SuppressWarnings("unused")
        RackSerialIndex() {
            rackSerial = null;
        }

        @Column(type = ColumnType.STRING, length = 64, name = RACK_SERIAL_COLUMN_NAME)
        String rackSerial;
    }
}
