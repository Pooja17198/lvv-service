package com.oracle.pic.networking.lvv.service.utils;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BadLinksTitleParserTest {

    /**
     * Ensures BadLinksTitleParser.parse returns null when the input is null or consists only of
     * whitespace. Validates defensive handling of empty inputs to avoid false positives.
     */
    @Test
    void parse_returnsNull_onNullOrBlank() {
        assertNull(BadLinksTitleParser.parse(null));
        assertNull(BadLinksTitleParser.parse("   "));
    }

    /**
     * Parses a well-formed summary line and extracts device and remote_device tokens into a
     * two-element array [device, remoteDevice]. Verifies correct tokenization and ordering when the
     * expected pattern is present.
     */
    @Test
    void parse_extractsDeviceAndRemoteDevice_whenFormatMatches() {
        String summary =
                "INTERFACE DOWN device:iad8-c1-b19-t2-r10:Ethernet22/1 remote_device:iad8-c1-b19-t1-r11:Ethernet19/1";
        String[] out = BadLinksTitleParser.parse(summary);
        assertNotNull(out);
        assertEquals("iad8-c1-b19-t2-r10:Ethernet22/1", out[0]);
        assertEquals("iad8-c1-b19-t1-r11:Ethernet19/1", out[1]);
    }

    /**
     * Confirms parsing is case-insensitive and tolerant of leading/trailing noise. The tokens
     * DEVICE and REMOTE_DEVICE can appear in any case and not necessarily at the start.
     */
    @Test
    void parse_isCaseInsensitive_andAllowsPrefixNoise() {
        String summary = "Some PREFIX text DEVICE:devA/1 REMOTE_DEVICE:remB/2 trailing stuff";
        String[] out = BadLinksTitleParser.parse(summary);
        assertNotNull(out);
        assertEquals("devA/1", out[0]);
        assertEquals("remB/2", out[1]);
    }

    /**
     * Returns null when one or both required tokens are missing from the input. Ensures the parser
     * does not return partial or ambiguous results.
     */
    @Test
    void parse_returnsNull_whenPatternNotFound() {
        assertNull(BadLinksTitleParser.parse("no tokens here"));
        assertNull(BadLinksTitleParser.parse("deviceOnly device:devA"));
        assertNull(BadLinksTitleParser.parse("remoteOnly remote_device:remB"));
    }
}
