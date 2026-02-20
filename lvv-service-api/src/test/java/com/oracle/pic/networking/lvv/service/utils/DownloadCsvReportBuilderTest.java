package com.oracle.pic.networking.lvv.service.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DownloadCsvReportBuilderTest {

    @Test
    void buildMultiTableCsv_rendersPerDeviceWithExpectedSpacingAndHeaders() {
        String rackSerial = "2546XV805A";

        // Build a structure that resembles the example JSON
        Map<String, Object> deviceR54 = new LinkedHashMap<>();
        // LLDP Errors list for r54
        List<Map<String, Object>> lldpR54 = new ArrayList<>();
        lldpR54.add(
                new LinkedHashMap<>(
                        Map.of(
                                "Device A Name", "mel22-q1-b2-t0-r54",
                                "Device B Port", "Ethernet39/1",
                                "Device B Rack", "0225",
                                "Expected Device B Name", "mel22-q1-b2-t1-r2",
                                "Device A Rack", "0213",
                                "Expected Device B Rack", "0225",
                                "Device B Name", "mel22-q1-b2-t1-r2",
                                "Device A Port", "Ethernet10/1",
                                "LLDP Status", "MISMATCH",
                                "Expected Device B Port", "Ethernet47/1")));
        lldpR54.add(
                new LinkedHashMap<>(
                        Map.of(
                                "Device A Name", "mel22-q1-b2-t0-r54",
                                "Device B Port", "Unknown",
                                "Device B Rack", "Unknown",
                                "Expected Device B Name", "mel22-q1-b2-t1-r4",
                                "Device A Rack", "0213",
                                "Expected Device B Rack", "0225",
                                "Device B Name", "Unknown",
                                "Device A Port", "Ethernet12/1",
                                "LLDP Status", "INTERFACE_DOWN",
                                "Expected Device B Port", "Ethernet47/1")));
        deviceR54.put("LLDP Errors", lldpR54);

        // Optic Errors for r54
        List<Map<String, Object>> opticR54 = new ArrayList<>();
        opticR54.add(
                new LinkedHashMap<>(
                        Map.of(
                                "Device Port", "Ethernet12/1",
                                "Rx Power", "-30.0",
                                "Device Name", "mel22-q1-b2-t0-r54",
                                "Tx Power", "1.46")));
        opticR54.add(
                new LinkedHashMap<>(
                        Map.of(
                                "Device Port", "Ethernet12/1",
                                "Rx Power", "-30.0",
                                "Device Name", "mel22-q1-b2-t0-r54",
                                "Tx Power", "1.46")));
        deviceR54.put("Optic Errors", opticR54);

        Map<String, Object> deviceR53 = new LinkedHashMap<>();
        List<Map<String, Object>> lldpR53 = new ArrayList<>();
        lldpR53.add(
                new LinkedHashMap<>(
                        Map.of(
                                "Device A Name", "mel22-q1-b2-t0-r53",
                                "Device B Port", "Ethernet47/1",
                                "Device B Rack", "0225",
                                "Expected Device B Name", "mel22-q1-b2-t1-r2",
                                "Device A Rack", "0213",
                                "Expected Device B Rack", "0225",
                                "Device B Name", "mel22-q1-b2-t1-r2",
                                "Device A Port", "Ethernet10/1",
                                "LLDP Status", "MISMATCH",
                                "Expected Device B Port", "Ethernet39/1")));
        deviceR53.put("LLDP Errors", lldpR53);

        Map<String, Object> rackMap = new LinkedHashMap<>();
        rackMap.put("mel22-c1-b2-t0-r51", new HashMap<>());
        rackMap.put("mel22-q1-b2-t0-r54", deviceR54);
        rackMap.put("mel22-q1-b2-t0-r53", deviceR53);

        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put(rackSerial, rackMap);

        String csv = DownloadCsvReportBuilder.buildMultiTableCsv(wrapper, rackSerial);

        assertNotNull(csv);
        // Error type headers exist
        assertTrue(csv.contains("LLDP Errors"));
        assertTrue(csv.contains("Optic Errors"));
        // Device sections exist
        assertTrue(csv.contains("Device : mel22-q1-b2-t0-r54\n\n"));
        assertTrue(csv.contains("\n\nDevice : mel22-q1-b2-t0-r53"));
        // Devices with no errors should not be shown
        assertFalse(csv.contains("Device : mel22-c1-b2-t0-r51"));
        // Headers: ensure current-device name columns are not displayed
        assertFalse(csv.contains("Device A Name"));
        assertFalse(csv.contains("Device Name"));
        // Some expected remaining header is present
        assertTrue(csv.contains("Device Port"));
        // There is a blank line between error-type sections within a device
        assertTrue(csv.contains("\n\nOptic Errors"));
    }

    @Test
    void buildMultiTableCsv_powerErrorsShowsStatusOnly() {
        String rackSerial = "RACK123";

        Map<String, Object> device = new LinkedHashMap<>();
        // Even if rows contain data, we should only render the status line
        List<Map<String, Object>> powerRows = new ArrayList<>();
        powerRows.add(new LinkedHashMap<>(Map.of("Some Key", "Some Value")));
        device.put("Power Errors", powerRows);

        Map<String, Object> rackMap = new LinkedHashMap<>();
        rackMap.put("phx14-c1-b92-t0-r10", device);

        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put(rackSerial, rackMap);

        String csv = DownloadCsvReportBuilder.buildMultiTableCsv(wrapper, rackSerial);

        assertNotNull(csv);
        assertTrue(csv.contains("Device : phx14-c1-b92-t0-r10"));
        assertTrue(csv.contains("Power Errors"));
        // Next line after the header should be exactly the status line, without a CSV header row
        String[] lines = csv.split("\n");
        boolean verified = false;
        for (int i = 0; i < lines.length - 1; i++) {
            if ("Power Errors".equals(lines[i])) {
                assertEquals("PSU status Down", lines[i + 1]);
                verified = true;
                break;
            }
        }
        assertTrue(verified, "Power Errors section did not render expected status line");
    }
}
