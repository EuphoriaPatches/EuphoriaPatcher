package com.euphoriapatches.euphoria_patcher.features.shader_settings;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles conversion of shader settings from one format to another
 * during shader config file updates.
 */
public class ShaderSettingsConverter {
    private static final String SETTINGS_CONVERSIONS_FILE = "settingsConversions.txt";
    private static final Map<Pattern, String> conversionRules = new HashMap<>();
    private static boolean initialized = false;

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[ShaderSettingsConverter] " + message);
    }

    public static void initialize() {
        if (initialized) {
            return;
        }

        loadConversionRules();
        initialized = true;
    }

    private static void loadConversionRules() {
        try (InputStream inputStream = ShaderSettingsConverter.class.getClassLoader().getResourceAsStream(SETTINGS_CONVERSIONS_FILE)) {
            if (inputStream == null) {
                debugLog("Settings conversions file not found: " + SETTINGS_CONVERSIONS_FILE);
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                int lineNumber = 0;

                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    line = line.trim();

                    // Skip comments and empty lines
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }

                    // Split by the arrow symbol
                    String[] parts = line.split("->", 2);
                    if (parts.length < 2) {
                        debugLog("Invalid conversion rule at line " + lineNumber + ": " + line);
                        continue;
                    }

                    String from = parts[0].trim();
                    String to = parts[1].trim();

                    // Special handling for wildcard (*) replacements
                    if (from.contains("=*")) {
                        // Convert "SETTING=*" to regex pattern that captures the value
                        String settingName = from.substring(0, from.indexOf("="));
                        String toSettingName = to.substring(0, to.indexOf("="));
                        from = "^" + Pattern.quote(settingName) + "=(.*)$";
                        to = toSettingName + "=$1";
                    } else {
                        // Escape special regex characters in the pattern
                        from = "^" + Pattern.quote(from) + "$";
                    }

                    // Create pattern
                    Pattern pattern = Pattern.compile(from, Pattern.CASE_INSENSITIVE);
                    conversionRules.put(pattern, to);
                    debugLog("Added conversion rule: " + from + " -> " + to);
                }

                debugLog("Loaded " + conversionRules.size() + " conversion rules");
            }
        } catch (IOException e) {
            EuphoriaPatcher.log(2, "Error loading settings conversion rules: " + e.getMessage());
        } catch (Exception e) {
            EuphoriaPatcher.log(2, "Unexpected error loading conversion rules: " + e.getMessage());
        }
    }

    public static String convertLine(String line) {
        if (!initialized) {
            initialize();
        }

        String trimmedLine = line.trim();

        // Skip empty lines and comments
        if (trimmedLine.isEmpty() || trimmedLine.startsWith("#") || trimmedLine.startsWith("//")) {
            return line;
        }

        // Try to match and apply each rule
        for (Map.Entry<Pattern, String> rule : conversionRules.entrySet()) {
            Matcher matcher = rule.getKey().matcher(trimmedLine);

            if (matcher.matches()) {
                String result = matcher.replaceAll(rule.getValue());
                debugLog("MATCH FOUND! Converted: '" + trimmedLine + "' -> '" + result + "'");
                return result;
            }
        }
        return line;
    }

    public static List<String> convertLines(List<String> lines) {
        if (!initialized) {
            initialize();
        }

        debugLog("Starting conversion of " + lines.size() + " lines");
        int modifiedLines = 0;
        List<String> result = new ArrayList<>(lines.size());

        for (String line : lines) {
            String converted = convertLine(line);
            if (!converted.equals(line)) {
                modifiedLines++;
            }
            result.add(converted);
        }

        if (modifiedLines > 0) {
            debugLog("Conversion completed. Modified " + modifiedLines + " out of " + lines.size() + " lines");
        } else {
            debugLog("Conversion completed. No lines required modification");
        }

        return result;
    }
}
