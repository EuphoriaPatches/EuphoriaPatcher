package mc.euphoria_patches.euphoria_patcher.features;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import mc.euphoria_patches.euphoria_patcher.util.EuphoriaLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateShaderConfig {
    private static final String EUPHORIA_IDENTIFIER = "AAA_THIS_IS_A_EUPHORIA_PATCHES_SETTINGS_FILE=true";
    private static final String VERSION_IDENTIFIER_PREFIX = "AAB_FOR_EUPHORIA_PATCHES_VERSION_";
    private static final String VERSION_IDENTIFIER_SUFFIX = "=true";

    private static final java.util.concurrent.ScheduledExecutorService scheduler = 
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "EuphoriaPatcher-FileWriter");
            t.setDaemon(true); // Make sure it doesn't prevent game exit
            return t;
        });

    public static void shutdownFileWriter() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[UpdateShaderConfig] " + message);
    }
    
    private static String getVersionIdentifier() {
        // Convert PATCH_VERSION (like "_1.5.2") to format "1_5_2" for the identifier
        String version = EuphoriaPatcher.PATCH_VERSION;
        version = version.substring(1); // Remove leading underscore
        version = version.replace(".", "_"); // Replace dots with underscores
        return VERSION_IDENTIFIER_PREFIX + version + VERSION_IDENTIFIER_SUFFIX;
    }
    
    // Extract version from identifier line
    private static String extractVersionFromIdentifier(String line) {
        if (line.startsWith(VERSION_IDENTIFIER_PREFIX) && line.endsWith(VERSION_IDENTIFIER_SUFFIX)) {
            String versionPart = line.substring(VERSION_IDENTIFIER_PREFIX.length(), 
                                              line.length() - VERSION_IDENTIFIER_SUFFIX.length());
            return "_" + versionPart.replace("_", "."); // Convert back to PATCH_VERSION format
        }
        return null;
    }
    
    public static void updateShaderTxtConfigFile(boolean styleUnbound, boolean styleReimagined) {
        try (DirectoryStream<Path> oldConfigTextStream = Files.newDirectoryStream(EuphoriaPatcher.shaderpacks,
                path -> isConfigFile(path, true))) {
            Path oldShaderConfigFilePath = findShaderConfigFile(oldConfigTextStream, true);
            if (oldShaderConfigFilePath != null) {
                doConfigFileCopy(oldShaderConfigFilePath, true, styleUnbound, styleReimagined);
            } else { // No Euphoria settings .txt file
                try (DirectoryStream<Path> baseShaderConfigTextStream = Files.newDirectoryStream(EuphoriaPatcher.shaderpacks,
                        path -> isConfigFile(path, false))) {
                    Path baseShaderConfigFilePath = findShaderConfigFile(baseShaderConfigTextStream, true);
                    if (baseShaderConfigFilePath != null) {
                        doConfigFileCopy(baseShaderConfigFilePath, false, styleUnbound, styleReimagined);
                    }
                } catch (IOException e) {
                    EuphoriaPatcher.log(3,0, "Error reading shaderpacks directory: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            EuphoriaPatcher.log(3,0, "Error reading shaderpacks directory: " + e.getMessage());
        }
        
        // Add identifier to all Euphoria Patches settings files
        markEuphoriaPatchesSettingsFiles();
    }

    public static void markEuphoriaPatchesSettingsFiles() {
        try {
            // Get all the files that need to be updated
            List<Path> filesToUpdate = new ArrayList<>();
            List<Boolean> addVersionFlags = new ArrayList<>();
            
            // Use existing pattern - modified to match just file identification parts
            Pattern euphoriaFilePattern = Pattern.compile(
                "(?:Comp\\d+(?:\\.\\d+)*[rdp]?\\d*EP_|.*(?:EuphoriaPatches|Euphoria-Patches))",
                Pattern.CASE_INSENSITIVE
            );
            
            try (DirectoryStream<Path> configStream = Files.newDirectoryStream(EuphoriaPatcher.shaderpacks,
                    path -> Files.isRegularFile(path) && path.toString().endsWith(".txt"))) {
                
                for (Path configFile : configStream) {
                    String fileName = configFile.getFileName().toString();
                    // Check if matches any Euphoria Patches pattern
                    if (euphoriaFilePattern.matcher(fileName).find()) {
                        boolean addVersionIdentifier = fileName.contains(EuphoriaPatcher.PATCH_VERSION);
                        filesToUpdate.add(configFile);
                        addVersionFlags.add(addVersionIdentifier);
                        debugLog("Identified settings file: " + fileName);
                    }
                }
            }
            
            // Schedule the updates to happen after a delay
            if (!filesToUpdate.isEmpty()) {
                debugLog("Scheduling identifier updates for " + filesToUpdate.size() + " files after shader reload");
                
                // Schedule the task to run 1 second after shader reload completes
                scheduler.schedule(() -> {
                    for (int i = 0; i < filesToUpdate.size(); i++) {
                        Path configFile = filesToUpdate.get(i);
                        boolean addVersionIdentifier = addVersionFlags.get(i);
                        
                        debugLog("Delayed processing of file: " + configFile.getFileName());
                        addIdentifierToSettingsFile(configFile, addVersionIdentifier);
                    }
                }, 1000, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
        } catch (IOException e) {
            EuphoriaPatcher.log(2, 0, "Error preparing to mark settings files: " + e.getMessage());
        }
    }

    private static void addIdentifierToSettingsFile(Path configFile, boolean addVersionIdentifier) {
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
                List<String> newLines = new ArrayList<>();
                boolean mainIdentifierExists = false;
                String existingVersionIdentifier = null;
                
                // First scan the file to check what identifiers exist
                for (String line : lines) {
                    if (line.trim().equals(EUPHORIA_IDENTIFIER)) { // Use trim() for safety
                        mainIdentifierExists = true;
                    } else if (line.startsWith(VERSION_IDENTIFIER_PREFIX) && line.endsWith(VERSION_IDENTIFIER_SUFFIX)) {
                        existingVersionIdentifier = line;
                    }
                }
                
                // If we don't need to add the main identifier and either:
                // 1. We don't need to add the version identifier, or
                // 2. The version identifier already exists and matches the current version
                if (mainIdentifierExists && 
                    (!addVersionIdentifier || 
                    (existingVersionIdentifier != null && existingVersionIdentifier.equals(getVersionIdentifier())))) {
                    return; // Nothing to do
                }
                
                // We need to modify the file - create new content
                if (lines.isEmpty()) {
                    // Empty file - just add identifiers
                    newLines.add(EUPHORIA_IDENTIFIER);
                    if (addVersionIdentifier) {
                        newLines.add(getVersionIdentifier());
                    }
                } else {
                    // Non-empty file
                    // Add first line (timestamp/header)
                    newLines.add(lines.get(0));
                    
                    // Add identifiers
                    newLines.add(EUPHORIA_IDENTIFIER);
                    if (addVersionIdentifier) {
                        newLines.add(getVersionIdentifier());
                    }
                    
                    // Add remaining lines, skipping any existing identifiers
                    if (lines.size() > 1) {
                        for (int i = 1; i < lines.size(); i++) {
                            String line = lines.get(i);
                            if (!line.equals(EUPHORIA_IDENTIFIER) && 
                                !(line.startsWith(VERSION_IDENTIFIER_PREFIX) && line.endsWith(VERSION_IDENTIFIER_SUFFIX))) {
                                newLines.add(line);
                            }
                        }
                    }
                }
                
                debugLog("About to write to file: " + configFile.getFileName());
                
                // Use try-with-resources to ensure streams are properly closed
                try (java.io.BufferedWriter writer = java.nio.file.Files.newBufferedWriter(
                        configFile, StandardCharsets.UTF_8)) {
                    
                    for (String line : newLines) {
                        writer.write(line);
                        writer.newLine();
                    }
                    
                    // Make sure to flush
                    writer.flush();
                }
                
                debugLog("Updated identifiers in settings file: " + configFile.getFileName());
                
                // If we got here without errors, break the retry loop
                break;
            } catch (IOException e) {
                if (attempt == 2) { // The Last attempt failed
                    EuphoriaPatcher.log(2, 0, "Error adding identifier to settings file " + 
                                        configFile.getFileName() + ": " + e.getMessage());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private static void doConfigFileCopy(Path configFilePath, boolean containsPatchName, boolean styleUnbound, boolean styleReimagined){
        String style = styleUnbound ? "Unbound" : "Reimagined";
        String newName = EuphoriaPatcher.BRAND_NAME + style + EuphoriaPatcher.VERSION + " + " + EuphoriaPatcher.PATCH_NAME + EuphoriaPatcher.PATCH_VERSION + ".txt";
        try {
            Path newPath = configFilePath.resolveSibling(newName);
            Files.copy(configFilePath, newPath); // Copy old config and rename it to current PATCH_VERSION
            
            // Add our identifiers to the new config file - include version since the name has current version
            addIdentifierToSettingsFile(newPath, true);
            
            EuphoriaPatcher.log(0, "Successfully updated shader config file to the latest version!");
        } catch (IOException e) {
            EuphoriaPatcher.log(3,0, "Could not rename the config file: " + e.getMessage());
        }
        
        if (styleUnbound && styleReimagined) { // Yeah, this makes things unnecessarily complex lol
            EuphoriaPatcher.log(0, "Both shader styles detected!");
            try (DirectoryStream<Path> latestConfigTextStream = Files.newDirectoryStream(EuphoriaPatcher.shaderpacks,
                    path -> isConfigFile(path, containsPatchName))) { // Create a new DirectoryStream - The iterator of Files.newDirectoryStream can only be used once
                Path latestShaderConfigFilePath = findShaderConfigFile(latestConfigTextStream, false);
                if (latestShaderConfigFilePath != null) {
                    style = latestShaderConfigFilePath.toString().contains("Unbound") ? "Reimagined" : "Unbound"; // Detect what the previously renamed (oldShaderConfigFilePath) .txt contains
                    newName = EuphoriaPatcher.BRAND_NAME + style + EuphoriaPatcher.VERSION + " + " + EuphoriaPatcher.PATCH_NAME + EuphoriaPatcher.PATCH_VERSION + ".txt";
                    try { // Now copy and past the renamed .txt file with a new name - 2 identical.txt files with different style names are now in the shaderpacks folder
                        Path newPath = latestShaderConfigFilePath.resolveSibling(newName);
                        Files.copy(latestShaderConfigFilePath, newPath);
                        
                        // Add our identifiers to this copy too
                        addIdentifierToSettingsFile(newPath, true);
                        
                        EuphoriaPatcher.log(0, "Successfully copied shader config file and renamed it!");
                    } catch (IOException e) {
                        EuphoriaPatcher.log(3,0, "Could not copy and rename the config file: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                EuphoriaPatcher.log(3,0, "Error reading shaderpacks directory: " + e.getMessage());
            }
        }
    }

    // Helper method to check if a file is a config file
    private static boolean isConfigFile(Path path, boolean containsPatchName) {
        String nameText = path.getFileName().toString();
        
        // If we're specifically looking for patched files
        if (containsPatchName) {
            // Check for our standard naming first
            boolean hasStandardName = nameText.matches("(?:Comp\\d\\.\\d|" + EuphoriaPatcher.BRAND_NAME + ").*") && 
                                     nameText.endsWith(".txt") && 
                                     (nameText.contains(EuphoriaPatcher.PATCH_NAME) || nameText.contains(" + EP_"));
            
            // If it doesn't have our standard name, check ANY .txt file since it might have our identifier
            if (!hasStandardName) {
                return nameText.endsWith(".txt");
            }
            return true;
        } else {
            // Original logic for base files
            return nameText.matches(".*" + EuphoriaPatcher.BRAND_NAME + ".*(Reimagined|Unbound).*") && nameText.endsWith(".txt");
        }
    }

    private static Path findShaderConfigFile(DirectoryStream<Path> textStream, boolean searchOldEuphoriaConfigs) {
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

        debugLog("Found " + euphoriaFiles.size() + " Euphoria files, " + 
                flaggedFiles.size() + " flagged files, and " + baseFiles.size() + " base files");

        // STEP 1: Try to find Euphoria files by name

        if (!euphoriaFiles.isEmpty()) {
            debugLog("Sorting Euphoria files by version...");
            euphoriaFiles.sort((p1, p2) -> compareConfigFileVersions(getConfigFileVersion(p1), getConfigFileVersion(p2)));
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
            flaggedFiles.sort((p1, p2) -> compareConfigFileVersions(getConfigFileVersion(p1), getConfigFileVersion(p2)));
            Path latestFlaggedFile = flaggedFiles.get(flaggedFiles.size() - 1);
            String latestName = latestFlaggedFile.getFileName().toString();
            debugLog("Selected flagged file: " + latestName + 
                       " with embedded version: " + getEuphoriaPatchesVersionFromFile(latestFlaggedFile));
            return latestFlaggedFile;
        }

        // STEP 3: Only as last resort, fall back to base versions
        if (!baseFiles.isEmpty()) {
            debugLog("No Euphoria files found, falling back to base files");
            baseFiles.sort((p1, p2) -> compareConfigFileVersions(getConfigFileVersion(p1), getConfigFileVersion(p2)));
            Path latestBaseFile = baseFiles.get(baseFiles.size() - 1);
            debugLog("Selected base file: " + latestBaseFile.getFileName());
            return latestBaseFile;
        }

        debugLog("No suitable config file found");
        return null;
    }

    private static String getEuphoriaPatchesVersionFromFile(Path file) {
        try {
             // Read first few lines of the file to check for our flags
            boolean foundMainIdentifier;
            try (BufferedReader reader = Files.newBufferedReader(file)) {
                String line;
                foundMainIdentifier = false;
                // Check first 10 lines, our flags should be at the top
                for (int i = 0; i < 10 && (line = reader.readLine()) != null; i++) {
                    if (line.trim().equals(EUPHORIA_IDENTIFIER)) {
                        foundMainIdentifier = true;
                        debugLog("Found main identifier in file: " + file.getFileName());
                    } else if (foundMainIdentifier &&
                            line.startsWith(VERSION_IDENTIFIER_PREFIX) &&
                            line.endsWith(VERSION_IDENTIFIER_SUFFIX)) {
                        // Extract version from the identifier line
                        String version = extractVersionFromIdentifier(line);
                        debugLog("Found version identifier in file: " + file.getFileName() + " - " + version);
                        return version;
                    }
                }
            }

            // If we found the main identifier but no version, return a default version
            if (foundMainIdentifier) {
                debugLog("Found main identifier but no version in file: " + file.getFileName() + " - using default 1.0.0");
                return "_1.0.0";  // Default version if no version identifier is found
            }
            debugLog("Main identifier not found in file: " + file.getFileName());
        } catch (IOException e) {
            EuphoriaPatcher.log(0, "Error reading file " + file.getFileName() + ": " + e.getMessage());
        }

        return null;
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

    private static int compareConfigFileVersions(String v1, String v2) {
        String[] fullVersion1 = v1.split("\\|");
        String[] fullVersion2 = v2.split("\\|");

        // Compare Euphoria Patches versions first
        int epCompare = compareVersionParts(fullVersion1[0], fullVersion2[0]);
        if (epCompare != 0) {
            debugLog("Comparing versions: " + v1 + " vs " + v2 + " - patch versions differ: " + epCompare);
            return epCompare;
        }

        // If Euphoria Patches versions are the same, compare main versions
        int result = compareVersionParts(fullVersion1[1], fullVersion2[1]);
        debugLog("Comparing versions: " + v1 + " vs " + v2 + " - main versions: " + result);
        return result;
    }

    private static int compareVersionParts(String p1, String p2) {
        String[] parts1 = p1.split("[.\\-]");
        String[] parts2 = p2.split("[.\\-]");
        int length = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < length; i++) {
            String part1 = i < parts1.length ? parts1[i] : "0";
            String part2 = i < parts2.length ? parts2[i] : "0";

            boolean isP1Dev = part1.contains("dev");
            boolean isP2Dev = part2.contains("dev");

            if (isP1Dev && isP2Dev) {
                // Both are dev versions, compare them
                String[] devParts1 = part1.split("dev");
                String[] devParts2 = part2.split("dev");
                debugLog(devParts1[1] + "  " + devParts2[1]);
                int mainCompare = Integer.compare(Integer.parseInt(devParts1[1]), Integer.parseInt(devParts2[1]));
                if (mainCompare != 0) {
                    return mainCompare;
                }
                return Integer.parseInt(devParts1[1]);
            } else if (isP1Dev) {
                // p1 is a dev version, p2 is not. p2 is considered newer.
                return -1;
            } else if (isP2Dev) {
                // p2 is a dev version, p1 is not. p1 is considered newer.
                return 1;
            } else {
                // Neither is a dev version, compare as integers
                int compare = Integer.compare(Integer.parseInt(part1), Integer.parseInt(part2));
                if (compare != 0) {
                    return compare;
                }
            }
        }
        return 0;
    }
}
