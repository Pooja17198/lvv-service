package com.oracle.pic.networking.lvv.service.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class DownloadExcelReportBuilderTest {

    @Test
    void buildWorkbook_createsAllSheetsEvenWhenEmpty() throws Exception {
        byte[] bytes = DownloadExcelReportBuilder.buildWorkbook(Map.of("RACK1", Map.of()), "RACK1");

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertNotNull(workbook.getSheet("Summary"));
            assertNull(workbook.getSheet("LLDP Mismatch + Link Down"));
            assertNull(workbook.getSheet("Optic Errors"));
            assertNull(workbook.getSheet("FEC_BER Errors"));
            assertNull(workbook.getSheet("Power Errors"));
            assertNull(workbook.getSheet("Interface Down Errors"));
            assertNull(workbook.getSheet("Fan Errors"));
        }
    }

    @Test
    void buildWorkbook_populatesCategorySheetsAndSummary() throws Exception {
        Map<String, Object> device = new LinkedHashMap<>();
        device.put(
                "LLDP Errors",
                List.of(
                        new LinkedHashMap<>(
                                Map.of(
                                        "LLDP Status", "MISMATCH",
                                        "Device A Port", "Eth1/1",
                                        "Expected Device B Port", "Eth1/2")),
                        new LinkedHashMap<>(
                                Map.of(
                                        "LLDP Status", "INTERFACE_DOWN",
                                        "Device A Port", "Eth1/3",
                                        "Expected Device B Port", "Eth1/4"))));
        device.put(
                "Optic Errors",
                List.of(new LinkedHashMap<>(Map.of("Device Port", "Eth2/1", "Rx Power", "-20"))));
        device.put(
                "FEC_BER Errors",
                List.of(new LinkedHashMap<>(Map.of("Port", "Eth3/1", "Lock Status", "false"))));
        device.put("Power Errors", List.of(new LinkedHashMap<>(Map.of("ignored", "ignored"))));
        device.put(
                "Fan Errors",
                List.of(new LinkedHashMap<>(Map.of("Fan Name", "FanA", "Fan Slot", "1"))));

        Map<String, Object> wrapper = Map.of("RACK1", Map.of("device-1", device));

        byte[] bytes = DownloadExcelReportBuilder.buildWorkbook(wrapper, "RACK1");

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(1, workbook.getSheet("LLDP Mismatch + Link Down").getLastRowNum());
            assertEquals(1, workbook.getSheet("Interface Down Errors").getLastRowNum());
            assertEquals(1, workbook.getSheet("Optic Errors").getLastRowNum());
            assertEquals(1, workbook.getSheet("FEC_BER Errors").getLastRowNum());
            assertEquals(1, workbook.getSheet("Power Errors").getLastRowNum());
            assertEquals(1, workbook.getSheet("Fan Errors").getLastRowNum());

            assertEquals(
                    "Error Category",
                    workbook.getSheet("Summary").getRow(0).getCell(0).getStringCellValue());
            assertEquals(
                    "LLDP Mismatch + Link Down",
                    workbook.getSheet("Summary").getRow(1).getCell(0).getStringCellValue());
        }
    }

    @Test
    void buildWorkbook_routesInterfaceDownRowsAndSummaryCountsCorrectly() throws Exception {
        Map<String, Object> device = new LinkedHashMap<>();
        device.put(
                "LLDP Errors",
                List.of(
                        new LinkedHashMap<>(
                                Map.of(
                                        "LLDP Status", "MISMATCH",
                                        "Device A Port", "Eth1/1",
                                        "Expected Device B Port", "Eth1/2")),
                        new LinkedHashMap<>(
                                Map.of(
                                        "LLDP Status", "INTERFACE_DOWN",
                                        "Device A Port", "Eth1/3",
                                        "Expected Device B Port", "Eth1/4"))));

        byte[] bytes =
                DownloadExcelReportBuilder.buildWorkbook(
                        Map.of("RACK1", Map.of("device-1", device)), "RACK1");

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet lldpSheet = workbook.getSheet("LLDP Mismatch + Link Down");
            Sheet interfaceSheet = workbook.getSheet("Interface Down Errors");
            Sheet summarySheet = workbook.getSheet("Summary");

            assertEquals(1, lldpSheet.getLastRowNum());
            assertEquals(1, interfaceSheet.getLastRowNum());
            assertTrue(rowContains(lldpSheet.getRow(0), "LLDP Status"));
            assertTrue(rowContains(interfaceSheet.getRow(0), "LLDP Status"));
            assertEquals("MISMATCH", findValueForHeader(lldpSheet, 1, "LLDP Status"));
            assertEquals("INTERFACE_DOWN", findValueForHeader(interfaceSheet, 1, "LLDP Status"));

            assertEquals("1", findSummaryCount(summarySheet, "LLDP Mismatch + Link Down"));
            assertEquals("1", findSummaryCount(summarySheet, "Interface Down Errors"));
        }
    }

    @Test
    void buildWorkbook_addsSourceDeviceAsFirstHeaderColumn() throws Exception {
        Map<String, Object> deviceOne = new LinkedHashMap<>();
        deviceOne.put(
                "Optic Errors",
                List.of(new LinkedHashMap<>(Map.of("Device Port", "Eth1/1", "Rx Power", "-20"))));

        Map<String, Object> deviceTwo = new LinkedHashMap<>();
        deviceTwo.put(
                "Optic Errors",
                List.of(new LinkedHashMap<>(Map.of("Device Port", "Eth2/1", "Rx Power", "-21"))));

        byte[] bytes =
                DownloadExcelReportBuilder.buildWorkbook(
                        Map.of("RACK1", Map.of("device-1", deviceOne, "device-2", deviceTwo)),
                        "RACK1");

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet opticSheet = workbook.getSheet("Optic Errors");
            Row headerRow = opticSheet.getRow(0);

            assertEquals("Source Device", headerRow.getCell(0).getStringCellValue());
            Set<String> sourceDevices = new HashSet<>();
            sourceDevices.add(opticSheet.getRow(1).getCell(0).getStringCellValue());
            sourceDevices.add(opticSheet.getRow(2).getCell(0).getStringCellValue());

            assertEquals(Set.of("device-1", "device-2"), sourceDevices);
        }
    }

    @Test
    void buildWorkbook_excludesDeviceNameHeadersFromSheets() throws Exception {
        Map<String, Object> device = new LinkedHashMap<>();
        device.put(
                "LLDP Errors",
                List.of(
                        new LinkedHashMap<>(
                                Map.of(
                                        "Device A Name", "device-1",
                                        "LLDP Status", "MISMATCH",
                                        "Device A Port", "Eth1/1",
                                        "Expected Device B Port", "Eth1/2"))));
        device.put(
                "Optic Errors",
                List.of(
                        new LinkedHashMap<>(
                                Map.of(
                                        "Device Name", "device-1",
                                        "Device Port", "Eth2/1",
                                        "Rx Power", "-20"))));

        byte[] bytes =
                DownloadExcelReportBuilder.buildWorkbook(
                        Map.of("RACK1", Map.of("device-1", device)), "RACK1");

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertFalse(
                    rowContains(
                            workbook.getSheet("LLDP Mismatch + Link Down").getRow(0),
                            "Device A Name"));
            assertFalse(rowContains(workbook.getSheet("Optic Errors").getRow(0), "Device Name"));
        }
    }

    @Test
    void buildWorkbook_powerErrorsUseStatusOnly() throws Exception {
        Map<String, Object> device = new LinkedHashMap<>();
        device.put(
                "Power Errors",
                List.of(new LinkedHashMap<>(Map.of("ignored", "value", "another", "field"))));

        byte[] bytes =
                DownloadExcelReportBuilder.buildWorkbook(
                        Map.of("RACK1", Map.of("device-1", device)), "RACK1");

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet powerSheet = workbook.getSheet("Power Errors");
            assertEquals("Status", powerSheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("PSU status Down", powerSheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals(0, powerSheet.getRow(0).getLastCellNum() - 1);
        }
    }

    @Test
    void buildWorkbook_usesFirstRackWhenRequestedRackIsMissing() throws Exception {
        Map<String, Object> device = new LinkedHashMap<>();
        device.put(
                "Fan Errors",
                List.of(new LinkedHashMap<>(Map.of("Fan Name", "FanA", "Fan Slot", "1"))));

        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("RACK1", Map.of("device-1", device));
        wrapper.put("RACK2", Map.of("device-2", Map.of()));

        byte[] bytes = DownloadExcelReportBuilder.buildWorkbook(wrapper, "UNKNOWN_RACK");

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet fanSheet = workbook.getSheet("Fan Errors");
            assertEquals("device-1", fanSheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("FanA", findValueForHeader(fanSheet, 1, "Fan Name"));
        }
    }

    @Test
    void buildWorkbook_toleratesNullAndMalformedInput() throws Exception {
        byte[] nullBytes = DownloadExcelReportBuilder.buildWorkbook(null, "RACK1");
        byte[] malformedBytes = DownloadExcelReportBuilder.buildWorkbook(List.of("bad"), "RACK1");

        try (XSSFWorkbook nullWorkbook = new XSSFWorkbook(new ByteArrayInputStream(nullBytes));
                XSSFWorkbook malformedWorkbook =
                        new XSSFWorkbook(new ByteArrayInputStream(malformedBytes))) {
            assertNotNull(nullWorkbook.getSheet("Summary"));
            assertNotNull(malformedWorkbook.getSheet("Summary"));
            assertNull(nullWorkbook.getSheet("LLDP Mismatch + Link Down"));
            assertNull(malformedWorkbook.getSheet("Optic Errors"));
        }
    }

    @Test
    void buildWorkbook_omitsCategorySheetsWhenTheyHaveNoErrors() throws Exception {
        Map<String, Object> device = new LinkedHashMap<>();
        device.put(
                "Optic Errors",
                List.of(new LinkedHashMap<>(Map.of("Device Port", "Eth2/1", "Rx Power", "-20"))));

        byte[] bytes =
                DownloadExcelReportBuilder.buildWorkbook(
                        Map.of("RACK1", Map.of("device-1", device)), "RACK1");

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertNotNull(workbook.getSheet("Summary"));
            assertNotNull(workbook.getSheet("Optic Errors"));
            assertNull(workbook.getSheet("LLDP Mismatch + Link Down"));
            assertNull(workbook.getSheet("FEC_BER Errors"));
            assertNull(workbook.getSheet("Power Errors"));
            assertNull(workbook.getSheet("Interface Down Errors"));
            assertNull(workbook.getSheet("Fan Errors"));
            assertEquals("0", findSummaryCount(workbook.getSheet("Summary"), "Fan Errors"));
            assertEquals("1", findSummaryCount(workbook.getSheet("Summary"), "Optic Errors"));
        }
    }

    private static boolean rowContains(Row row, String expectedValue) {
        for (int i = 0; i < row.getLastCellNum(); i++) {
            if (expectedValue.equals(row.getCell(i).getStringCellValue())) {
                return true;
            }
        }
        return false;
    }

    private static String findSummaryCount(Sheet summarySheet, String category) {
        for (int i = 1; i <= summarySheet.getLastRowNum(); i++) {
            Row row = summarySheet.getRow(i);
            if (category.equals(row.getCell(0).getStringCellValue())) {
                return row.getCell(1).getStringCellValue();
            }
        }
        fail("Could not find summary row for category: " + category);
        return null;
    }

    private static String findValueForHeader(Sheet sheet, int rowIndex, String headerName) {
        Row headerRow = sheet.getRow(0);
        Row dataRow = sheet.getRow(rowIndex);
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            if (headerName.equals(headerRow.getCell(i).getStringCellValue())) {
                return dataRow.getCell(i).getStringCellValue();
            }
        }
        fail("Could not find header: " + headerName);
        return null;
    }
}
