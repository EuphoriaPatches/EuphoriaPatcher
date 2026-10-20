package com.euphoriapatches.euphoria_patcher.services;

import com.euphoriapatches.euphoria_patcher.io.ArchiveOperations;
import com.euphoriapatches.euphoria_patcher.util.UserPersistentData;
import com.euphoriapatches.euphoria_patcher.util.shader.ShaderPropertyReader;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.VersionComparator;
import org.apache.commons.io.FileUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Handles shader naming operations including renaming and creating alternative names
 */
public class ShaderNamingService {
    private final String brandName;
    @SuppressWarnings("unused")
    private final String patchName;
    private final String version;
    private final String patchVersion;
    private final String commonLocation;
    private final String shaderMyFileLocation;
    private final Path shaderpacks;
    private final ShaderDetector shaderDetector;

    public ShaderNamingService(String brandName, String patchName, String version, String patchVersion,
                               String commonLocation, String shaderMyFileLocation, Path shaderpacks,
                               ShaderDetector shaderDetector) {
        this.brandName = brandName;
        this.patchName = patchName;
        this.version = version;
        this.patchVersion = patchVersion;
        this.commonLocation = commonLocation;
        this.shaderMyFileLocation = shaderMyFileLocation;
        this.shaderpacks = shaderpacks;
        this.shaderDetector = shaderDetector;
    }

    /**
     * Rename shader to correct naming format
     */
    public Path renameToCorrectShaderName(Path path) {
        try {
            String fileName = path.getFileName().toString();
            String style;

            // First try to determine style from filename
            if (fileName.contains("Unbound")) {
                style = "Unbound";
            } else if (fileName.contains("Reimagined")) {
                style = "Reimagined";
            } else {
                // If not in filename, check the common.glsl file
                style = ShaderPropertyReader.detectStyleFromCommonFile(path, commonLocation);
                debugLog("Detected " + style + " style from common.glsl file");
            }

            // Create the correct name format
            String correctName = brandName + style + version;
            if (fileName.endsWith(".zip")) {
                correctName += ".zip";
            }

            // If the name is already correct, return the original path
            if (fileName.equals(correctName)) {
                return path;
            }

            // Create path for the renamed shader
            Path targetPath = path.resolveSibling(correctName);

            // Skip if a file with the target name already exists
            if (Files.exists(targetPath)) {
                debugLog("A file with the correct name already exists: " + targetPath.getFileName());
                return path;
            }

            // Rename the file/directory
            Path renamedPath = Files.move(path, targetPath);
            log(0, "Renamed shader from \"" + fileName + "\" to \"" + correctName + "\"");

            return renamedPath;
        } catch (IOException e) {
            log(2, "Failed to rename shader: " + e.getMessage());
            return path; // Return original path if renaming failed
        }
    }

    /**
     * Create alternative shader names as configured
     */
    public void createAlternativeShaderNames(Path patchedShaderPath, boolean isAlreadyInstalled, String alternativeShaderNames) {
        debugLog("createAlternativeShaderNames called with isAlreadyInstalled: " + isAlreadyInstalled);
        String baseVersion = version.replace("_", "");
        String patchVersionClean = patchVersion.replace("_", "");

        // Define illegal characters for file/folder names on most OSes
        String illegalChars = "[\\\\/:*?\"<>|]";

        String[] alternativeNames = alternativeShaderNames.isEmpty() ? new String[0] : alternativeShaderNames.split(",");
        java.util.Set<String> configuredAlternativeNames = new java.util.TreeSet<>();

        for (String name : alternativeNames) {
            String trimmedName = name.trim();

            if (trimmedName.isEmpty()) {
                continue; // Skip empty names
            }

            // Replace placeholders with actual version values
            String finalName = trimmedName
                    .replace("{baseVersion}", baseVersion)
                    .replace("{patchVersion}", patchVersionClean);

            if (finalName.matches(".*" + illegalChars + ".*")) {
                log(2, "Skipping alternative shader name with illegal characters: \"" + finalName + "\"");
                continue;
            }

            configuredAlternativeNames.add(finalName);

            if (!UserPersistentData.hasKnownAlternativeShaderName(finalName)) {
                debugLog("Alternative shader \"" + finalName + "\" has not been created before, creating it now.");
                createShaderCopy(patchedShaderPath, finalName);
                continue;
            }

            // Get the target path
            Path targetPath = shaderpacks.resolve(finalName);

            // If EP is already installed, only regenerate if the alternative exists and is corrupted
            if (isAlreadyInstalled) {
                if (Files.exists(targetPath)) {
                    try {
                        boolean isCorrupted = !shaderDetector.hasEuphoriaFile(targetPath);
                        if (isCorrupted) {
                            debugLog("Found corrupted alternative shader \"" + finalName + "\", regenerating...");
                            ArchiveOperations.deleteRecursively(targetPath); // Delete the corrupted version
                            createShaderCopy(patchedShaderPath, finalName); // Create a new copy - patchedShaderPath should be safe since EP is installed
                        } else {
                            UserPersistentData.rememberAlternativeShaderNames(finalName);
                            debugLog("Alternative shader \"" + finalName + "\" exists and is valid, skipping.");
                        }
                    } catch (IOException e) {
                        log(2, "Error verifying alternative shader \"" + finalName + "\": " + e.getMessage());
                    }
                } else {
                    if (UserPersistentData.hasKnownAlternativeShaderName(finalName)) {
                        debugLog("Alternative shader \"" + finalName + "\" was previously created but is now missing, assuming the user deleted it and skipping creation.");
                    } else {
                        debugLog("Alternative shader \"" + finalName + "\" has not been created before, creating it now.");
                        createShaderCopy(patchedShaderPath, finalName);
                    }
                }
            } else {
                // EP is not already installed, create alternative names normally
                createShaderCopy(patchedShaderPath, finalName);
            }
        }

        UserPersistentData.pruneAlternativeShaderNames(configuredAlternativeNames);

        if (configuredAlternativeNames.isEmpty()) {
            debugLog("No alternative shader names configured.");
        }
    }

