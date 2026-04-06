package com.oracle.pic.networking.lvv.service.kiev;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;
import lombok.Value;

// Stores patch panel (physical cutsheet) data fetched from the IDE service.
// Keyed by "deviceName#devicePort" composite string for fast per-port lookup.
// Indexed on rackSerial for bulk retrieval of all entries for a rack.
// lastFetchedAt records when the entry was last refreshed from IDE.

@Getter
@Setter
@Builder(builderClassName = "Builder")
@KievEntity(builderClass = PatchPanelEntry.Builder.class, builderPrefix = "")
@KievIndex(
        name = PatchPanelEntry.RACK_SERIAL_COLUMN_NAME,
        columns = {PatchPanelEntry.RACK_SERIAL_COLUMN_NAME},
        unique = false)
@ToString
public class PatchPanelEntry {

    public static final String RACK_SERIAL_COLUMN_NAME = "ppeRackSerialCol";

    /** Composite hash key: "deviceName#devicePort" */
    @NonNull
    @HashKey
    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH)
    private String devicePortKey;

    @NonNull
    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH, name = RACK_SERIAL_COLUMN_NAME)
    private String rackSerial;

    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH, nullable = true)
    private String deviceName;

    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH, nullable = true)
    private String devicePort;

    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH, nullable = true)
    private String buildingName;

    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH, nullable = true)
    private String roomName;

    @Column(type = STRING, length = KievConstants.MAX_NAME_LENGTH, nullable = true)
    private String rackNumber;

    @Column(type = JSON_CLOB, nullable = true)
    private List<String> easyMark;

    @Column(type = TIMESTAMP, nullable = true)
    private Timestamp lastFetchedAt;

    public static String buildKey(String deviceName, String devicePort) {
        return deviceName + "#" + devicePort;
    }

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
