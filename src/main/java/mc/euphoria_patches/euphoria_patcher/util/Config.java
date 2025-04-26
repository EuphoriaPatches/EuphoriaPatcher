package mc.euphoria_patches.euphoria_patcher.util;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Config {
    private static final Path CONFIG_PATH = EuphoriaPatcher.configDirectory.resolve("euphoria_patcher.properties");
    private static final Properties properties = new Properties();
    private static FileTime lastModified = null;
    private static boolean watcherActive = false;
    private static ScheduledExecutorService scheduler;
    private static final Map<String, ConfigOptionMetadata> CONFIG_METADATA = new HashMap<>();

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[Config] " + message);
    }

    public static void createConfig() {
        try {
            Files.createFile(CONFIG_PATH);
            writeInitialConfig();
            EuphoriaPatcher.log(0, "Successfully created config file");
        } catch (IOException e) {
            EuphoriaPatcher.log(3, 0, "Error creating config file: " + e.getMessage());
        }
    }

    private static void writeInitialConfig() throws IOException {
        try (FileWriter writer = new FileWriter(String.valueOf(CONFIG_PATH), false)) {
            writer.write("# This file stores configuration options for the Euphoria Patcher mod\n");
            writer.write("# Made for version " + EuphoriaPatcher.PATCH_VERSION.replace("_", "") + "\n");
            writer.write("# Thank you for using Euphoria Patches - SpacEagle17\n");
        }
    }

    public static void updateVersionLine() {
        try {
            List<String> lines = Files.readAllLines(CONFIG_PATH, StandardCharsets.UTF_8);
            boolean versionLineFound = false;

            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith("# Made for version")) {
                    if (lines.get(i).contains(EuphoriaPatcher.PATCH_VERSION.replace("_", ""))) return;
                    lines.set(i, "# Made for version " + EuphoriaPatcher.PATCH_VERSION.replace("_", ""));
                    versionLineFound = true;
                    break;
                }
            }

            if (!versionLineFound) {
                int headerIndex = lines.indexOf("# This file stores configuration options for the Euphoria Patcher mod");
                if (headerIndex >= 0) {
                    lines.add(headerIndex + 1, "# Made for version " + EuphoriaPatcher.PATCH_VERSION.replace("_", ""));
                }
            }

            Files.write(CONFIG_PATH, lines, StandardCharsets.UTF_8);
            debugLog("Successfully updated version info in config file");
        } catch (IOException e) {
            EuphoriaPatcher.log(3, 0, "Error updating config file with version: " + e.getMessage());
        }
    }

    public static void writeConfig(String option, String value, String description) {
        try {
            if (!Files.exists(CONFIG_PATH)) {
                createConfig();
            } else {
                updateVersionLine(); // Always update the version line
            }
            loadProperties();
            
            // Check if the option already exists
            boolean optionExists = properties.containsKey(option);
            String existingValue = properties.getProperty(option);
            boolean valueChanged = !value.equals(existingValue);

            List<String> lines = Files.readAllLines(CONFIG_PATH, StandardCharsets.UTF_8);
            if (!optionExists) {
                // Add new option with description (existing logic)
                try (FileWriter writer = new FileWriter(String.valueOf(CONFIG_PATH), false)) {
                    // Write existing lines
                    for (String line : lines) {
                        writer.write(line + "\n");
                    }

                    // Add new configuration
                    writer.write("\n"); // Add newline before new entry
                    if (description != null) {
                        String[] descLines = description.split("\n");
                        for (String line : descLines) {
                            writer.write("# " + line + "\n");
                        }
                    }
                    writer.write(option + "=" + value + "\n");
                    debugLog("Successfully wrote to config file: " + option + "=" + value);
                }
            } else if (valueChanged) {
                // Only update if the value actually changed
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line.startsWith(option + "=")) {
                        lines.set(i, option + "=" + value);
                        break;
                    }
                }
                Files.write(CONFIG_PATH, lines, StandardCharsets.UTF_8);
                debugLog("Updated existing config option: " + option + "=" + value);
            }
            
            // Update the property in memory as well
            properties.setProperty(option, value);
            
            // Update last modified time
            lastModified = Files.getLastModifiedTime(CONFIG_PATH);
        } catch (IOException e) {
            EuphoriaPatcher.log(3, 0, "Error writing to config file: " + e.getMessage());
        }
    }

    public static String readWriteConfig(String optionName, String defaultValue, String description) {
        // Store metadata when config is read
        CONFIG_METADATA.put(optionName, new ConfigOptionMetadata(optionName, defaultValue, description));
        
        // Make sure properties are loaded
        if (properties.isEmpty() && Files.exists(CONFIG_PATH)) {
            loadProperties();
        }
        
        // Check if the property already exists in the file
        String existingValue = properties.getProperty(optionName);
        
        if (existingValue == null) {
            // Only write the default if the option doesn't exist yet
            writeConfig(optionName, defaultValue, description);
            return defaultValue;
        } else {
            // Option already exists, just return the existing value
            return existingValue;
        }
    }

    public static void loadProperties() {
        try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
            properties.load(in);
            lastModified = Files.getLastModifiedTime(CONFIG_PATH);
        } catch (IOException e) {
            EuphoriaPatcher.log(3, 0, "Error loading properties: " + e.getMessage());
        }
    }

    public static void startConfigWatcher() {
        if (watcherActive) return;

        watcherActive = true;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "EuphoriaConfigWatcher");
            thread.setDaemon(true);
            return thread;
        });

        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (Files.exists(CONFIG_PATH)) {
                    FileTime currentModified = Files.getLastModifiedTime(CONFIG_PATH);
                    if (!currentModified.equals(lastModified)) {
                        debugLog("Config file changed, reloading settings");
                        loadProperties();
                        EuphoriaPatcher instance = EuphoriaPatcher.getInstance();
                        if (instance != null) instance.configStuff();
                    }
                }
            } catch (IOException ignored) {}
        }, 10, 10, TimeUnit.SECONDS);
    }

    public static void stopConfigWatcher() {
        if (watcherActive && scheduler != null) {
            scheduler.shutdown();
            watcherActive = false;
        }
    }

    public static Map<String, ConfigOptionMetadata> getConfigMetadata() {
        return CONFIG_METADATA;
    }

    public static class ConfigOptionMetadata {
        public final String key;
        public final String defaultValue;
        public final String description;
        public final String displayName;
        
        public ConfigOptionMetadata(String key, String defaultValue, String description) {
            this.key = key;
            this.defaultValue = defaultValue;
            this.description = description;
            
            // Generate a display name from the key
            // e.g., "doPopUpLogging" → "Popup Logging"
            if (key.startsWith("do") && key.length() > 2) {
                StringBuilder result = new StringBuilder();
                // Skip the "do" prefix
                for (int i = 2; i < key.length(); i++) {
                    char c = key.charAt(i);
                    if (i == 2) {
                        result.append(Character.toUpperCase(c));
                    } else if (Character.isUpperCase(c)) {
                        result.append(' ').append(c);
                    } else {
                        result.append(c);
                    }
                }
                this.displayName = result.toString();
            } else {
                this.displayName = key;
            }
        }
    }
}