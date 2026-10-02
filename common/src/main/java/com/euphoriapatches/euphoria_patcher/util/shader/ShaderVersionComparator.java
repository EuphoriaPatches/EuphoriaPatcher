package com.euphoriapatches.euphoria_patcher.util.shader;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.VersionComparator;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for shader version comparison and extraction
 */
public class ShaderVersionComparator {
    private final String brandName;
    private final String patchName;
    private final String version;
    private final Path shaderpacks;
    private ShaderVersionComparator instance;

    // Pattern for general version format
    public static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

    // Pattern for Complementary shader version extraction
    private static final Pattern COMPLEMENTARY_VERSION_PATTERN = Pattern.compile("_r(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    public ShaderVersionComparator(String brandName, String patchName, String version, Path shaderpacks) {
        this.brandName = brandName;
        this.patchName = patchName;
        this.version = version;
        this.shaderpacks = shaderpacks;
    }

    public ShaderVersionComparator getInstance() {
        if (instance == null) {
            instance = new ShaderVersionComparator(brandName, patchName, version, shaderpacks);
        }
        return instance;
    }
    /**
     * Checks if the given filename represents a newer version of the shader than what's expected
     * @param fileName The filename to check
     * @return true if it's a newer version, false otherwise
     */
    public boolean isNewerShaderVersion(String fileName) {
        // First check if it's a Complementary shader
        if (!fileName.contains(brandName)) {
            return false;
        }

        // Extract version numbers using regex
        int[] fileVersion = extractComplementaryVersionNumbers(fileName);
        int[] targetVersion = extractComplementaryVersionNumbers(version);

        // Compare versions using generic comparator
        return VersionComparator.compareVersionArrays(fileVersion, targetVersion) > 0;
    }

    /**
     * Extract version string from a filename
     * @param fileName The filename to extract from
     * @return Version string in format "r5.1" or "r5.3.2"
     */
    public String getComplementaryVersionFromFileName(String fileName) {
        int[] versionNumbers = extractComplementaryVersionNumbers(fileName);
        StringBuilder sb = new StringBuilder("r").append(versionNumbers[0]).append(".").append(versionNumbers[1]);
        if (versionNumbers[2] > 0) {
            sb.append(".").append(versionNumbers[2]);
        }
        return sb.toString();
    }

    /**
     * Extract version numbers from a filename
     * @return int array with [major, minor, patch]
     */
    public int[] extractComplementaryVersionNumbers(String filename) {
        int[] version = {0, 0, 0};

        // Extract r-version number (e.g., _r5.1 or _r5.3.2)
        Matcher matcher = COMPLEMENTARY_VERSION_PATTERN.matcher(filename);

        if (matcher.find()) {
            version[0] = Integer.parseInt(matcher.group(1));  // Major
            version[1] = Integer.parseInt(matcher.group(2));  // Minor
            version[2] = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;  // Patch
        }

        return version;
    }

    /**
     * Converts a shader loader version string to integer representation
     * @param versionString Version in format "X.Y.Z" (e.g., "1.8.8" or "2.0.1")
     * @return Integer representation (e.g., 10808 for 1.8.8, 20001 for 2.0.1)
     */
    public static int convertVersionNumberToInt(String versionString) {
        Matcher matcher = VERSION_PATTERN.matcher(versionString);
        if (!matcher.matches()) {
            debugLog("Invalid version format: " + versionString);
            return 0;
        }

        // Extract parts from X.Y.Z format
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = Integer.parseInt(matcher.group(3));

        // Convert to format XYYPP (major * 10000 + minor * 100 + patch)
        return (major * 10000) + (minor * 100) + patch;
    }

