package com.euphoriapatches.euphoria_patcher.util.shader;

import com.euphoriapatches.euphoria_patcher.io.ArchiveOperations;
import com.euphoriapatches.euphoria_patcher.io.FileOperations;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the version of a shaderpack from the shaders/pack.json inside it. Works for both folders and zip files.
 */
public final class PackVersionReader {
    private static final String PACK_JSON_LOCATION = "shaders/pack.json";
    private static final Pattern VERSION_PATTERN = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"");

    // Static cache of the versions read from pack.json, so repeated reads of the same shaderpack cost nothing.
    // Uses Optional to distinguish between "not checked" (not in map), "no version" (Optional.empty()), and "version found" (Optional.of(version))
    private static final Map<Path, Optional<String>> versionCache = new ConcurrentHashMap<>();

    private PackVersionReader() {
    }

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[PackVersionReader] " + message);
    }

    /**
     * Reads the version from pack.json in a shader directory or zip file
     * Uses static cache to avoid redundant reads
     * @param shaderPath Path to shader (directory or zip file)
     * @return Version string from pack.json, or null if not found
     */
    public static String readVersion(Path shaderPath) {
        if (shaderPath == null) {
            debugLog("readVersion called with null path, returning null");
            return null;
        }

        // Check cache first
        Optional<String> cached = versionCache.get(shaderPath);
        if (cached != null) {
            debugLog("Version cache hit for " + shaderPath.getFileName() + ": " + cached.orElse("null"));
            return cached.orElse(null);
        }

        // Not in cache, perform the read
        String version = null;
        Path packJsonPath;

        try {
            if (Files.isDirectory(shaderPath)) {
                // For directories, read directly from pack.json
                packJsonPath = shaderPath.resolve(PACK_JSON_LOCATION);
                if (Files.exists(packJsonPath)) {
                    version = parseVersion(FileOperations.readFileAsString(packJsonPath));
                    debugLog("Read version from directory " + shaderPath.getFileName() + ": " + version);
                } else {
                    debugLog("pack.json not found at " + packJsonPath);
                }
            } else if (Files.isRegularFile(shaderPath) && shaderPath.toString().endsWith(".zip")) {
                // For zip files, read from archive
                String packJsonContent = ArchiveOperations.readFileFromZip(shaderPath, PACK_JSON_LOCATION);
                if (packJsonContent != null) {
                    version = parseVersion(packJsonContent);
                    debugLog("Read version from zip " + shaderPath.getFileName() + ": " + version);
                } else {
                    debugLog("pack.json not found in zip " + shaderPath.getFileName());
                }
            }
        } catch (Exception e) {
            debugLog("Error reading version from pack.json: " + e.getMessage());
        }

        // Cache the result (including null)
        versionCache.put(shaderPath, Optional.ofNullable(version));
        return version;
    }

    /**
     * Parses the version string from pack.json content
     * @return The version, or null if the content is null or has no version
     */
    static String parseVersion(String jsonContent) {
        if (jsonContent == null) {
            return null;
        }
        Matcher matcher = VERSION_PATTERN.matcher(jsonContent);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
