package com.euphoriapatches.euphoria_patcher.features.shader_settings;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.integration.ShaderLoader;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.services.ShaderDetector;
import com.euphoriapatches.euphoria_patcher.util.VersionComparator;
import com.euphoriapatches.euphoria_patcher.util.shader.ShaderVersionComparator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateShaderConfig {
    private static final String EUPHORIA_IDENTIFIER_KEY = "AAA_THIS_IS_A_EUPHORIA_PATCHES_SETTINGS_FILE";
    private static final String VERSION_IDENTIFIER_PREFIX = "AAB_FOR_EUPHORIA_PATCHES_VERSION_";
    private static final String VERSION_IDENTIFIER_SUFFIX = "=true";
    private static final String EUPHORIA_IDENTIFIER = EUPHORIA_IDENTIFIER_KEY + VERSION_IDENTIFIER_SUFFIX;
    private static final Pattern EUPHORIA_FILE_PATTERN = Pattern.compile(
            "Comp\\d+(?:\\.\\d+)*[rdp]?\\d*EP_|.*(?:EuphoriaPatches|Euphoria-Patches)",
        Pattern.CASE_INSENSITIVE
    );
    private static final String TEMP_FILE_SUFFIX = ".ep-tmp";

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[UpdateShaderConfig] " + message);
    }

    private static ShaderDetector getShaderDetector() {
        EuphoriaPatcher instance = EuphoriaPatcher.getInstance();
        return instance != null ? instance.getShaderDetector() : null;
    }

    private static String removeTxtSuffix(String fileName) {
        return fileName.endsWith(".txt") ? fileName.substring(0, fileName.length() - ".txt".length()) : fileName;
    }

    private static String getVersionIdentifier() {
        // Convert PATCH_VERSION (like "_1.5.2") to format "1_5_2" for the identifier
        String version = EuphoriaPatcher.PATCH_VERSION;
        version = version.substring(1); // Remove leading underscore
        return createVersionIdentifier(version);
    }

    // Create version identifier from a version string
    static String createVersionIdentifier(String version) {
        // Convert version (like "1.5.2") to format "1_5_2" for the identifier
        String formattedVersion = version.replace(".", "_"); // Replace dots with underscores
        return VERSION_IDENTIFIER_PREFIX + formattedVersion + VERSION_IDENTIFIER_SUFFIX;
    }

    // Extract version from identifier line
    static String extractVersionFromIdentifier(String line) {
        if (line.startsWith(VERSION_IDENTIFIER_PREFIX) && line.endsWith(VERSION_IDENTIFIER_SUFFIX)) {
            String versionPart = line.substring(VERSION_IDENTIFIER_PREFIX.length(),
                                              line.length() - VERSION_IDENTIFIER_SUFFIX.length());
            return "_" + versionPart.replace("_", "."); // Convert back to PATCH_VERSION format
        }
        return null;
    }

    public static void updateShaderTxtConfigFile(boolean styleUnbound, boolean styleReimagined) {
        List<String> targetStyles = new ArrayList<>();
        if (styleReimagined) {
            targetStyles.add("Reimagined");
        }
        if (styleUnbound) {
            targetStyles.add("Unbound");
        }
        if (targetStyles.isEmpty()) {
            // Style could not be determined ;-;
            targetStyles.add("Reimagined");
        }

        // Only when the user is installing both styles do we need to resolve a source per style.
        // Otherwise we just take the newest config regardless of style.
        boolean perStyle = targetStyles.size() > 1;

        // Resolve every source first, then copy. Resolving as we go would let the file we just
        // created for the first style become the "newest config" picked for the second style.
        Map<String, Path> sourcesByStyle = new LinkedHashMap<>();
        for (String targetStyle : targetStyles) {
            Path sourceConfig = findSourceConfigForStyle(perStyle ? targetStyle : null);
            if (sourceConfig != null) {
                sourcesByStyle.put(targetStyle, sourceConfig);
            }
        }

        for (Map.Entry<String, Path> entry : sourcesByStyle.entrySet()) {
            doConfigFileCopy(entry.getValue(), entry.getKey());
        }

        // Add identifier to all Euphoria Patches settings files
        markAllEPSettingsFiles();
    }

    /**
     * Finds the newest config file for a target style, falling back to any style if no exact match is found.
     *
     * @param style Style filter ("Reimagined", "Unbound"), or {@code null} for the overall newest config.
     */
    private static Path findSourceConfigForStyle(String style) {
        if (style != null) {
            Path styleSpecific = openStreamAndFindConfig(style);
            if (styleSpecific != null) {
                return styleSpecific;
            }
            debugLog("No " + style + "-specific config found, falling back to newest config of any style");
        }
        return openStreamAndFindConfig(null);
    }

    private static Path openStreamAndFindConfig(String styleFilter) {
        try (DirectoryStream<Path> textStream = Files.newDirectoryStream(EuphoriaPatcher.shaderpacks,
                UpdateShaderConfig::isConfigFile)) {
            return findShaderConfigFile(textStream, true, styleFilter);
        } catch (IOException e) {
            EuphoriaPatcher.log(3, 0, "Error reading shaderpacks directory: " + e.getMessage());
            return null;
        }
    }

    /**
     * Marks all Euphoria Patches settings files in the shaderpacks directory
     */
    public static void markAllEPSettingsFiles() {
        try {
            ShaderDetector detector = getShaderDetector();

            // Old settings are only converted in the file of the shaderpack that is in use
            Path currentShaderpack = ShaderLoader.getCurrentShaderpackPath();
            Path currentSettingsFile = currentShaderpack == null ? null
                : EuphoriaPatcher.shaderpacks.resolve(currentShaderpack.getFileName() + ".txt");
            boolean convertCurrentSettings = SettingsConverterUtil.shouldConvertSettings(currentShaderpack);

            try (DirectoryStream<Path> configStream = Files.newDirectoryStream(EuphoriaPatcher.shaderpacks,
                    path -> Files.isRegularFile(path) && path.toString().endsWith(".txt"))) {

                for (Path configFile : configStream) {
                    String fileName = configFile.getFileName().toString();
                    // Check if matches any Euphoria Patches pattern
                    if (EUPHORIA_FILE_PATTERN.matcher(fileName).find()) {
                        String versionIdentifier = getVersionIdentifierForShader(fileName, detector);
                        debugLog("Identified settings file: " + fileName + " (version identifier: " +
                                (versionIdentifier != null ? versionIdentifier : "none") + ")");
                        addIdentifierToSettingsFile(configFile, versionIdentifier,
                            convertCurrentSettings && configFile.equals(currentSettingsFile));
                    }
                }
            }
        } catch (IOException e) {
            EuphoriaPatcher.log(2, 0, "Error marking settings files: " + e.getMessage());
        }
    }

    /**
     * Marks only the current shaderpack's settings file if it's an EP shader.
     * Uses ShaderLoader to get the current shaderpack and ShaderDetector to verify it's an EP shader.
     * Only used where Iris does not save the settings for us, see {@link #addIdentifiersToSavedSettings}.
     */
    public static void markCurrentEPSettingsFile() {
        try {
            Path currentShaderpack = ShaderLoader.getCurrentShaderpackPath();
            if (currentShaderpack == null) {
                debugLog("No current shaderpack selected");
                return;
            }

            ShaderDetector detector = getShaderDetector();
            if (detector == null || !detector.isEuphoriaPatchesShader(currentShaderpack)) {
                debugLog("Current shaderpack is not an EP shader: " + currentShaderpack.getFileName());
                return;
            }

            String configFileName = currentShaderpack.getFileName() + ".txt";
            Path configFile = EuphoriaPatcher.shaderpacks.resolve(configFileName);
            if (!Files.isRegularFile(configFile)) {
                debugLog("Config file does not exist: " + configFileName);
                return;
            }

            debugLog("Marking current EP settings file: " + configFileName);
            addIdentifierToSettingsFile(configFile, getVersionIdentifierForShader(configFileName, detector),
                SettingsConverterUtil.shouldConvertSettings(currentShaderpack));

        } catch (Exception e) {
            EuphoriaPatcher.log(2, 0, "Error marking current settings file: " + e.getMessage());
        }
    }

    /**
     * Determines the version identifier to use for a shader based on its actual version from pack.json
     * Only returns a version identifier if the shader version is >= current PATCH_VERSION
     * @param shaderName The name of the shader (without .txt extension for config files)
     * @param detector ShaderDetector instance, can be null
     * @return Version identifier string to add, or null if no version identifier should be added
     */
    private static String getVersionIdentifierForShader(String shaderName, ShaderDetector detector) {
        String baseShaderName = removeTxtSuffix(shaderName);

        String versionFromShader = readVersionFromShader(baseShaderName, detector);
        if (versionFromShader == null) {
            // Fallback to filename check
            return shaderName.contains(EuphoriaPatcher.PATCH_VERSION) ? getVersionIdentifier() : null;
        }

        // Compare with current PATCH_VERSION (remove leading underscore from PATCH_VERSION)
        String currentVersion = EuphoriaPatcher.PATCH_VERSION.substring(1); // e.g., "_1.8.3" -> "1.8.3"

        // Use VersionComparator to check if shader version >= current version
        int comparison = VersionComparator.compareVersionStrings(versionFromShader, currentVersion);

        debugLog("Version check for " + baseShaderName + ": pack.json=" + versionFromShader +
                ", current=" + currentVersion + ", comparison=" + comparison);

        // Only add version identifier if shader version >= current version
        if (comparison >= 0) {
            // Use the shader's actual version for the identifier
            String identifier = createVersionIdentifier(versionFromShader);
            debugLog("Will add version identifier: " + identifier);
            return identifier;
        } else {
            debugLog("Shader version is older than current version, not adding version identifier");
            return null;
        }
    }

    /**
     * @return the version from the pack.json of the shader pack, or {@code null} if it can't be read
     */
    private static String readVersionFromShader(String baseShaderName, ShaderDetector detector) {
        if (detector == null) {
            debugLog("ShaderDetector not available, falling back to filename check");
            return null;
        }

        // Try to find the corresponding shader pack (directory or .zip)
        Path shaderPath = EuphoriaPatcher.shaderpacks.resolve(baseShaderName);
        if (!Files.exists(shaderPath)) {
            debugLog("Could not find shader pack for: " + baseShaderName + ", falling back to filename check");
            return null;
        }

        String versionFromShader = detector.getEuphoriaPatchesVersionFromShader(shaderPath);
        if (versionFromShader == null) {
            debugLog("Could not read version from shader pack: " + baseShaderName + ", falling back to filename check");
        }
        return versionFromShader;
    }

    /**
     * Called by the Iris and Oculus mixins right before Iris saves the settings of a shaderpack. The identifiers go
     * into the settings Iris is about to write, so the file is written once, by Iris
     */
    public static void addIdentifiersToSavedSettings(Path settingsFile, Properties settings) {
        try {
            // Iris deletes the file when there are no settings, don't make it exist with only the identifiers in it
            String fileName = settingsFile.getFileName().toString();
            if (settings.isEmpty() || !fileName.endsWith(".txt")) {
                return;
            }

            ShaderDetector detector = getShaderDetector();
            if (detector == null || !detector.isEuphoriaPatchesShader(settingsFile.resolveSibling(removeTxtSuffix(fileName)))) {
                debugLog("Not adding identifiers to the saved settings, not an EP shader: " + fileName);
                return;
            }

            addIdentifiersToProperties(settings, getVersionIdentifierForShader(fileName, detector));
            debugLog("Added identifiers to the settings that Iris is about to save: " + fileName);
        } catch (Exception e) {
            EuphoriaPatcher.log(2, 0, "Error adding identifiers to the saved settings: " + e.getMessage());
        }
    }

    static void addIdentifiersToProperties(Properties settings, String versionIdentifierToAdd) {
        // Collapse the version identifiers to one, like when rewriting a file
        String existingVersionIdentifier = null;
        for (String key : new ArrayList<>(settings.stringPropertyNames())) {
            if (key.startsWith(VERSION_IDENTIFIER_PREFIX)) {
                if ("true".equals(settings.getProperty(key))) {
                    existingVersionIdentifier = higherVersionIdentifier(existingVersionIdentifier, key + VERSION_IDENTIFIER_SUFFIX);
                }
                settings.remove(key);
            }
        }

        settings.setProperty(EUPHORIA_IDENTIFIER_KEY, "true");

        String versionIdentifier = versionIdentifierToAdd != null ? versionIdentifierToAdd : existingVersionIdentifier;
        if (versionIdentifier != null) {
            // The identifier is "key=true"
            settings.setProperty(versionIdentifier.substring(0, versionIdentifier.length() - VERSION_IDENTIFIER_SUFFIX.length()), "true");
        }
    }

    /**
     * Adds the identifiers to a settings file. For startup and after patching, and for the loaders where Iris does not
     * save the settings through our mixin. The identifiers are put right after the comments at the top, but where they
     * end up in the file does not matter.
     */
    static void addIdentifierToSettingsFile(Path configFile, String versionIdentifierToAdd, boolean allowSettingsConversion) {
        // Try with increasing delay between attempts
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                if (attempt > 0) {
                    // Wait before retrying
                    Thread.sleep(200 * attempt);
                    EuphoriaPatcher.log(0, "Retry attempt " + attempt + " for file: " + configFile.getFileName());
                }

                debugLog("Starting to process file: " + configFile.getFileName());

                if (!Files.isWritable(configFile)) {
                    debugLog("File is not writable: " + configFile.getFileName());
                    return;
                }

                List<String> lines = Files.readAllLines(configFile, StandardCharsets.UTF_8);

                // Iris deletes the file when the pack has no settings, so this is not a file to put identifiers in
                if (!hasSettingLines(lines)) {
                    debugLog("File has no settings, leaving it alone: " + configFile.getFileName());
                    return;
                }

                List<String> newLines = addIdentifiersToLines(configFile, lines, versionIdentifierToAdd, allowSettingsConversion);
                if (newLines == null) {
                    return; // Nothing to do
                }

                debugLog("About to write to file: " + configFile.getFileName());
                writeAtomically(configFile, newLines);
                debugLog("Updated identifiers in settings file: " + configFile.getFileName());
                return;
            } catch (IOException e) {
                if (attempt == 2) { // The Last attempt failed
                    EuphoriaPatcher.log(2, 0, "Error adding identifier to settings file " +
                                        configFile.getFileName() + ": " + e.getMessage());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * @return the lines with the identifiers right after the leading comments, or {@code null} if the file is
     *         already right
     */
    private static List<String> addIdentifiersToLines(Path configFile, List<String> lines, String versionIdentifierToAdd, boolean allowSettingsConversion) {
        boolean settingsChanged = false;
        if (allowSettingsConversion) {
            List<String> convertedLines = ShaderSettingsConverter.convertLines(lines);
            settingsChanged = !lines.equals(convertedLines);
            lines = convertedLines;

            debugLog("Applied settings conversions to file: " + configFile.getFileName() +
                     (settingsChanged ? " (changes applied)" : " (no changes needed)"));
        }

        // Check for identifiers
        boolean mainIdentifierExists = false;
        String existingVersionIdentifier = null;
        int versionIdentifierCount = 0;

        // First scan the file to check what identifiers exist
        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.equals(EUPHORIA_IDENTIFIER)) { // Use trim() for safety
                mainIdentifierExists = true;
            } else if (trimmed.startsWith(VERSION_IDENTIFIER_PREFIX) && trimmed.endsWith(VERSION_IDENTIFIER_SUFFIX)) {
                versionIdentifierCount++;
                existingVersionIdentifier = higherVersionIdentifier(existingVersionIdentifier, trimmed);
            }
        }

        boolean hasDuplicateVersionIdentifiers = versionIdentifierCount > 1;
        if (!settingsChanged && mainIdentifierExists && !hasDuplicateVersionIdentifiers &&
            (versionIdentifierToAdd == null ||
            (existingVersionIdentifier != null && existingVersionIdentifier.equals(versionIdentifierToAdd)))) {

            debugLog((allowSettingsConversion ? "No settings changes needed and identifiers" : "Identifiers") + " are correct for: " + configFile.getFileName());
            return null; // Nothing to do
        } else if (hasDuplicateVersionIdentifiers) {
            debugLog("Found duplicate version identifiers - will collapse to one marker for: " + configFile.getFileName());
        }

        // We need to modify the file - create new content with reorganized structure
        List<String> newLines = new ArrayList<>();

        // Phase 1: Add all comment lines that start with #
        // Usually the first line is a timestamp comment
        for (String line : lines) {
            if (line.trim().startsWith("#")) {
                newLines.add(line);
            }
        }

        // Phase 2: Add identifiers right after comments
        newLines.add(EUPHORIA_IDENTIFIER);

        if (versionIdentifierToAdd != null) {
            newLines.add(versionIdentifierToAdd);
        } else if (existingVersionIdentifier != null) {
            newLines.add(existingVersionIdentifier);
            debugLog("Preserved existing version identifier: " + existingVersionIdentifier);
        }

        // Phase 3: Add all remaining lines except comments and identifiers
        for (String line : lines) {
            String trimmed = line.trim();
            // Skip comments (already added), and identifiers (also already added)
            if (!trimmed.startsWith("#") &&
                !trimmed.equals(EUPHORIA_IDENTIFIER) &&
                !(trimmed.startsWith(VERSION_IDENTIFIER_PREFIX) && trimmed.endsWith(VERSION_IDENTIFIER_SUFFIX))) {
                newLines.add(line);
            }
        }
        return newLines;
    }

    /**
     * Keeps the highest embedded version if there are multiple version identifiers.
     */
    private static String higherVersionIdentifier(String current, String candidate) {
        if (current == null) {
            return candidate;
        }
        String currentVersion = extractVersionFromIdentifier(current);
        String candidateVersion = extractVersionFromIdentifier(candidate);
        if (currentVersion != null && candidateVersion != null &&
            VersionComparator.compareVersionsWithDev(candidateVersion.substring(1), currentVersion.substring(1)) > 0) {
            return candidate;
        }
        return current;
    }

    /**
     * A settings file with only comments and blank lines is never a valid state
     */
    private static boolean hasSettingLines(List<String> lines) {
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Writes the lines to a temp file next to the target and moves it over the target, so nobody who reads the file
     * ever sees it half written.
     */
    private static void writeAtomically(Path configFile, List<String> newLines) throws IOException {
        Path tempFile = configFile.resolveSibling(configFile.getFileName() + TEMP_FILE_SUFFIX);
        try {
            try (java.io.BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                for (String line : newLines) {
                    writer.write(line);
                    writer.newLine();
                }
            }
            Files.move(tempFile, configFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * Copies {@code configFilePath} to the current-version config file name for the given style and adds identifiers.
     * Does nothing if the target already exists or would be the source file itself.
     */
    private static void doConfigFileCopy(Path configFilePath, String style) {
        String newName = EuphoriaPatcher.BRAND_NAME + style + EuphoriaPatcher.VERSION + " + " + EuphoriaPatcher.PATCH_NAME + EuphoriaPatcher.PATCH_VERSION + ".txt";
        try {
            Path newPath = configFilePath.resolveSibling(newName);

            if (Files.exists(newPath)) {
                debugLog("Target config file already exists, skipping copy: " + newName);
                EuphoriaPatcher.log(0, "Shader config file already up to date!");
                return;
            }
            if (configFilePath.equals(newPath)) {
                debugLog("Source config file already has the target name, skipping copy: " + newName);
                return;
            }

            Files.copy(configFilePath, newPath); // Copy old config and rename it to current PATCH_VERSION

            // Add our identifiers to the new config file - include version since the name has current version
            addIdentifierToSettingsFile(newPath, getVersionIdentifier(), true);

            EuphoriaPatcher.log(0, "Successfully updated the " + style + " shader config file to the latest version!");
        } catch (IOException e) {
            EuphoriaPatcher.log(3,0, "Could not rename the config file: " + e.getMessage());
        }
    }

    private static boolean isConfigFile(Path path) {
        return Files.isRegularFile(path) && path.getFileName().toString().endsWith(".txt");
    }

    private static Path findShaderConfigFile(DirectoryStream<Path> textStream, boolean searchOldEuphoriaConfigs, String styleFilter) {
        List<Path> euphoriaFiles = new ArrayList<>();
        List<Path> baseFiles = new ArrayList<>();
        List<Path> flaggedFiles = new ArrayList<>();

        // Categorize all files
        for (Path potentialTextFile : textStream) {
            String name = potentialTextFile.getFileName().toString();
            if (name.endsWith(".txt")) {
                if (name.contains("EuphoriaPatches") || name.contains("EP_")) {
                    euphoriaFiles.add(potentialTextFile);
                    debugLog("Found Euphoria named file: " + name);
                } else {
                    // Check if this file contains our hidden flag
                    String versionFromFile = getEuphoriaPatchesVersionFromFile(potentialTextFile);
                    if (versionFromFile != null) {
                        flaggedFiles.add(potentialTextFile);
                        debugLog("Found flagged file: " + name + " with version " + versionFromFile);
                    } else {
                        // Only add to baseFiles if it matches the base file pattern
                        if (name.matches(".*" + EuphoriaPatcher.BRAND_NAME + ".*(Reimagined|Unbound).*")) {
                            baseFiles.add(potentialTextFile);
                            debugLog("Found base file: " + name);
                        } else {
                            debugLog("Skipping non-matching file: " + name);
                        }
                    }
                }
            }
        }

        // Restrict to the requested style (known only from the file name) when a filter is set.
        if (styleFilter != null) {
            euphoriaFiles.removeIf(p -> !p.getFileName().toString().contains(styleFilter));
            baseFiles.removeIf(p -> !p.getFileName().toString().contains(styleFilter));
            if (!flaggedFiles.isEmpty()) {
                debugLog("Ignoring " + flaggedFiles.size() + " flagged file(s) - style cannot be determined from their name");
                flaggedFiles.clear();
            }
        }

        debugLog("Found " + euphoriaFiles.size() + " Euphoria files, " +
                flaggedFiles.size() + " flagged files, and " + baseFiles.size() + " base files" +
                (styleFilter != null ? " (filtered to style: " + styleFilter + ")" : ""));

        // STEP 1: Try to find Euphoria files by name

        if (!euphoriaFiles.isEmpty()) {
            debugLog("Sorting Euphoria files by version...");
            euphoriaFiles.sort(UpdateShaderConfig::compareConfigFiles);
            Path latestEuphoriaConfig = euphoriaFiles.get(euphoriaFiles.size() - 1);
            String latestName = latestEuphoriaConfig.getFileName().toString();

            if (searchOldEuphoriaConfigs) {
                if (!latestName.contains(EuphoriaPatcher.PATCH_VERSION) || latestName.contains("dev")) {
                    debugLog("Selected old Euphoria config: " + latestName);
                    return latestEuphoriaConfig;
                }
                // Continue to check flagged files if no suitable Euphoria file found
            } else {
                debugLog("Selected latest Euphoria config: " + latestName);
                return latestEuphoriaConfig;
            }
        }

        // STEP 2: If no suitable Euphoria file by name, try flagged files
        if (!flaggedFiles.isEmpty()) {
            debugLog("Sorting flagged files by embedded version...");
            flaggedFiles.sort(UpdateShaderConfig::compareConfigFiles);
            Path latestFlaggedFile = flaggedFiles.get(flaggedFiles.size() - 1);
            String latestName = latestFlaggedFile.getFileName().toString();
            debugLog("Selected flagged file: " + latestName +
                       " with embedded version: " + getEuphoriaPatchesVersionFromFile(latestFlaggedFile));
            return latestFlaggedFile;
        }

        // STEP 3: Only as last resort, fall back to base versions
        if (!baseFiles.isEmpty()) {
            debugLog("No Euphoria files found, falling back to base files");
            baseFiles.sort(UpdateShaderConfig::compareConfigFiles);
            Path latestBaseFile = baseFiles.get(baseFiles.size() - 1);
            debugLog("Selected base file: " + latestBaseFile.getFileName());
            return latestBaseFile;
        }

        debugLog("No suitable config file found");
        return null;
    }

    // Iris saves the identifiers in any order, so the version identifier can come before the main one
    static String getEuphoriaPatchesVersionFromFile(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            boolean foundMainIdentifier = false;
            String highestVersion = null;

            for (String line : lines) {
                String trimmed = line.trim();

                if (trimmed.equals(EUPHORIA_IDENTIFIER)) {
                    foundMainIdentifier = true;
                    debugLog("Found main identifier in file: " + file.getFileName());
                } else if (trimmed.startsWith(VERSION_IDENTIFIER_PREFIX) &&
                        trimmed.endsWith(VERSION_IDENTIFIER_SUFFIX)) {
                    String version = extractVersionFromIdentifier(trimmed);
                    if (version != null) {
                        debugLog("Found version identifier in file: " + file.getFileName() + " - " + version);
                        if (highestVersion == null ||
                            VersionComparator.compareVersionsWithDev(version.substring(1), highestVersion.substring(1)) > 0) {
                            highestVersion = version;
                        }
                    }
                }
            }

            if (!foundMainIdentifier) {
                debugLog("Main identifier not found in file: " + file.getFileName());
                return null;
            }

            if (highestVersion != null) {
                debugLog("Using highest version identifier in file: " + file.getFileName() + " - " + highestVersion);
                return highestVersion;
            }

            debugLog("Found main identifier but no version in file: " + file.getFileName() + " - using default 1.0.0");
            return "_1.0.0";
        } catch (IOException e) {
            EuphoriaPatcher.log(0, "Error reading file " + file.getFileName() + ": " + e.getMessage());
        }

        return null;
    }

    private static int compareConfigFiles(Path p1, Path p2) {
        int versionCompare = ShaderVersionComparator.compareConfigFileVersions(getConfigFileVersion(p1), getConfigFileVersion(p2));
        if (versionCompare != 0) {
            return versionCompare;
        }
        int timeCompare = Long.compare(getLastModifiedMillis(p1), getLastModifiedMillis(p2));
        if (timeCompare != 0) {
            debugLog("Versions equal for " + p1.getFileName() + " / " + p2.getFileName() +
                    " - breaking tie by last modified time (" + timeCompare + ")");
        }
        return timeCompare;
    }

    private static long getLastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            debugLog("Could not read last modified time for " + path.getFileName() + ": " + e.getMessage());
            return 0L;
        }
    }

    private static String getConfigFileVersion(Path path) {
        // First try to get version from file content
        String versionFromFile = getEuphoriaPatchesVersionFromFile(path);
        if (versionFromFile != null) {
            // Use any version found in file for patched configs
            String result = versionFromFile.substring(1) + "|0"; // Remove leading underscore
            debugLog("Using embedded version for " + path.getFileName() + ": " + result);
            return result;
        }

        // Fall back to parsing from filename
        String name = path.getFileName().toString();

        // First try to match Euphoria pattern
        Pattern euphoriaPattern = Pattern.compile("(?:[a-zA-Z_]+)?[rdp]?(\\d+(?:\\.\\d+)*)(?:[rdp]\\d+)?(?: \\+ )?(?:EuphoriaPatches_|EP_)(\\d+(?:\\.\\d+)*(?:-dev\\d+)?)");
        Matcher euphoriaMatcher = euphoriaPattern.matcher(name);
        if (euphoriaMatcher.find()) {
            String mainVersion = euphoriaMatcher.group(1);
            String patchVersion = euphoriaMatcher.group(2);
            if (patchVersion != null) {
                String result = patchVersion + "|" + mainVersion;
                debugLog("Extracted Euphoria version from filename " + name + ": " + result);
                return result; // Euphoria Patches version first
            }
            debugLog("Found main version only in " + name + ": 0|" + mainVersion);
            return "0|" + mainVersion; // If no Euphoria Patches version, use 0
        }

        // For base files (Complementary shaders without Euphoria)
        Pattern basePattern = Pattern.compile(".*" + EuphoriaPatcher.BRAND_NAME + ".*[_r]([\\d.]+).*");
        Matcher baseMatcher = basePattern.matcher(name);
        if (baseMatcher.find()) {
            String baseVersion = baseMatcher.group(1);
            String result = "0|" + baseVersion; // No Euphoria version, only base version
            debugLog("Extracted base version from filename " + name + ": " + result);
            return result;
        }

        debugLog("Could not extract any version from " + name + ", using default 0|0");
        return "0|0"; // Default version if no pattern matches
    }

}