    /**
     * Finds a newer version of Complementary shader in the shaderpacks folder
     * @return Path to the newer version, or null if none found
     */
    public Path findNewerComplementaryVersion() {
        try {
            if (shaderpacks == null || !Files.exists(shaderpacks)) {
                return null;
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks)) {
                for (Path path : stream) {
                    String fileName = path.getFileName().toString();
                    if (fileName.contains(brandName) &&
                        !fileName.contains(patchName) &&
                        !isTestOrDevVersion(fileName) &&
                        isNewerShaderVersion(fileName)) {
                        boolean isFile = Files.isRegularFile(path) && fileName.endsWith(".zip");
                        boolean isDir = Files.isDirectory(path);
                        if (isFile || isDir) {
                            return path;
                        }
                    }
                }
            }
        } catch (Exception e) {
            debugLog("Error checking for newer shader versions: " + e.getMessage());
        }
        return null;
    }

    /**
     * Finds the highest version of any older Complementary shader
     * @return Path to the highest version file/directory, or null if none found
     */
    public Path findHighestOlderComplementaryVersion() {
        Path highestVersionPath = null;
        int[] highestVersion = {0, 0, 0}; // major, minor, patch

        try {
            // Check files
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks)) {
                for (Path path : stream) {
                    if (isOlderComplementaryShader(path, Files.isRegularFile(path) && path.toString().endsWith(".zip"))) {
                        int[] version = extractComplementaryVersionNumbers(path.getFileName().toString());
                        if (VersionComparator.compareVersionArrays(version, highestVersion) > 0) {
                            highestVersion = version;
                            highestVersionPath = path;
                        }
                    }
                }
            }
        } catch (IOException e) {
            debugLog("Error checking for older shader versions: " + e.getMessage());
        }

        return highestVersionPath;
    }

    /** Finds path of highest dev version but only if the user has no comp release
     * @return Path to the highest dev version, or null if there is none or a release is also present
     */
    public Path findDevOnlyComplementaryVersion() {
        Path highestDevPath = null;
        int[] highestDevVersion = null;

        try {
            if (shaderpacks == null || !Files.exists(shaderpacks)) {
                return null;
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks)) {
                for (Path path : stream) {
                    String name = path.getFileName().toString();
                    if (!name.contains(brandName) || name.contains(patchName)) {
                        continue;
                    }
                    boolean isFile = Files.isRegularFile(path) && name.endsWith(".zip");
                    if (!isFile && !Files.isDirectory(path)) {
                        continue;
                    }

                    if (!isTestOrDevVersion(name)) {
                        if (name.matches(".*_r\\d+\\.\\d+(?:\\.\\d+)?.*")) {
                            debugLog("Found release version " + name + ", not a dev-only setup");
                            return null;
                        }
                        continue;
                    }

                    int[] devVersion = extractComplementaryVersionNumbers(name);
                    if (highestDevVersion == null || VersionComparator.compareVersionArrays(devVersion, highestDevVersion) > 0) {
                        highestDevVersion = devVersion;
                        highestDevPath = path;
                    }
                }
            }
        } catch (IOException e) {
            debugLog("Error checking for dev shader versions: " + e.getMessage());
        }

        return highestDevPath;
    }

    /**
     * Checks if a path is an older version of Complementary shader
     */
    public boolean isOlderComplementaryShader(Path path, boolean isFile) {
        String name = path.getFileName().toString();

        // First check if it's a Complementary shader without the patch
        boolean isComplementary = name.contains(brandName) &&
                                 name.matches(".*_r\\d+\\.\\d+(?:\\.\\d+)?.*") &&
                                 !name.contains(patchName) &&
                                 !isTestOrDevVersion(name);

        if (isComplementary) {
            // Extract version numbers and compare
            int[] fileVersion = extractComplementaryVersionNumbers(name);
            int[] targetVersion = extractComplementaryVersionNumbers(version);

            // Only consider it "older" if the version is actually lower
            boolean isOlder = VersionComparator.compareVersionArrays(fileVersion, targetVersion) < 0;

            return isOlder && (isFile ? name.endsWith(".zip") : Files.isDirectory(path));
        }

        return false;
    }

    /**
     * Validates if a shader file/directory name matches the expected version format
     * @return true if it matches the expected brand, version format, and is not a test/dev version
     */
    public static boolean isTestOrDevVersion(String fileName) {
        String fileNameLower = fileName.toLowerCase(Locale.ROOT);
        return fileNameLower.contains("test") || fileNameLower.contains("fix") ||
                fileNameLower.contains("dev") || fileNameLower.contains("pre");
    }

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[ShaderVersionComparator] " + message);
    }
}
