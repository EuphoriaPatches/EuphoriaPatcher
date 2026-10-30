package com.euphoriapatches.euphoria_patcher.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for comparing version strings and version number arrays
 */
public class VersionComparator {

    public static final Pattern PLAIN_VERSION = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

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
     * Check if a packed version (see {@link #toPackedInt(String)}) is newer than or equal to oldVersion
     * @param packedVersion A version packed into one integer, 0 stands for an unknown version
     * @param oldVersion The version to compare with, in the format "X.Y.Z"
     * @return true if packedVersion is newer than or equal to oldVersion
     * @throws IllegalArgumentException if oldVersion is not in the format "X.Y.Z"
     */
    public static boolean isNewerOrEqual(int packedVersion, String oldVersion) {
        if (oldVersion == null || !PLAIN_VERSION.matcher(oldVersion).matches()) {
            throw new IllegalArgumentException("Not a version of the format X.Y.Z: " + oldVersion);
        }
        return packedVersion >= toPackedInt(oldVersion);
    }

    /**
     * Packs a version of three numbers into one integer (major * 10000 + minor * 100 + patch), the way the version of a
     * shader loader is kept, e.g. "1.8.8" gives 10808 and "2.0.1" gives 20001. Minor and patch have to stay below 100.
     * @param version Version in the format "X.Y.Z"
     * @return The packed version, or 0 if the version is not exactly three numbers
     */
    public static int toPackedInt(String version) {
        if (version == null) {
            return 0;
        }
        Matcher matcher = PLAIN_VERSION.matcher(version);
        if (!matcher.matches()) {
            return 0;
        }
        return Integer.parseInt(matcher.group(1)) * 10000 + Integer.parseInt(matcher.group(2)) * 100 + Integer.parseInt(matcher.group(3));
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
