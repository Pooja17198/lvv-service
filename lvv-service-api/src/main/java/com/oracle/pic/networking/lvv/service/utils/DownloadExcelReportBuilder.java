package com.oracle.pic.networking.lvv.service.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public final class DownloadExcelReportBuilder {

    private static final String SOURCE_DEVICE = "Source Device";

    private static final String SUMMARY_SHEET = "Summary";
    private static final String LLDP_SHEET = "LLDP Mismatch + Link Down";
    private static final String OPTIC_SHEET = "Optic Errors";
    private static final String FEC_BER_SHEET = "FEC_BER Errors";
    private static final String POWER_SHEET = "Power Errors";
    private static final String INTERFACE_DOWN_SHEET = "Interface Down Errors";
    private static final String FAN_SHEET = "Fan Errors";

    private static final List<String> REQUIRED_SHEETS =
            List.of(
                    SUMMARY_SHEET,
                    LLDP_SHEET,
                    OPTIC_SHEET,
                    FEC_BER_SHEET,
                    POWER_SHEET,
                    INTERFACE_DOWN_SHEET,
                    FAN_SHEET);

    private DownloadExcelReportBuilder() {}

    @SuppressWarnings("unchecked")
    public static byte[] buildWorkbook(Object serviceResult, String rackSerial) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Map<String, List<Map<String, Object>>> groupedRows = initGroups();
            Map<String, LinkedHashSet<String>> headers = initHeaders();

            if (serviceResult instanceof Map) {
                Map<String, Object> outer = (Map<String, Object>) serviceResult;
                Object inner = null;
                if (rackSerial != null && outer.containsKey(rackSerial)) {
                    inner = outer.get(rackSerial);
                } else if (!outer.isEmpty()) {
                    inner = outer.values().iterator().next();
                }

                if (inner instanceof Map) {
                    Map<String, Object> devicesMap = (Map<String, Object>) inner;
                    for (Map.Entry<String, Object> deviceEntry : devicesMap.entrySet()) {
                        String deviceName = deviceEntry.getKey();
                        if (!(deviceEntry.getValue() instanceof Map)) {
                            continue;
                        }
                        Map<String, Object> categoryMap =
                                (Map<String, Object>) deviceEntry.getValue();
                        for (Map.Entry<String, Object> categoryEntry : categoryMap.entrySet()) {
                            if (!(categoryEntry.getValue() instanceof List)) {
                                continue;
                            }
                            String category = categoryEntry.getKey();
                            List<?> rows = (List<?>) categoryEntry.getValue();
                            for (Object rowObj : rows) {
                                if (!(rowObj instanceof Map)) {
                                    continue;
                                }
                                Map<String, Object> row =
                                        new LinkedHashMap<>((Map<String, Object>) rowObj);
                                row.putIfAbsent("Source Device", deviceName);

                                String targetSheet = classifySheet(category, row);
                                groupedRows.get(targetSheet).add(row);
                                collectHeaders(headers.get(targetSheet), row, targetSheet);
                            }
                        }
                    }
                }
            }

            createSummarySheet(workbook, groupedRows);
            for (String sheetName : REQUIRED_SHEETS) {
                if (SUMMARY_SHEET.equals(sheetName)) {
                    continue;
                }
                if (groupedRows.get(sheetName) == null || groupedRows.get(sheetName).isEmpty()) {
                    continue;
                }
                createDataSheet(
                        workbook, sheetName, groupedRows.get(sheetName), headers.get(sheetName));
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate Excel workbook", e);
        }
    }

    private static Map<String, List<Map<String, Object>>> initGroups() {
        Map<String, List<Map<String, Object>>> groupedRows = new LinkedHashMap<>();
        for (String sheet : REQUIRED_SHEETS) {
            if (!SUMMARY_SHEET.equals(sheet)) {
                groupedRows.put(sheet, new ArrayList<>());
            }
        }
        return groupedRows;
    }

    private static Map<String, LinkedHashSet<String>> initHeaders() {
        Map<String, LinkedHashSet<String>> headers = new LinkedHashMap<>();
        for (String sheet : REQUIRED_SHEETS) {
            if (!SUMMARY_SHEET.equals(sheet)) {
                headers.put(sheet, new LinkedHashSet<>());
            }
        }
        return headers;
    }

    private static void createSummarySheet(
            XSSFWorkbook workbook, Map<String, List<Map<String, Object>>> groupedRows) {
        Sheet summary = workbook.createSheet(SUMMARY_SHEET);
        writeRow(summary.createRow(0), List.of("Error Category", "Error Count"));

        int rowIndex = 1;
        for (String sheetName : REQUIRED_SHEETS) {
            if (SUMMARY_SHEET.equals(sheetName)) {
                continue;
            }
            writeRow(
                    summary.createRow(rowIndex++),
                    List.of(sheetName, String.valueOf(groupedRows.get(sheetName).size())));
        }
        autoSize(summary, 2);
    }

    private static void createDataSheet(
            XSSFWorkbook workbook,
            String sheetName,
            List<Map<String, Object>> rows,
            LinkedHashSet<String> headerSet) {
        Sheet sheet = workbook.createSheet(sheetName);

        List<String> headers = new ArrayList<>(headerSet);
        if (headers.remove(SOURCE_DEVICE)) {
            headers.add(0, SOURCE_DEVICE);
        }
        if (headers.isEmpty()) {
            headers = defaultHeadersForSheet(sheetName);
        }

        writeRow(sheet.createRow(0), headers);

        int rowIndex = 1;
        for (Map<String, Object> row : rows) {
            List<String> values = new ArrayList<>();
            for (String header : headers) {
                Object value = row.get(header);
                values.add(value == null ? "" : String.valueOf(value));
            }
            writeRow(sheet.createRow(rowIndex++), values);
        }

        autoSize(sheet, headers.size());
    }

    private static void writeRow(Row row, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(values.get(i));
        }
    }

    private static void autoSize(Sheet sheet, int size) {
        for (int i = 0; i < size; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private static void collectHeaders(
            LinkedHashSet<String> headers, Map<String, Object> row, String sheetName) {
        if (POWER_SHEET.equals(sheetName) && headers.isEmpty()) {
            headers.add("Status");
            return;
        }

        if (row.containsKey(SOURCE_DEVICE)) {
            headers.add(SOURCE_DEVICE);
        }

        for (String key : row.keySet()) {
            if (shouldIncludeHeader(key)) {
                headers.add(key);
            }
        }
    }

    private static boolean shouldIncludeHeader(String key) {
        if (key == null) {
            return false;
        }
        return !("Device A Name".equalsIgnoreCase(key) || "Device Name".equalsIgnoreCase(key));
    }

    private static List<String> defaultHeadersForSheet(String sheetName) {
        if (POWER_SHEET.equals(sheetName)) {
            return List.of("Status");
        }
        return List.of(SOURCE_DEVICE);
    }

    private static String classifySheet(String category, Map<String, Object> row) {
        if (category == null) {
            return LLDP_SHEET;
        }

        String normalized = category.toLowerCase(Locale.ROOT);
        if (normalized.contains("fan")) {
            return FAN_SHEET;
        }
        if (normalized.contains("fec")) {
            return FEC_BER_SHEET;
        }
        if (normalized.contains("power")) {
            row.clear();
            row.put("Status", "PSU status Down");
            return POWER_SHEET;
        }
        if (normalized.contains("optic")) {
            return OPTIC_SHEET;
        }
        if (normalized.contains("interface")) {
            return INTERFACE_DOWN_SHEET;
        }
        if (normalized.contains("lldp")) {
            if (isInterfaceDownRow(row)) {
                return INTERFACE_DOWN_SHEET;
            }
            return LLDP_SHEET;
        }
        return LLDP_SHEET;
    }

    private static boolean isInterfaceDownRow(Map<String, Object> row) {
        for (Object value : row.values()) {
            if (value != null
                    && String.valueOf(value).toUpperCase(Locale.ROOT).contains("INTERFACE_DOWN")) {
                return true;
            }
        }
        return false;
    }
}
