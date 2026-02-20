package com.oracle.pic.networking.lvv.service.utils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

// DownloadCsvReportBuilder takes JSON input and produces a CSV string with one table per device
public final class DownloadCsvReportBuilder {

    @SuppressWarnings("unchecked")
    public static String buildMultiTableCsv(Object serviceResult, String rackSerial) {
        if (!(serviceResult instanceof Map)) {
            return "";
        }

        Map<String, Object> serialName = (Map<String, Object>) serviceResult;

        Object inner = null;
        if (rackSerial != null && serialName.containsKey(rackSerial)) {
            inner = serialName.get(rackSerial);
        } else if (!serialName.isEmpty()) {
            inner = serialName.values().iterator().next();
        }

        if (!(inner instanceof Map)) {
            return "";
        }

        Map<String, Object> devicesMap = (Map<String, Object>) inner;

        StringBuilder sb = new StringBuilder();

        List<String> preferredErrorTypes = List.of("LLDP Errors", "Optic Errors", "Power Errors");

        boolean firstDevice = true;
        for (Map.Entry<String, Object> deviceEntry : devicesMap.entrySet()) {
            String deviceName = deviceEntry.getKey();
            Object value = deviceEntry.getValue();
            if (!(value instanceof Map)) {
                continue;
            }
            Map<String, Object> errorTypeMap = (Map<String, Object>) value;

            // Determine ordered error types for this device
            List<String> errorTypes = new ArrayList<>();
            for (String p : preferredErrorTypes) {
                if (errorTypeMap.containsKey(p)) {
                    Object rowsObj = errorTypeMap.get(p);
                    if (rowsObj instanceof List && !((List<?>) rowsObj).isEmpty()) {
                        errorTypes.add(p);
                    }
                }
            }

            for (Map.Entry<String, Object> et : errorTypeMap.entrySet()) {
                String etName = et.getKey();
                if (errorTypes.contains(etName)) {
                    continue;
                }
                Object rowsObj = et.getValue();
                if (rowsObj instanceof List && !((List<?>) rowsObj).isEmpty()) {
                    errorTypes.add(etName);
                }
            }

            if (errorTypes.isEmpty()) {
                continue;
            }

            if (!firstDevice) {
                sb.append('\n').append('\n');
            }
            firstDevice = false;

            sb.append("Device : ").append(escapeCell(deviceName)).append('\n').append('\n');

            for (int i = 0; i < errorTypes.size(); i++) {
                String errorType = errorTypes.get(i);
                Object rowsObj = errorTypeMap.get(errorType);
                if (!(rowsObj instanceof List)) {
                    continue;
                }
                List<?> rows = (List<?>) rowsObj;
                if (rows.isEmpty()) {
                    continue;
                }

                sb.append(escapeCell(errorType)).append('\n');

                if ("Power Errors".equalsIgnoreCase(errorType)) {
                    sb.append("PSU status Down").append('\n');
                } else {
                    LinkedHashSet<String> headers = new LinkedHashSet<>();
                    for (Object r : rows) {
                        if (r instanceof Map) {
                            Map<String, Object> row = (Map<String, Object>) r;
                            for (String key : row.keySet()) {
                                if (shouldIncludeHeader(key)) {
                                    headers.add(key);
                                }
                            }
                        }
                    }

                    List<String> headerList = new ArrayList<>(headers);
                    if (!headerList.isEmpty()) {
                        writeRow(sb, headerList);

                        for (Object r : rows) {
                            if (r instanceof Map) {
                                Map<String, Object> row = (Map<String, Object>) r;
                                List<String> values = new ArrayList<>(headerList.size());
                                for (String h : headerList) {
                                    Object val = row.get(h);
                                    values.add(val == null ? "" : String.valueOf(val));
                                }
                                writeRow(sb, values);
                            }
                        }
                    } else {
                        for (Object r : rows) {
                            if (r instanceof Map) {
                                // Join values in insertion order
                                List<String> vals = new ArrayList<>();
                                for (Object v : ((Map<String, Object>) r).values()) {
                                    vals.add(v == null ? "" : String.valueOf(v));
                                }
                                writeRow(sb, vals);
                            }
                        }
                    }
                }

                if (i < errorTypes.size() - 1) {
                    sb.append('\n');
                }
            }
        }

        return sb.toString();
    }

    private static boolean shouldIncludeHeader(String key) {
        if (key == null) {
            return false;
        }
        return !("Device A Name".equalsIgnoreCase(key) || "Device Name".equalsIgnoreCase(key));
    }

    private static void writeRow(StringBuilder sb, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escapeCell(cells.get(i)));
        }
        sb.append('\n');
    }

    private static String escapeCell(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuote = false;
        for (int i = 0; i < value.length(); i++) {
            char specialChar = value.charAt(i);
            if (specialChar == '"'
                    || specialChar == ','
                    || specialChar == '\n'
                    || specialChar == '\r') {
                needsQuote = true;
                break;
            }
        }
        String replaceChar = value.replace("\"", "\"\"");
        return needsQuote ? ('"' + replaceChar + '"') : replaceChar;
    }
}
