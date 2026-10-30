package com.euphoriapatches.euphoria_patcher.integration;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.util.mod.ModLoaderSpecifics;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.mod.ModChecker;
import com.euphoriapatches.euphoria_patcher.util.mod.ModsDirectory;
import com.euphoriapatches.euphoria_patcher.util.VersionComparator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Utility class for detecting and interacting with shader loader mods (Iris, Oculus, OptiFine, Angelica)
 * Provides methods to:
 * - Detect which shader loader is installed
 * - Extract version information from shader loader filenames
 * - Locate and read shader loader configuration files
 * - Get information about the currently selected shaderpack
 */
@SuppressWarnings("SpellCheckingInspection")
public class ShaderLoader {
    // Constants for shader types
    public static final String IRIS = "iris";
    public static final String OCULUS = "oculus";
    public static final String OPTIFINE = "optifine";
    public static final String ANGELICA = "angelica";
    public static final String NEOCULUS = "neoculus";
    public static final String UNKNOWN = "unknown";

    private static final Pattern MINECRAFT_VERSION = Pattern.compile("\\d+\\.\\d+(?:\\.\\d+)?");
    // The numbers after +mc, anything after them (like -local or .jar) is not part of the version
    private static final Pattern IRIS_MC_AFTER_PLUS_FILE = Pattern.compile("\\+mc(\\d+(?:\\.\\d+)+)");
    private static final Pattern IRIS_MC_FIRST_FILE = Pattern.compile("^iris-mc(\\d+(?:\\.\\d+)+)-");
    // The three numbers of the Iris version, with or without the loader or the Minecraft version in front of them
    private static final Pattern IRIS_FILE_VERSION = Pattern.compile("^iris-(?:[a-z]+-|mc\\d+(?:\\.\\d+)+-)?(\\d+\\.\\d+\\.\\d+)");

