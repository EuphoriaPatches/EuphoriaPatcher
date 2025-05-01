package mc.euphoria_patches.euphoria_patcher.util;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShaderLoader {
    // Constants for shader types
    public static final String IRIS = "iris";
    public static final String OCULUS = "oculus";
    public static final String OPTIFINE = "optifine";
    public static final String UNKNOWN = "unknown";

    // Pattern to validate Minecraft version format (1.X.Y or 1.XX.YY)
    private static final Pattern VERSION_PATTERN = Pattern.compile("1\\.(\\d+)\\.(\\d+)");
    
    // Cache variables
    private static File cachedShaderFile = null;
    private static boolean shaderFileSearched = false;
    private static String cachedShaderLoader = null;
    private static String cachedMCVersion = null;

    private static File findShaderLoaderFile() {
        // Return cached result if we've already searched
        if (shaderFileSearched) {
            return cachedShaderFile;
        }
        
        try {
            File modsFolder = new File(String.valueOf(EuphoriaPatcher.modDirectory));
            File[] modFiles = modsFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));

            if (modFiles != null) {
                for (File modFile : modFiles) {
                    String fileName = modFile.getName().toLowerCase();

                    if (fileName.startsWith("iris") ||
                            fileName.startsWith("oculus") ||
                            fileName.startsWith("optifine")) {
                        cachedShaderFile = modFile;
                        shaderFileSearched = true;
                        return cachedShaderFile;
                    }
                }
            }
            shaderFileSearched = true;
            return null;
        } catch (Exception e) {
            EuphoriaPatcher.log(2, 0, "Error finding shader loader: " + e.getMessage());
            shaderFileSearched = true;
            return null;
        }
    }

    /**
     * Gets the shader loader mod name from the shader loader filename.
     * Examples: "iris", "oculus", "optifine"
     *
     * @return The shader loader mod name, or "unknown" if not detected
     */
    public static String getShaderLoader() {
        // Return cached result if available
        if (cachedShaderLoader != null) {
            return cachedShaderLoader;
        }
        
        File shaderFile = findShaderLoaderFile();
        if (shaderFile == null) {
            cachedShaderLoader = UNKNOWN;
            return cachedShaderLoader;
        }

        String fileName = shaderFile.getName().toLowerCase();
        if (fileName.startsWith("iris")) {
            cachedShaderLoader = IRIS;
        } else if (fileName.startsWith("oculus")) {
            cachedShaderLoader = OCULUS;
        } else if (fileName.startsWith("optifine")) {
            cachedShaderLoader = OPTIFINE;
        } else {
            cachedShaderLoader = UNKNOWN;
        }

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
            return cachedMCVersion;
        }
        
        try {
            File shaderFile = findShaderLoaderFile();
            if (shaderFile == null) {
                cachedMCVersion = UNKNOWN;
                return cachedMCVersion;
            }

            String fileName = shaderFile.getName();
            String lowerFileName = fileName.toLowerCase();
            String extractedVersion = null;

            // Handle OptiFine format: OptiFine_1.18.1_HD_U_H6.jar
            if (lowerFileName.startsWith("optifine_")) {
                String[] parts = fileName.split("_");
                if (parts.length >= 2) {
                    extractedVersion = parts[1]; // Return the version part (1.18.1)
                }
            }
            // Handle Iris format: iris-fabric-1.8.8+mc1.21.4.jar
            else if (lowerFileName.startsWith("iris")) {
                int mcIndex = lowerFileName.indexOf("+mc");
                if (mcIndex != -1) {
                    // Extract version after "+mc" until the next non-version character
                    String versionPart = lowerFileName.substring(mcIndex + 3);
                    // Find end of version (next dot that's not part of version number)
                    int endIndex = versionPart.indexOf(".jar");
                    if (endIndex != -1) {
                        extractedVersion = versionPart.substring(0, endIndex);
                    }
                }
            }
            // Handle Oculus format: oculus-mc1.20.1-1.8.0.jar
            else if (lowerFileName.startsWith("oculus")) {
                int mcIndex = lowerFileName.indexOf("-mc");
                if (mcIndex != -1) {
                    // Extract version after "-mc" until the next dash
                    String afterMc = lowerFileName.substring(mcIndex + 3);
                    int dashIndex = afterMc.indexOf("-");
                    if (dashIndex != -1) {
                        extractedVersion = afterMc.substring(0, dashIndex);
                    }
                }
            }

            // Validate the extracted version format
            if (extractedVersion != null) {
                Matcher matcher = VERSION_PATTERN.matcher(extractedVersion);
                if (matcher.matches()) {
                    cachedMCVersion = extractedVersion;
                    return cachedMCVersion;
                } else {
                    EuphoriaPatcher.log(1, 0, "Invalid version format detected: " + extractedVersion);
                }
            }

            cachedMCVersion = UNKNOWN;
            return cachedMCVersion;
        } catch (Exception e) {
            EuphoriaPatcher.log(2, 0, "Error extracting Minecraft version: " + e.getMessage());
            cachedMCVersion = UNKNOWN;
            return cachedMCVersion;
        }
    }

    /**
     * Compares two Minecraft versions to determine if the first version is greater than or equal to the second.
     * For example, isVersionGreaterOrEqual("1.21.2", "1.21.1") returns true.
     *
     * @param version The version to check (e.g., "1.21.2")
     * @param minVersion The minimum required version (e.g., "1.21.1")
     * @return true if version >= minVersion, false otherwise or if any version is invalid
     */
    public static boolean isVersionGreaterOrEqual(String version, String minVersion) {
        if (version == null || minVersion == null || UNKNOWN.equals(version) || UNKNOWN.equals(minVersion)) {
            return false;
        }

        try {
            int versionInt = convertVersionToInt(version);
            int minVersionInt = convertVersionToInt(minVersion);

            return versionInt >= minVersionInt;
        } catch (Exception e) {
            EuphoriaPatcher.log(1, 0, "Error comparing versions: " + e.getMessage());
            return false;
        }
    }

    /**
     * Converts a Minecraft version string (e.g., "1.21.1") to an integer representation.
     * The format is: 1MMRR where MM is the minor version and RR is the release version.
     * Examples:
     * - "1.6.8" -> 10608
     * - "1.7.10" -> 10710
     * - "1.21.1" -> 12101
     *
     * @param versionString The version string to convert (e.g., "1.21.1")
     * @return The integer representation of the version
     * @throws IllegalArgumentException if the version format is invalid
     */
    private static int convertVersionToInt(String versionString) {
        Matcher matcher = VERSION_PATTERN.matcher(versionString);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid version format: " + versionString);
        }

        // Extract parts from 1.XX.YY format
        int minor = Integer.parseInt(matcher.group(1));
        int release = Integer.parseInt(matcher.group(2));

        // Convert to format 1MMRR
        return 10000 + (minor * 100) + release;
    }

    /**
     * Utility method to check if the detected shader loader's Minecraft version is
     * greater than or equal to a specific version.
     *
     * @param minVersion The minimum required version (e.g., "1.21.1")
     * @return true if the detected version >= minVersion, false otherwise
     */
    public static boolean isMinecraftVersionAtLeast(String minVersion) {
        String currentVersion = getShaderLoaderMCVersion();
        return isVersionGreaterOrEqual(currentVersion, minVersion);
    }
}