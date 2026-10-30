package com.euphoriapatches.euphoria_patcher.util;

import com.euphoriapatches.euphoria_patcher.integration.ShaderLoader;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

public class Dimensions {
    public static final String SHADER_DIMENSION_PROPERTIES_LOCATION = "shaders/dimension.properties";

    private static final Pattern PREPROCESSOR_LINE = Pattern.compile("^\\s*#(if|ifdef|ifndef|elif|else|endif)\\b");

    // dimension.properties key -> dimension category
    private static final Map<String, String> CATEGORY_KEYS = new LinkedHashMap<>();
    static {
        CATEGORY_KEYS.put("dimension.world0", "overworld");
        CATEGORY_KEYS.put("dimension.world-1", "nether");
        CATEGORY_KEYS.put("dimension.world1", "end");
    }

    // Cache to store dimension ID -> dimension category mappings
    private static final Map<String, String> dimensionCache = new HashMap<>();

    // Cache to store whether a dimension ID is in mappings
    private static final Map<String, Boolean> dimensionInMappingsCache = new HashMap<>();

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[Dimensions] " + message);
    }

    /**
     * Gets the dimension category based on dimension ID and shader properties.
     * Returns mapped categories for recognized dimensions, or a sanitized version
     * of the dimension ID for unrecognized dimensions (safe for C-style defines).
     *
     * @param currentDimensionId The dimension identifier string
     * @return String representing the dimension (e.g., "overworld", "nether", "end", or sanitized dimension ID)
     */
    public static String getCurrentDimension(String currentDimensionId) {
        if (currentDimensionId == null) {
            debugLog("Dimension ID is null, defaulting to 'overworld'");
            return "overworld";
        }

        // Check cache first for early exit
        if (dimensionCache.containsKey(currentDimensionId)) {
            String cached = dimensionCache.get(currentDimensionId);
            debugLog("Found dimension in cache: " + cached);
            return cached;
        }

        // Get current shader pack path
        Path shaderpackPath = ShaderLoader.getCurrentShaderpackPath();
        if (shaderpackPath == null) {
            debugLog("No active shaderpack found");
        } else {
            debugLog("Active shaderpack: " + shaderpackPath.getFileName());
        }

        String result;

        if (shaderpackPath != null) {
            // Parse dimension mappings
            Map<String, String> dimensionMappings = parseDimensionProperties(shaderpackPath);
            debugLog("Parsed " + dimensionMappings.size() + " dimension mappings from properties file");

            // Check if the dimension is directly mapped
            if (dimensionMappings.containsKey(currentDimensionId)) {
                result = dimensionMappings.get(currentDimensionId);
                debugLog("Found mapping for current dimension: " + result);
                dimensionCache.put(currentDimensionId, result);
                return result;
            } else {
                debugLog("No specific mapping found for dimension: " + currentDimensionId);
            }
        }

        // Check if it's a vanilla dimension
        String vanillaCategory = getVanillaDimensionCategory(currentDimensionId);
        if (vanillaCategory != null) {
            debugLog("Identified as vanilla " + vanillaCategory);
            dimensionCache.put(currentDimensionId, vanillaCategory);
            return vanillaCategory;
        }

        // Sanitize dimension ID for use as a C-style define
        result = sanitizeDimensionId(currentDimensionId);
        debugLog("No mapping found, returning sanitized dimension ID: " + result);
        dimensionCache.put(currentDimensionId, result);
        return result;
    }

    /**
     * Checks if a dimension ID is recognized in the shader pack mappings or as a vanilla dimension.
     *
     * @param currentDimensionId The dimension identifier string
     * @return true if the dimension is in mappings, false otherwise
     */
    public static boolean isCurrentDimensionInMappings(String currentDimensionId) {
        if (currentDimensionId == null) {
            return false;
        }

        // Check cache first for early exit
        if (dimensionInMappingsCache.containsKey(currentDimensionId)) {
            debugLog("Dimension in mappings cache hit for: " + currentDimensionId);
            return dimensionInMappingsCache.get(currentDimensionId);
        }

        // Check vanilla dimensions
        if (getVanillaDimensionCategory(currentDimensionId) != null) {
            dimensionInMappingsCache.put(currentDimensionId, true);
            return true;
        }

        // Check shader pack mappings
        boolean isInMappings;
        Path shaderpackPath = ShaderLoader.getCurrentShaderpackPath();
        if (shaderpackPath != null) {
            Map<String, String> dimensionMappings = parseDimensionProperties(shaderpackPath);
            isInMappings = dimensionMappings.containsKey(currentDimensionId);
        } else {
            isInMappings = false;
        }

        dimensionInMappingsCache.put(currentDimensionId, isInMappings);
        return isInMappings;
    }

    /**
     * Sanitizes a dimension ID to be safe for use as a C-style define.
     * Replaces non-alphanumeric characters with underscores.
     *
     * @param dimensionId The dimension identifier string
     * @return Sanitized dimension ID
     */
    private static String sanitizeDimensionId(String dimensionId) {
        // Replace non-alphanumeric characters (including : and .) with underscores for C-style defines
        return dimensionId.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    /**
     * Checks if the given dimension ID is a vanilla dimension and returns its category.
     *
     * @param dimensionId The dimension identifier string
     * @return "overworld", "nether", or "end" if vanilla dimension, null otherwise
     */
    private static String getVanillaDimensionCategory(String dimensionId) {
        if (dimensionId == null) {
            return null;
        }

        switch (dimensionId) {
            case "minecraft:overworld":
                return "overworld";
            case "minecraft:the_nether":
                return "nether";
            case "minecraft:the_end":
                return "end";
            default:
                return null;
        }
    }

    /**
     * Parses the dimension.properties file to create mappings from dimension IDs to
     * dimension categories (overworld, nether, end).
     *
     * @param shaderPackPath The path to the active shader pack
     * @return Map of dimension IDs to dimension categories
     */
    static Map<String, String> parseDimensionProperties(Path shaderPackPath) {
        Map<String, String> dimensionMap = new HashMap<>();

        if (shaderPackPath == null) {
            debugLog("Shader pack path is null");
            return dimensionMap;
        }

        // Check if shader pack is a ZIP file
        if (shaderPackPath.toString().endsWith(".zip")) {
            debugLog("Reading from ZIP file: " + shaderPackPath.getFileName());
            try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(shaderPackPath.toFile())) {
                // Get the dimension.properties entry from the ZIP
                java.util.zip.ZipEntry entry = zipFile.getEntry(SHADER_DIMENSION_PROPERTIES_LOCATION);

                if (entry == null) {
                    debugLog("dimension.properties not found in ZIP");
                    return dimensionMap;
                }

                // Read the file from the ZIP
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(zipFile.getInputStream(entry), StandardCharsets.UTF_8))) {
                    return parseDimensionPropertiesFromReader(reader);
                }
            } catch (IOException e) {
                debugLog("Error reading dimension.properties from ZIP: " + e.getMessage());
            }
        } else {
            // Regular directory
            Path propertiesFile = shaderPackPath.resolve(SHADER_DIMENSION_PROPERTIES_LOCATION);

            if (Files.exists(propertiesFile)) {
                try (BufferedReader reader = Files.newBufferedReader(propertiesFile, StandardCharsets.UTF_8)) {
                    return parseDimensionPropertiesFromReader(reader);
                } catch (IOException e) {
                    debugLog("Error reading dimension.properties from directory: " + e.getMessage());
                }
            } else {
                debugLog("dimension.properties file not found at path: " + propertiesFile);
            }
        }

        return dimensionMap;
    }

    /**
     * Parse dimension properties from a reader.
     * <p>
     * Split at preprocessor to avoid lower lines overwriting  earlier ones.
     * The results are merged afterwards, so Nether IDs from both blocks are combined, End IDs likewise.
     *
     * @param reader The reader containing dimension.properties content
     * @return Map of dimension IDs to dimension categories
     * @throws IOException If there's an error reading from the reader
     */
    static Map<String, String> parseDimensionPropertiesFromReader(BufferedReader reader) throws IOException {
        Map<String, String> dimensionMap = new HashMap<>();
        StringBuilder segment = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            if (PREPROCESSOR_LINE.matcher(line).find()) {
                loadSegment(segment.toString(), dimensionMap);
                segment.setLength(0);
            } else {
                segment.append(line).append('\n');
            }
        }
        loadSegment(segment.toString(), dimensionMap);

        return dimensionMap;
    }

    private static void loadSegment(String segment, Map<String, String> dimensionMap) throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(segment));

        for (Map.Entry<String, String> entry : CATEGORY_KEYS.entrySet()) {
            String value = properties.getProperty(entry.getKey());
            if (value != null) {
                addDimensionsToMap(value, entry.getValue(), dimensionMap);
            }
        }
    }

    /**
     * Helper method to parse dimension IDs from a line and add them to the mapping
     *
     * @param dimensionsLine Line containing dimension IDs
     * @param targetCategory Category to map these dimensions to (overworld, nether, end)
     * @param dimensionMap Map to populate
     */
    private static void addDimensionsToMap(String dimensionsLine, String targetCategory, Map<String, String> dimensionMap) {
        String[] parts = dimensionsLine.split("\\s+");
        for (String part : parts) {
            if (!part.trim().isEmpty() && !part.equals("\\")) {
                if (part.equals("*")) {
                    // Wildcard for any unmapped dimension - handled separately if needed
                    continue;
                }
                dimensionMap.put(part, targetCategory);
            }
        }
    }
}
