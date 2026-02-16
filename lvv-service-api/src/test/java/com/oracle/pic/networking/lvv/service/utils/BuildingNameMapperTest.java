package com.oracle.pic.networking.lvv.service.utils;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BuildingNameMapperTest {

    @Nested
    @DisplayName("getCanonicalNameOrOriginal")
    class GetCanonicalNameOrOriginal {

        @Test
        @DisplayName("returns null for null input")
        void returnsNullForNull() {
            assertNull(BuildingNameMapper.getCanonicalNameOrOriginal(null));
        }

        @Test
        @DisplayName("returns empty string for blank input after trim")
        void returnsEmptyForBlank() {
            assertEquals("", BuildingNameMapper.getCanonicalNameOrOriginal("   "));
        }

        @Test
        @DisplayName("returns canonical for exact mapped legacy (lowercase)")
        void returnsCanonicalForExactMapped() {
            // Map contains: phoenix -> phx1
            assertEquals("phx1", BuildingNameMapper.getCanonicalNameOrOriginal("phoenix"));
        }

        @Test
        @DisplayName("lookup is case-insensitive for keys")
        void caseInsensitiveLookup() {
            // Map contains: fra-1 -> fra1
            assertEquals("fra1", BuildingNameMapper.getCanonicalNameOrOriginal("FRA-1"));
            // Map contains: us-ashburn-1-1 -> iad1
            assertEquals("iad1", BuildingNameMapper.getCanonicalNameOrOriginal("Us-Ashburn-1-1"));
        }

        @Test
        @DisplayName("output canonical value is normalized to lowercase")
        void outputLowercased() {
            // Map contains: iad69old -> iad69new (already lowercase), use uppercase input
            assertEquals("phx1", BuildingNameMapper.getCanonicalNameOrOriginal("PhOeNiX"));
        }

        @Test
        @DisplayName("returns original trimmed when no mapping exists")
        void returnsOriginalWhenNoMapping() {
            assertEquals(
                    "unknown-bldg",
                    BuildingNameMapper.getCanonicalNameOrOriginal(" unknown-bldg "));
        }

        @Test
        @DisplayName("trims input before lookup")
        void trimsInput() {
            assertEquals("phx1", BuildingNameMapper.getCanonicalNameOrOriginal("  phoenix  "));
        }
    }
}
