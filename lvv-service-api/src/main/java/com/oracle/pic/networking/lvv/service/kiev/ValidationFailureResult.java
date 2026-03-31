package com.oracle.pic.networking.lvv.service.kiev;

import static com.oracle.pic.kiev.mapping.annotations.ColumnType.INT;
import static com.oracle.pic.kiev.mapping.annotations.ColumnType.JSON_CLOB;
import static com.oracle.pic.kiev.mapping.annotations.ColumnType.STRING;
import static com.oracle.pic.kiev.mapping.annotations.ColumnType.TIMESTAMP;

import com.oracle.pic.kiev.mapping.annotations.Column;
import com.oracle.pic.kiev.mapping.annotations.ColumnType;
import com.oracle.pic.kiev.mapping.annotations.HashKey;
import com.oracle.pic.kiev.mapping.annotations.KievEntity;
import com.oracle.pic.kiev.mapping.annotations.KievIndex;
import com.oracle.pic.networking.lvv.service.utils.KievConstants;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
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

    public static final String RACK_SERIAL_COLUMN_NAME = "rackSlColumn";

    @NonNull
    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH, name = RACK_SERIAL_COLUMN_NAME)
    private String rackSerial;

    @NonNull
    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH)
    @HashKey
    private String deviceName;

    @NonNull
    @Column(type = JSON_CLOB)
    private Map<String, List<Map<String, String>>> validationResults;

    @NonNull
    @Column(type = INT)
    private Integer numberOfValidations;

    @NonNull
    @Column(type = TIMESTAMP)
    private Timestamp firstValidatedTime;

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
