package com.oracle.pic.networking.lvv.service.utils;

import java.util.Locale;
import java.util.Map;

/**
 * Case-insensitive mapper from legacy building names to canonical building names.
 *
 * <p>Behavior:
 *
 * <ul>
 *   <li>Lookup trims input and is case-insensitive.
 *   <li>If no mapping exists (or mapped value is blank), returns the original input trimmed.
 *   <li>Canonical values are returned exactly as stored.
 * </ul>
 *
 * <p>Note: Store all keys normalized to lower-case to keep lookups consistent.
 *
 * <p>Use Case: Some of the initial buildings of OCI have different legacy and canonical names and
 * in the network monitoring i.e. BadLinksService.java we are fetching the tickets from jira for a
 * building and the canonical names are used in jira but in our project DB the building names will
 * be legacy names so when the /badLinks api request comes from frontend then the frontend sends the
 * legacy building name, and we fetch the corresponding canonical building names using this file
 * then use it to query jira
 */
public final class BuildingNameMapper {
    private BuildingNameMapper() {}

    /**
     * Returns the canonical building name for the given legacy name, or the original (trimmed)
     * value if there is no mapping (or the mapped value is blank).
     */
    public static String getCanonicalNameOrOriginal(String legacyName) {
        if (legacyName == null) {
            return null;
        }

        String legacyNameTrimmed = legacyName.trim();
        if (legacyNameTrimmed.isEmpty()) {
            return legacyNameTrimmed;
        }

        String key = legacyNameTrimmed.toLowerCase(Locale.ROOT);
        String canonicalName = LEGACY_TO_CANONICAL.get(key);

        return (canonicalName == null || canonicalName.isBlank())
                ? legacyNameTrimmed
                : canonicalName.toLowerCase(Locale.ROOT).trim();
    }

    // BOTH key AND value ARE CASE-INSENSITIVE(always lower case)
    private static final Map<String, String> LEGACY_TO_CANONICAL =
            Map.ofEntries(
                    // OC1 -> 14 entries
                    Map.entry("phoenix", "phx1"),
                    Map.entry("phoenix-2", "phx2"),
                    Map.entry("phoenix-3", "phx3"),
                    Map.entry("phoenix-4", "phx4"),
                    Map.entry("phoenix-5", "phx5"),
                    Map.entry("fra-1", "fra1"),
                    Map.entry("fra-2", "fra2"),
                    Map.entry("fra-3", "fra3"),
                    Map.entry("fra-4", "fra4"),
                    Map.entry("us-ashburn-1-1", "iad1"),
                    Map.entry("us-ashburn-1-2", "iad2"),
                    Map.entry("us-ashburn-1-3", "iad3"),
                    Map.entry("us-ashburn-1-4", "iad4"),
                    Map.entry("us-ashburn-1-5", "iad5"));
}