    /**
     * Create a copy of a shader with a new name
     */
    public void createShaderCopy(Path sourceShaderPath, String newName) {
        try {
            // Get the parent directory (shaderpacks folder)
            Path shaderpacks = sourceShaderPath.getParent();

            // Create the new path with the alternative name
            Path targetPath = shaderpacks.resolve(newName);

            // Check if it already exists
            if (Files.exists(targetPath)) {
                // Check if it's an outdated version by examining myFile.glsl
                Path myFilePath = targetPath.resolve(shaderMyFileLocation);

                if (Files.exists(myFilePath)) {
                    // Read first line of the file to extract version
                    String firstLine;
                    try (BufferedReader reader = Files.newBufferedReader(myFilePath)) {
                        firstLine = reader.readLine();
                    }

                    // Check if it's a Euphoria Patches file with a different version
                    if (firstLine != null && firstLine.startsWith("// Euphoria Patches")) {
                        String fileVersion = firstLine.replace("// Euphoria Patches ", "").trim();
                        String expectedVersion = patchVersion.replace("_", "");

                        if (VersionComparator.isNewerVersion(expectedVersion, fileVersion)) {
                            debugLog("Found outdated alternative shader \"" + newName + "\" (version " + fileVersion + "), updating to " + expectedVersion);
                            // Delete outdated version
                            ArchiveOperations.deleteRecursively(targetPath);
                        } else {
                            // Version is current, skip creation
                            debugLog("Skipping creation of alternative shader name \"" + newName + "\" as it already exists with current version.");
                            return;
                        }
                    } else {
                        // Not a Euphoria Patches file or can't determine version
                        debugLog("Found existing shader with name \"" + newName + "\" but couldn't verify version, replacing it.");
                        ArchiveOperations.deleteRecursively(targetPath);
                    }
                } else {
                    // myFile.glsl doesn't exist, assume not a Euphoria shader or corrupted
                    debugLog("Found existing shader with name \"" + newName + "\" but it doesn't appear to be a valid Euphoria shader, replacing it.");
                    ArchiveOperations.deleteRecursively(targetPath);
                }
            }

            log(0, "Creating alternative shader names from: " + sourceShaderPath.getFileName());

            // Copy the directory
            debugLog("Creating alternative shader with name: \"" + newName + "\"");
            FileUtils.copyDirectory(sourceShaderPath.toFile(), targetPath.toFile());
            UserPersistentData.rememberAlternativeShaderNames(newName);

            log(0, "Successfully created alternative shader: \"" + newName + "\"");
        } catch (IOException e) {
            log(2, "Error creating alternative shader \"" + newName + "\": " + e.getMessage());
        }
    }

    private void log(int level, String message) {
        com.euphoriapatches.euphoria_patcher.EuphoriaPatcher.log(level, message);
    }

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[ShaderNamingService] " + message);
    }
}
