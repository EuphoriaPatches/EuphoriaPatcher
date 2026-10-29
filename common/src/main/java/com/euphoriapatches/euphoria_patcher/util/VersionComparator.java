package com.euphoriapatches.euphoria_patcher.util;

/**
 * Utility for comparing version strings and version number arrays
 */
public class VersionComparator {

    /**
     * Compare two version strings (e.g., "1.7.4" vs "1.7.3")
     * @param newVersion First version string
     * @param oldVersion Second version string
     * @return positive if newVersion > oldVersion, 0 if equal, negative if newVersion < oldVersion
     */
    public static int compareVersionStrings(String newVersion, String oldVersion) {
        String[] v1Parts = newVersion.split("\\.");
        String[] v2Parts = oldVersion.split("\\.");

        for (int i = 0; i < Math.min(v1Parts.length, v2Parts.length); i++) {
            int v1Part = Integer.parseInt(v1Parts[i]);
            int v2Part = Integer.parseInt(v2Parts[i]);

            if (v1Part != v2Part) {
                return v1Part - v2Part;
            }
        }

        // If all compared parts are equal, the longer version is considered newer
        // (e.g., "1.2.3" > "1.2")
        return v1Parts.length - v2Parts.length;
    }

    /**
     * Check if newVersion is newer than oldVersion
     * @param newVersion First version string
     * @param oldVersion Second version string
     * @return true if newVersion is newer than oldVersion
     */
    public static boolean isNewerVersion(String newVersion, String oldVersion) {
        return compareVersionStrings(newVersion, oldVersion) > 0;
    }

    /**
     * Check if newVersion is newer than or equal to oldVersion
     * @param newVersion First version string
     * @param oldVersion Second version string
     * @return true if newVersion is newer than or equal to oldVersion
     */
    public static boolean isNewerOrEqual(String newVersion, String oldVersion) {
        return compareVersionStrings(newVersion, oldVersion) >= 0;
    }

    /**
     * Splits a dotted version string into its numbers (e.g., "1.7.4" to [1, 7, 4]), without throwing on bad input.
     * @param version Version string with at least 2 numeric parts separated by dots
     * @return The numbers, or null if the version is null, has fewer than 2 parts, or has a part that is not a number
     */
    public static int[] parseVersionToIntArray(String version) {
        if (version == null) {
            return null;
        }
        String[] parts = version.split("\\.");
        int[] numbers = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                numbers[i] = Integer.parseInt(parts[i]);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return numbers.length >= 2 ? numbers : null;
    }

    /**
     * Compare two version arrays (e.g., [5, 3, 2] vs [5, 1, 0])
     * @param v1 First version array [major, minor, patch]
     * @param v2 Second version array [major, minor, patch]
     * @return positive if v1 > v2, 0 if equal, negative if v1 < v2
     */
    public static int compareVersionArrays(int[] v1, int[] v2) {
        int minLength = Math.min(v1.length, v2.length);

        for (int i = 0; i < minLength; i++) {
            if (v1[i] != v2[i]) {
                return v1[i] - v2[i];
            }
        }

        // If all compared parts are equal, the longer array is considered newer
        return v1.length - v2.length;
    }
}