    // Cache variables
    private static File cachedShaderFile = null;
    private static boolean shaderFileSearched = false;
    private static String cachedShaderLoader = null;
    private static String cachedMCVersion = null;
    private static Integer cachedShaderLoaderVersion = null;
    private static Path shaderLoaderConfigPath = null;
    private static final Map<String, Path> shaderpackPathCache = new HashMap<>();
    private static Path cachedCurrentShaderpackPath = null;
    private static long lastConfigModifiedTime = 0;

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[ShaderLoader] " + message);
    }

    public static boolean isShaderLoaderRunning() {
        debugLog("Iris is running: " + DefineHelper.isShaderLoaderRunning);
        return DefineHelper.isShaderLoaderRunning;
    }

    /**
     * Detects shader loader by filename (fallback method)
     * @param shaderFile The shader loader file to analyze
     * @return The shader loader type, or UNKNOWN if not detected
     */
    private static String detectShaderLoaderByFilename(File shaderFile) {
        debugLog("Attempting shader loader detection via filename (fallback method)");

        if (shaderFile == null) {
            debugLog("No shader file provided for filename detection");
            return UNKNOWN;
        }

        String fileName = shaderFile.getName().toLowerCase(Locale.ROOT);
        debugLog("Analyzing file name: " + fileName);

        if (fileName.startsWith("iris")) {
            debugLog("Detected IRIS via filename");
            return IRIS;
        } else if (fileName.startsWith("oculus") || fileName.startsWith("mekalus") || fileName.startsWith("emberlens")) {
            debugLog("Detected OCULUS via filename" + (fileName.startsWith("mekalus") ? " (Mekalus fork)" : fileName.startsWith("emberlens") ? " (Emberlens fork)" : ""));
            return OCULUS;
        } else if (fileName.startsWith("optifine")) {
            debugLog("Detected OPTIFINE via filename");
            return OPTIFINE;
        } else if (fileName.startsWith("angelica")) {
            debugLog("Detected ANGELICA via filename");
            return ANGELICA;
        } else if (fileName.startsWith("neoculus")) {
            debugLog("Detected NEOCULUS via filename");
            return NEOCULUS;
        }

        debugLog("Could not determine shader loader type from filename");
        return UNKNOWN;
    }

    /**
     * Whether a jar name (any capitalisation) is the one of a shader loader. Iris has two name schemes:
     * iris-fabric-1.8.8+mc1.21.4.jar and iris-mc1.20.1-1.6.9.jar
     */
    static boolean isShaderLoaderFileName(String fileName) {
        fileName = fileName.toLowerCase(Locale.ROOT);
        return (fileName.startsWith("iris") && (fileName.contains("fabric") || fileName.contains("neoforge") || fileName.contains("+mc") || fileName.startsWith("iris-mc"))) ||
                fileName.startsWith("oculus-mc") ||
                fileName.startsWith("mekalus-mc") ||
                fileName.startsWith("emberlens-mc") ||
                fileName.startsWith("optifine_") ||
                fileName.startsWith("neoculus-mc") ||
                fileName.startsWith("angelica");
    }

    private static File findShaderLoaderFile() {
        // Return cached result if we've already searched
        if (shaderFileSearched) {
            debugLog("Using cached shader file result: " + (cachedShaderFile != null ? cachedShaderFile.getName() : "null"));
            return cachedShaderFile;
        }

        debugLog("Searching for shader loader file in mods directory");
        try {
            File modsFolder = ModsDirectory.get().toFile();
            File[] modFiles = modsFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jar"));

            if (modFiles != null) {
                debugLog("Found " + modFiles.length + " JAR files in mods directory");
                for (File modFile : modFiles) {
                    String fileName = modFile.getName().toLowerCase(Locale.ROOT);
                    debugLog("Checking file: " + fileName);

                    // Skip compatibility and addon mods
                    if (fileName.contains("compat") ||
                        fileName.contains("addon") ||
                        fileName.contains("compatibility") ||
                        fileName.contains("flywheel")) {
                        debugLog("Skipping compatibility/addon mod: " + fileName);
                        continue;
                    }

                    if (isShaderLoaderFileName(fileName)) {
                        cachedShaderFile = modFile;
                        shaderFileSearched = true;
                        debugLog("Found shader loader file: " + modFile.getName());
                        return cachedShaderFile;
                    }
                }
            }
            debugLog("No shader loader file found");
            shaderFileSearched = true;
            return null;
        } catch (Exception e) {
            EuphoriaPatcher.log(2, 0, "Error finding shader loader: " + e.getMessage());
            debugLog("Exception while searching for shader loader: " + e.getMessage());
            shaderFileSearched = true;
            return null;
        }
    }

    /**
     * Gets the shader loader mod name from the shader loader filename.
     * Examples: "iris", "oculus", "optifine", "angelica"
     *
     * @return The shader loader mod name, or "unknown" if not detected
     */
    public static String getShaderLoader() {
        // Return cached result if available
        if (cachedShaderLoader != null) {
            debugLog("Using cached shader loader type: " + cachedShaderLoader);
            return cachedShaderLoader;
        }

        debugLog("Determining shader loader type");

        // Primary method: Class-based detection
        String detectedByClass = ModChecker.detectShaderLoaderByClass();
        if (detectedByClass != null) {
            cachedShaderLoader = detectedByClass;
            return cachedShaderLoader;
        }

        // Fallback method: Filename-based detection
        debugLog("Class-based detection failed, falling back to filename detection");
        File shaderFile = findShaderLoaderFile();
        if (shaderFile == null) {
            debugLog("No shader file found, returning UNKNOWN");
            EuphoriaPatcher.log(2, 0, "No shader loader mod was found");
            cachedShaderLoader = UNKNOWN;
            return cachedShaderLoader;
        }

        cachedShaderLoader = detectShaderLoaderByFilename(shaderFile);
        return cachedShaderLoader;
    }

    /**
     * Gets the Minecraft version string from the shader loader filename.
     * Examples: "1.7.10", "1.8.9", "1.12.2", "1.16.2", "1.21.1"
     *
     * @return The Minecraft version as a string, or "unknown" if not detected
     */
    public static String getShaderLoaderMCVersion() {
        // Return cached result if available
        if (cachedMCVersion != null) {
            debugLog("Using cached MC version: " + cachedMCVersion);
            return cachedMCVersion;
        }

        debugLog("Extracting Minecraft version from shader loader filename");
        try {
            File shaderFile = findShaderLoaderFile();
            if (shaderFile == null) {
                debugLog("No shader file found, returning UNKNOWN version");
                cachedMCVersion = UNKNOWN;
                return cachedMCVersion;
            }

            String fileName = shaderFile.getName();
            String lowerFileName = fileName.toLowerCase(Locale.ROOT);
            String extractedVersion = null;
            debugLog("Parsing version from filename: " + fileName);

            // Handle Angelica format: angelica-1.0.0-betaXX.jar
            if (lowerFileName.startsWith("angelica")) {
                debugLog("Detected Angelica format - always for Minecraft 1.7.10");
                // Angelica is specifically for 1.7.10
                extractedVersion = "1.7.10";
                debugLog("Set version to 1.7.10 for Angelica");
            }
            else if (lowerFileName.startsWith("neoculus")) {
                debugLog("Detected NeOculus format - always for Minecraft 1.21.1");
                // NeOculus is specifically for 1.21.1
                extractedVersion = "1.21.1";
                debugLog("Set version to 1.21.1 for NeOculus");
            }
            // Handle OptiFine format: OptiFine_1.18.1_HD_U_H6.jar
            else if (lowerFileName.startsWith("optifine_")) {
                debugLog("Detected OptiFine format");
                String[] parts = fileName.split("_");
                if (parts.length >= 2) {
                    extractedVersion = parts[1]; // Return the version part (1.18.1)
                    debugLog("Extracted version: " + extractedVersion);
                }
            }
            // Handle Iris format: iris-fabric-1.8.8+mc1.21.4.jar
            else if (lowerFileName.startsWith("iris")) {
                debugLog("Detected Iris format");
                extractedVersion = extractIrisMinecraftVersion(fileName);
                debugLog("Extracted version: " + extractedVersion);
            }
            // Handle Oculus/Mekalus/Emberlens format: oculus-mc1.20.1-1.8.0.jar, mekalus-mc1.20.1-1.7.0.3.jar or emberlens-mc1.20.1-1.8.6.jar
            else if (lowerFileName.startsWith("oculus") || lowerFileName.startsWith("mekalus") || lowerFileName.startsWith("emberlens")) {
                debugLog("Detected Oculus-like format (" + lowerFileName + ")");
                int mcIndex = lowerFileName.indexOf("-mc");
                if (mcIndex != -1) {
                    int dashAfterMc = fileName.indexOf('-', mcIndex + 3);
                    if (dashAfterMc != -1) {
                        extractedVersion = fileName.substring(mcIndex + 3, dashAfterMc);
                        debugLog("Extracted version: " + extractedVersion);
                    }
                }
            }

            // Validate the extracted version format
            if (extractedVersion != null) {
                if (isMinecraftVersion(extractedVersion)) {
                    debugLog("Valid version format: " + extractedVersion);
                    cachedMCVersion = extractedVersion;
                    return cachedMCVersion;
                } else {
                    debugLog("Invalid version format: " + extractedVersion);
                    EuphoriaPatcher.log(1, 0, "Invalid version format detected: " + extractedVersion);
                }
            } else {
                debugLog("Could not extract version from filename");
            }

            debugLog("Setting version to UNKNOWN");
            cachedMCVersion = UNKNOWN;
            return cachedMCVersion;
        } catch (Exception e) {
            EuphoriaPatcher.log(2, 0, "Error extracting Minecraft version: " + e.getMessage());
            debugLog("Exception extracting Minecraft version: " + e.getMessage());
            cachedMCVersion = UNKNOWN;
            return cachedMCVersion;
        }
    }

    /**
     * Utility method to check if the detected shader loader's Minecraft version is
     * greater than or equal to a specific version.
     *
     * @param minVersion The minimum required version (e.g., "1.21.1")
     * @return true if the detected version >= minVersion, false otherwise or if a version is unknown or invalid
     */
    public static boolean isMinecraftVersionAtLeast(String minVersion) {
        String currentVersion = getShaderLoaderMCVersion();
        if (minVersion == null || UNKNOWN.equals(currentVersion) || UNKNOWN.equals(minVersion)) {
            return false;
        }

        try {
            return VersionComparator.isNewerOrEqual(currentVersion, minVersion);
        } catch (NumberFormatException e) {
            EuphoriaPatcher.log(1, 0, "Error comparing versions: " + e.getMessage());
            return false;
        }
    }

    /**
     * Reads the Minecraft version from the name of an Iris jar.
     *
     * @return The Minecraft version as written in the name, or null if the name has none
     */
    static String extractIrisMinecraftVersion(String fileName) {
        String lowerFileName = fileName.toLowerCase(Locale.ROOT);
        Matcher matcher = IRIS_MC_AFTER_PLUS_FILE.matcher(lowerFileName);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = IRIS_MC_FIRST_FILE.matcher(lowerFileName);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Whether a text is a Minecraft version
     */
    static boolean isMinecraftVersion(String version) {
        return version != null && MINECRAFT_VERSION.matcher(version).matches();
    }

    /**
     * Reads the Iris version from the file name
     * @return The version as "X.Y.Z", or null if the name has none
     */
    static String extractIrisVersion(String fileName) {
        Matcher matcher = IRIS_FILE_VERSION.matcher(fileName.toLowerCase(Locale.ROOT));
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Gets the shader loader version from the shader loader filename.
     * Examples:
     * - iris-fabric-1.8.8+mc1.21.4.jar -> 10808
     * - oculus-mc1.20.1-1.8.0.jar -> 10800
     * - mekalus-mc1.20.1-1.7.0.3.jar -> 10700 (simplifying to 1.7.0)
     * - angelica-1.0.0-beta15.jar -> 10000
     *
     * @return The shader loader version as integer, or 0 if not detected or OptiFine
     */
    public static int getShaderLoaderVersion() {
        // Return cached result if available
        if (cachedShaderLoaderVersion != null) {
            debugLog("Using cached shader loader version: " + cachedShaderLoaderVersion);
            return cachedShaderLoaderVersion;
        }

        debugLog("Extracting shader loader version from filename");
        try {
            File shaderFile = findShaderLoaderFile();
            if (shaderFile == null) {
                debugLog("No shader file found, returning 0");
                cachedShaderLoaderVersion = 0;
                return cachedShaderLoaderVersion;
            }

            String fileName = shaderFile.getName();
            String lowerFileName = fileName.toLowerCase(Locale.ROOT);
            String extractedVersion = null;
            debugLog("Parsing shader loader version from filename: " + fileName);

            // Handle Iris format: iris-fabric-1.8.8+mc1.21.4.jar or iris-neoforge-1.8.14-beta.1+mc1.21.1.jar
            if (lowerFileName.startsWith("iris")) {
                debugLog("Detected Iris format");
                extractedVersion = extractIrisVersion(fileName);
                debugLog("Extracted Iris version: " + extractedVersion);
            }
            // Handle Oculus/Mekalus/Emberlens format: oculus-mc1.20.1-1.8.0.jar, mekalus-mc1.20.1-1.7.0.3.jar or emberlens-mc1.20.1-1.8.6.jar
            else if (lowerFileName.startsWith("oculus") || lowerFileName.startsWith("mekalus") || lowerFileName.startsWith("emberlens")) {
                debugLog("Detected Oculus-like format (" + lowerFileName + ")");
                // Find the last dash before .jar to get the version
                int lastDashIndex = fileName.lastIndexOf('-');
                int jarIndex = lowerFileName.indexOf(".jar");
                if (lastDashIndex != -1 && jarIndex != -1 && jarIndex > lastDashIndex) {
                    String fullVersion = fileName.substring(lastDashIndex + 1, jarIndex);

                    // Handle Mekalus versioning (e.g., 1.7.0.3 -> 1.7.0)
                    if (lowerFileName.startsWith("mekalus") && fullVersion.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                        String[] versionParts = fullVersion.split("\\.");
                        extractedVersion = versionParts[0] + "." + versionParts[1] + "." + versionParts[2];
                        debugLog("Simplified Mekalus version from " + fullVersion + " to " + extractedVersion);
                    } else {
                        extractedVersion = fullVersion;
                    }

                    debugLog("Extracted version: " + extractedVersion);
                }
            }
            // Handle Angelica format: angelica-1.0.0-betaXX.jar
            else if (lowerFileName.startsWith("angelica")) {
                debugLog("Detected Angelica format");
                // Find version between "angelica-" and "-beta"
                int angelicaIndex = lowerFileName.indexOf("angelica-");
                int betaIndex = lowerFileName.indexOf("-beta");
                if (angelicaIndex != -1 && betaIndex != -1 && betaIndex > angelicaIndex) {
                    extractedVersion = fileName.substring(angelicaIndex + 9, betaIndex);
                    debugLog("Extracted Angelica version: " + extractedVersion);
                }
            }
            // Handle OptiFine - return 0 as requested
            else if (lowerFileName.startsWith("optifine")) {
                debugLog("OptiFine detected - returning version 0 as specified");
                cachedShaderLoaderVersion = 0;
                return cachedShaderLoaderVersion;
            }
            // Handle NeOculus - neoculus-mc1.21.1-1.8.7.jar
            else if (lowerFileName.startsWith("neoculus")) {
                debugLog("Detected NeOculus format");
                // Find the last dash before .jar to get the version
                int lastDashIndex = fileName.lastIndexOf('-');
                int jarIndex = lowerFileName.indexOf(".jar");
                if (lastDashIndex != -1 && jarIndex != -1 && jarIndex > lastDashIndex) {
                    extractedVersion = fileName.substring(lastDashIndex + 1, jarIndex);
                    debugLog("Extracted NeOculus version: " + extractedVersion);
                }
            }

            // Convert the extracted version to integer
            if (extractedVersion != null) {
                try {
                    int versionInt = VersionComparator.toPackedInt(extractedVersion);
                    debugLog("Converted shader loader version to integer: " + versionInt);
                    cachedShaderLoaderVersion = versionInt;
                    return cachedShaderLoaderVersion;
                } catch (Exception e) {
                    debugLog("Error converting shader loader version to int: " + e.getMessage());
                }
            } else {
                debugLog("Could not extract shader loader version from filename");
            }

            debugLog("Setting shader loader version to 0");
            cachedShaderLoaderVersion = 0;
            return cachedShaderLoaderVersion;
        } catch (Exception e) {
            EuphoriaPatcher.log(2, 0, "Error extracting shader loader version: " + e.getMessage());
            debugLog("Exception extracting shader loader version: " + e.getMessage());
            cachedShaderLoaderVersion = 0;
            return cachedShaderLoaderVersion;
        }
    }

    /**
     * Gets the shader loader version as a key-value pair for define injection
     * @return String array [key, value] or null if no shader loader detected
     */
    public static String[] getShaderLoaderVersionDefine() {
        int version = getShaderLoaderVersion();
        String shaderLoader = getShaderLoader();

        switch (shaderLoader) {
            case IRIS:
                return new String[]{"EUPHORIA_PATCHES_IRIS_VERSION", String.valueOf(version)};
            case OCULUS:
                return new String[]{"EUPHORIA_PATCHES_OCULUS_VERSION", String.valueOf(version)};
            case ANGELICA:
                return new String[]{"EUPHORIA_PATCHES_ANGELICA_VERSION", String.valueOf(version)};
            case NEOCULUS:
                return new String[]{"EUPHORIA_PATCHES_NEOCULUS_VERSION", String.valueOf(version)};
            default:
                return null;
        }
    }

    /**
     * Locates the shader loader configuration file
     * Checks for Iris, Oculus, OptiFine and Angelica config files in that order
     * @return Path to the shader loader config file, or null if none found
     */
    public static Path getShaderLoaderConfigPath() {
        // Return cached result if available
        if (shaderLoaderConfigPath != null && Files.exists(shaderLoaderConfigPath)) {
            debugLog("Using cached shader loader config path: " + shaderLoaderConfigPath);
            return shaderLoaderConfigPath;
        }

        Path configPath;
        String shaderLoader = getShaderLoader();

        switch (shaderLoader) {
            case IRIS:
                configPath = EuphoriaPatcher.configDirectory.resolve("iris.properties");
                if (Files.exists(configPath)) {
                    debugLog("Found Iris config at: " + configPath);
                    shaderLoaderConfigPath = configPath;
                    return shaderLoaderConfigPath;
                }
                break;
            case OCULUS:
            case NEOCULUS:
                configPath = EuphoriaPatcher.configDirectory.resolve("oculus.properties");
                if (Files.exists(configPath)) {
                    debugLog("Found" + (shaderLoader.equals(NEOCULUS) ? " NeOculus" : " Oculus") + " config at: " + configPath);
                    shaderLoaderConfigPath = configPath;
                    return shaderLoaderConfigPath;
                }
                break;
            case OPTIFINE:
                configPath = ModLoaderSpecifics.gameDirectory().resolve("optionsshaders.txt");
                if (Files.exists(configPath)) {
                    debugLog("Found OptiFine config at: " + configPath);
                    shaderLoaderConfigPath = configPath;
                    return shaderLoaderConfigPath;
                }
                break;
            case ANGELICA:
                configPath = EuphoriaPatcher.configDirectory.resolve("shaders.properties");
                if (Files.exists(configPath)) {
                    debugLog("Found Angelica config at: " + configPath);
                    shaderLoaderConfigPath = configPath;
                    return shaderLoaderConfigPath;
                }
                break;
            default:  // Fallback: check all known config locations
                configPath = EuphoriaPatcher.configDirectory.resolve("iris.properties");
                if (Files.exists(configPath)) {
                    shaderLoaderConfigPath = configPath;
                    return shaderLoaderConfigPath;
                }
                configPath = EuphoriaPatcher.configDirectory.resolve("oculus.properties");
                if (Files.exists(configPath)) {
                    shaderLoaderConfigPath = configPath;
                    return shaderLoaderConfigPath;
                }
                configPath = ModLoaderSpecifics.gameDirectory().resolve("optionsshaders.txt");
                if (Files.exists(configPath)) {
                    shaderLoaderConfigPath = configPath;
                    return shaderLoaderConfigPath;
                }
                configPath = EuphoriaPatcher.configDirectory.resolve("shaders.properties");
                if (Files.exists(configPath)) {
                    shaderLoaderConfigPath = configPath;
                    return shaderLoaderConfigPath;
                }
                break;
        }

        debugLog("No shader loader config found");
        shaderLoaderConfigPath = null;
        return null;
    }

    /**
     * Gets the path to the currently selected shaderpack by reading the shader loader config
     * @return Path to the current shaderpack directory or zip file, or null if none is selected or an error occurs
     */
    public static Path getCurrentShaderpackPath() {
        if (shaderLoaderConfigPath == null) getShaderLoaderConfigPath();
        if (shaderLoaderConfigPath == null) {
            debugLog("No shader loader config path available");
            return null;
        }

        // Check if config file has been modified since we last cached
        try {
            long currentModifiedTime = Files.getLastModifiedTime(shaderLoaderConfigPath).toMillis();
            if (cachedCurrentShaderpackPath != null && currentModifiedTime == lastConfigModifiedTime) {
                debugLog("Using cached current shaderpack path (config unchanged): " + cachedCurrentShaderpackPath);
                return cachedCurrentShaderpackPath;
            }
            lastConfigModifiedTime = currentModifiedTime;
        } catch (IOException e) {
            debugLog("Could not check config file modification time: " + e.getMessage());
            // Continue anyway - will read the file
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(shaderLoaderConfigPath.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("shaderPack=")) {
                    String shaderpackName = line.substring("shaderPack=".length()).trim();

                    // Check if no shaderpack is selected (empty or "OFF")
                    if (shaderpackName.isEmpty() || shaderpackName.equalsIgnoreCase("OFF")) {
                        cachedCurrentShaderpackPath = null;
                        return null;
                    }

                    // Check cache first
                    if (shaderpackPathCache.containsKey(shaderpackName)) {
                        Path cachedPath = shaderpackPathCache.get(shaderpackName);
                        if (cachedPath != null && Files.exists(cachedPath)) {
                            debugLog("Using cached shaderpack path for: " + shaderpackName + " from the HashMap");
                            cachedCurrentShaderpackPath = cachedPath;
                            return cachedPath;
                        } else {
                            // Remove stale cache entry
                            shaderpackPathCache.remove(shaderpackName);
                        }
                    }

                    // Find the actual shader file by name without relying on direct path resolution
                    try {
                        Path shaderpackPath = findShaderpackByName(shaderpackName);
                        // Cache the result (even if null)
                        shaderpackPathCache.put(shaderpackName, shaderpackPath);
                        cachedCurrentShaderpackPath = shaderpackPath;
                        return shaderpackPath;
                    } catch (Exception e) {
                        debugLog("Error finding shaderpack: " + e.getMessage());
                        EuphoriaPatcher.log(2, 0, "Could not find shaderpack: " + shaderpackName + " - " + e.getMessage());
                        cachedCurrentShaderpackPath = null;
                        return null;
                    }
                }
            }
        } catch (IOException e) {
            EuphoriaPatcher.log(3, 0, "Error reading shader loader config: " + e.getMessage());
        }

        cachedCurrentShaderpackPath = null;
        return null;
    }

    public static String getCurrentShaderpackName() {
        Path shaderpackPath = getCurrentShaderpackPath();
        if (shaderpackPath != null) {
            return shaderpackPath.getFileName().toString();
        }
        return null;
    }

    /**
     * Finds a shaderpack by name in the shaderpacks directory
     * Handles Unicode escape sequences (like \u0424) that may appear in config files
     * @param shaderpackName The name of the shaderpack to find
     * @return Path to the shaderpack, or null if not found
     * @throws IOException if there's an error reading the directory
     */
    private static Path findShaderpackByName(String shaderpackName) throws IOException {
        // First try direct resolution (will work for normal filenames)
        try {
            Path directPath = EuphoriaPatcher.shaderpacks.resolve(shaderpackName);
            if (Files.exists(directPath)) {
                debugLog("Found shader directly: " + directPath);
                return directPath;
            }
        } catch (InvalidPathException e) {
            debugLog("Invalid path characters in shader name: " + e.getMessage());
        }

        // If the name contains Unicode escape sequences (like \u0424), unescape them
        String unescapedName = shaderpackName;
        if (shaderpackName.contains("\\u")) {
            unescapedName = unescapeJavaString(shaderpackName);
            debugLog("Unescaped '" + shaderpackName + "' to '" + unescapedName + "'");

            // Try direct resolution with unescaped name
            try {
                Path directPath = EuphoriaPatcher.shaderpacks.resolve(unescapedName);
                if (Files.exists(directPath)) {
                    debugLog("Found shader with unescaped name: " + directPath);
                    return directPath;
                }
            } catch (InvalidPathException e) {
                debugLog("Invalid path after unescaping: " + e.getMessage());
            }
        }

        debugLog("Direct path resolution failed, trying directory scan");

        // Special case for error shader
        final boolean isErrorShader = shaderpackName.contains("EuphoriaPatches") &&
                                     shaderpackName.contains("Error") &&
                                     shaderpackName.contains("Shader");

        // Fall back to directory listing and exact matching
        final String finalUnescapedName = unescapedName;
        try (Stream<Path> fileStream = Files.list(EuphoriaPatcher.shaderpacks)) {
            Path result = fileStream
                    .filter(Files::exists)
                    .filter(path -> {
                        String fileName = path.getFileName().toString();

                        // Check exact match with original name
                        if (fileName.equals(shaderpackName)) {
                            debugLog("Found exact shader match: " + fileName);
                            return true;
                        }

                        // Check exact match with unescaped name
                        if (fileName.equals(finalUnescapedName)) {
                            debugLog("Found shader match with unescaped name: " + fileName);
                            return true;
                        }

                        // Special handling for error shader
                        if (isErrorShader && fileName.contains("EuphoriaPatches") &&
                            fileName.contains("Error") && fileName.contains("Shader")) {
                            debugLog("Found error shader: " + fileName);
                            return true;
                        }

                        return false;
                    })
                    .findFirst().orElse(null);

            if (result == null) {
                debugLog("No matching shader found in directory scan");
            }
            return result;
        }
    }

    /**
     * Unescapes Java Unicode escape sequences in a string (e.g., \u0424 -> Ф)
     * This is needed because config file readers don't automatically interpret escape sequences
     * @param str The string potentially containing escape sequences
     * @return The unescaped string
     */
    private static String unescapeJavaString(String str) {
        if (str == null || !str.contains("\\u")) {
            return str;
        }

        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < str.length()) {
            if (i < str.length() - 5 && str.charAt(i) == '\\' && str.charAt(i + 1) == 'u') {
                // Found potential Unicode escape sequence
                try {
                    String hex = str.substring(i + 2, i + 6);
                    int codePoint = Integer.parseInt(hex, 16);
                    sb.append((char) codePoint);
                    i += 6;
                } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
                    // Not a valid escape sequence, keep as-is
                    sb.append(str.charAt(i));
                    i++;
                }
            } else {
                sb.append(str.charAt(i));
                i++;
            }
        }
        return sb.toString();
    }
}
