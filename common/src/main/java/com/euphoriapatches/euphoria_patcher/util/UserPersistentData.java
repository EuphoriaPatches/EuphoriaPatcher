package com.euphoriapatches.euphoria_patcher.util;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.util.mod.ModLoaderSpecifics;
import com.google.gson.*;
import com.euphoriapatches.euphoria_patcher.config.Config;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Handles persistent storage of shader data in a JSON file
 */
public class UserPersistentData {
    private static final Path DATA_FILE = Config.CONFIG_DIR.resolve(".data.json");
    private static final Path LEGACY_DATA_FILE = Config.CONFIG_DIR.resolve("data.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Cache for loaded data to avoid repeated disk I/O
    private static PersistentShaderData cachedData = null;

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[UserPersistentData] " + message);
    }

    /**
     * Enum representing fields that can be saved/loaded
     */
    public enum DataField {
        STYLE_REIMAGINED("styleReimagined"),
        STYLE_UNBOUND("styleUnbound"),
        SHADER_HASH("shaderHash"),
        SUPPORT_EP_BUTTON("supportEPButtonVisible"),
        EP_VERSION("EPVersion"),
        ALTERNATIVE_SHADER_NAMES("alternativeShaderNames"),
        FIRST_EP_OPTIONS_TIMESTAMP("firstEPOptionsTimestamp"),
        TIMES_SETTINGS_CHANGED("timesSettingsChanged"),
        CLICKED_SUPPORT_POPUP("clickedSupportPopup");

        private final String jsonKey;

        DataField(String jsonKey) {
            this.jsonKey = jsonKey;
        }

        public String getJsonKey() {
            return jsonKey;
        }

        /**
         * Get all allowed JSON keys as a set
         * @return Set of allowed JSON key names
         */
        public static java.util.Set<String> getAllowedKeys() {
            java.util.Set<String> keys = new java.util.HashSet<>();
            for (DataField field : values()) {
                keys.add(field.getJsonKey());
            }
            return keys;
        }
    }

    /**
     * Data class to hold persistent shader information
     */
    public static class PersistentShaderData {
        public Boolean styleReimagined;
        public Boolean styleUnbound;
        public String shaderHash;
        public Boolean supportEPButtonVisible;
        public String EPVersion;
        public String alternativeShaderNames;
        public String firstEPOptionsTimestamp;
        public Integer timesSettingsChanged;
        public Boolean clickedSupportPopup;

        public PersistentShaderData() {
            this.styleReimagined = null;
            this.styleUnbound = null;
            this.shaderHash = null;
            this.supportEPButtonVisible = null;
            this.EPVersion = null;
            this.alternativeShaderNames = null;
            this.firstEPOptionsTimestamp = null;
            this.timesSettingsChanged = null;
            this.clickedSupportPopup = null;
        }
    }

    /**
     * Helper class to pair a field with its value for saving
     */
    public static class SaveData {
        public final DataField field;
        public final Object value;

        private SaveData(DataField field, Object value) {
            this.field = field;
            this.value = value;
        }

        public static SaveData of(DataField field, Object value) {
            return new SaveData(field, value);
        }
    }

    /**
     * Save specific fields to the .data.json file. Only updates the specified fields,
     * preserving other existing data.
     *
     * @param updates One or more field updates to save
     */
    public static void save(SaveData... updates) {
        if (updates == null || updates.length == 0) {
            debugLog("No updates provided to save");
            return;
        }

        debugLog("Saving " + updates.length + " field(s) to data file");

        try {
            removeLegacyDataFileIfPresent();

            // Ensure config directory exists
            if (!Files.exists(Config.CONFIG_DIR)) {
                Files.createDirectories(Config.CONFIG_DIR);
                debugLog("Created config directory: " + Config.CONFIG_DIR);
            }

            // Load existing JSON or create new object
            JsonObject jsonObject;
            if (dataFileExists()) {
                try (Reader reader = new InputStreamReader(
                        Files.newInputStream(DATA_FILE), StandardCharsets.UTF_8)) {
                    @SuppressWarnings("null")
                    JsonElement element = GSON.fromJson(reader, JsonElement.class);
                    jsonObject = (element != null && element.isJsonObject()) ? element.getAsJsonObject() : new JsonObject();
                }
            } else {
                jsonObject = new JsonObject();
            }

            // Remove any old/unknown fields that are no longer allowed
            java.util.Set<String> allowedKeys = DataField.getAllowedKeys();
            java.util.Set<String> keysToRemove = new java.util.HashSet<>();
            for (String key : jsonObject.keySet()) {
                if (!allowedKeys.contains(key)) {
                    keysToRemove.add(key);
                }
            }
            if (!keysToRemove.isEmpty()) {
                debugLog("Removing " + keysToRemove.size() + " old/unknown field(s): " + keysToRemove);
                for (String key : keysToRemove) {
                    jsonObject.remove(key);
                }
            }

            // Update specified fields only using reflection for better maintainability
            for (SaveData update : updates) {
                String jsonKey = update.field.getJsonKey();
                try {
                    // Get the field type from PersistentShaderData class
                    Field dataField = PersistentShaderData.class.getField(jsonKey);
                    Class<?> fieldType = dataField.getType();

                    // Add property based on the field type
                    if (fieldType == Boolean.class) {
                        jsonObject.addProperty(jsonKey, (Boolean) update.value);
                    } else if (fieldType == String.class) {
                        jsonObject.addProperty(jsonKey, (String) update.value);
                    } else if (fieldType == Integer.class) {
                        jsonObject.addProperty(jsonKey, (Number) update.value);
                    } else {
                        debugLog("Unsupported field type for " + update.field + ": " + fieldType);
                        continue;
                    }
                    debugLog("Updating " + update.field + " to " + update.value);
                } catch (NoSuchFieldException e) {
                    debugLog("Field not found in PersistentShaderData: " + jsonKey);
                } catch (ClassCastException e) {
                    debugLog("Invalid type for field " + update.field + ": " + e.getMessage());
                }
            }

            // Write to file
            setDataFileWritable();
            try (Writer writer = new OutputStreamWriter(
                    Files.newOutputStream(DATA_FILE), StandardCharsets.UTF_8)) {
                GSON.toJson(jsonObject, writer);
                ensureDataFileHidden();
                debugLog("Successfully saved data to " + DATA_FILE);
                cachedData = null; // Invalidate cache after save
            }
        } catch (IOException e) {
            debugLog("Error saving data: " + e.getMessage());
        } catch (ClassCastException e) {
            debugLog("Invalid type for field update: " + e.getMessage());
        } finally {
            setDataFileWritable();
        }
    }

    /**
     * Load shader data from the .data.json file
     * @return PersistentShaderData object with loaded data, or default values if file doesn't exist
     */
    public static PersistentShaderData load() {
        // Return cached data if available
        if (cachedData != null) {
            debugLog("Returning cached shader data");
            return cachedData;
        }

        removeLegacyDataFileIfPresent();

        debugLog("Loading shader data from " + DATA_FILE);

        if (!Files.exists(DATA_FILE)) {
            debugLog("Data file does not exist, returning default values");
            cachedData = new PersistentShaderData();
            return cachedData;
        }

        try (Reader reader = new InputStreamReader(
                Files.newInputStream(DATA_FILE), StandardCharsets.UTF_8)) {
            @SuppressWarnings("null")
            PersistentShaderData data = GSON.fromJson(reader, PersistentShaderData.class);

            cachedData = data;
            return cachedData;
        } catch (IOException e) {
            debugLog("Error loading shader data: " + e.getMessage());
            cachedData = new PersistentShaderData();
            return cachedData;
        } catch (JsonSyntaxException e) {
            debugLog("Invalid JSON format in data file: " + e.getMessage());
            cachedData = new PersistentShaderData();
            return cachedData;
        }
    }

    /**
     * Check if the data file exists
     * @return true if the data file exists
     */
    public static boolean dataFileExists() {
        removeLegacyDataFileIfPresent();
        return Files.exists(DATA_FILE);
    }

    public static void deleteDataFile() {
        removeLegacyDataFileIfPresent();
        debugLog("Deleting data file: " + DATA_FILE);
        try {
            if (Files.exists(DATA_FILE)) {
                setDataFileWritable();
                Files.delete(DATA_FILE);
                debugLog("Successfully deleted data file");
                cachedData = null; // Invalidate cache after delete
                return;
            }
            debugLog("Data file does not exist, nothing to delete");
        } catch (IOException e) {
            debugLog("Error deleting data file: " + e.getMessage());
        }
    }

    /**
     * Ensure file is writeable to fix the mess I did in 1.9.0
     */
    private static void setDataFileWritable() {
        File file = DATA_FILE.toFile();
        if (!file.exists()) {
            return;
        }

        boolean isSetWriteable = file.setWritable(true, false);

        if (isSetWriteable) {
            debugLog("Set writable state to " + true + " for " + DATA_FILE);
        }  else {
            debugLog("Could not set data file writable=" + true + ": " + DATA_FILE);
        }
    }

    /**
     * Removes legacy non-hidden data.json if it exists.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void removeLegacyDataFileIfPresent() {
        try {
            if (!Files.exists(LEGACY_DATA_FILE)) {
                return;
            }

            File legacyFile = LEGACY_DATA_FILE.toFile();
            legacyFile.setWritable(true, false);
            Files.delete(LEGACY_DATA_FILE);
            debugLog("Removed legacy data file: " + LEGACY_DATA_FILE);
        } catch (IOException e) {
            debugLog("Could not remove legacy data file " + LEGACY_DATA_FILE + ": " + e.getMessage());
        }
    }

    /**
     * Keeps the data file hidden on platforms that support hidden-file attributes.
     */
    private static void ensureDataFileHidden() {
        if (!Files.exists(DATA_FILE)) {
            return;
        }

        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return;
        }

        try {
            Files.setAttribute(DATA_FILE, "dos:hidden", true, LinkOption.NOFOLLOW_LINKS);
        } catch (UnsupportedOperationException e) {
            debugLog("Hidden attribute unsupported for " + DATA_FILE);
        } catch (IOException e) {
            debugLog("Could not set hidden attribute for " + DATA_FILE + ": " + e.getMessage());
        }
    }

    /**
     * Convenience method to save both shader styles at once
     *
     * @param styleReimagined Whether Reimagined style is used
     * @param styleUnbound    Whether Unbound style is used
     */
    public static void saveShaderStyles(boolean styleReimagined, boolean styleUnbound) {
        save(
                SaveData.of(DataField.STYLE_REIMAGINED, styleReimagined),
                SaveData.of(DataField.STYLE_UNBOUND, styleUnbound)
        );
    }

    /**
     * Records the timestamp when first time shaderpack option menu was opened with EP.
     */
    public static void recordFirstEPOptionsTimestampIfAbsent() {
        PersistentShaderData data = load();
        if (data.firstEPOptionsTimestamp != null && !data.firstEPOptionsTimestamp.trim().isEmpty()) {
            debugLog("First EP options timestamp already recorded: " + data.firstEPOptionsTimestamp);
            return;
        }

        String timestamp = java.time.Instant.now().toString();
        debugLog("Recording first EP options timestamp: " + timestamp);
        save(SaveData.of(DataField.FIRST_EP_OPTIONS_TIMESTAMP, timestamp));
    }

    /**
     * Returns how many times the user has changed settings while EP was active
     */
    public static int getTimesSettingsChanged() {
        Integer value = load().timesSettingsChanged;
        return value == null ? 0 : value;
    }

    // Increment counter by 1
    public static void incrementTimesSettingsChanged() {
        int updated = getTimesSettingsChanged() + 1;
        debugLog("Incrementing timesSettingsChanged to " + updated);
        save(SaveData.of(DataField.TIMES_SETTINGS_CHANGED, updated));
    }

    /**
     * Returns the set of alternative shader names that have already been recorded.
     */
    public static Set<String> getKnownAlternativeShaderNames() {
        return parseAlternativeShaderNames(load().alternativeShaderNames);
    }

    /**
     * Checks whether an alternative shader name has already been recorded.
     */
    public static boolean hasKnownAlternativeShaderName(String name) {
        String normalizedName = normalizeAlternativeShaderName(name);
        return normalizedName != null && getKnownAlternativeShaderNames().contains(normalizedName);
    }

    /**
     * Records one or more alternative shader names as having been created before.
     */
    public static void rememberAlternativeShaderNames(String... names) {
        if (names == null || names.length == 0) {
            return;
        }

        Set<String> knownNames = new TreeSet<>(getKnownAlternativeShaderNames());
        boolean changed = false;

        for (String name : names) {
            String normalizedName = normalizeAlternativeShaderName(name);
            if (normalizedName != null && knownNames.add(normalizedName)) {
                changed = true;
            }
        }

        if (changed) {
            save(SaveData.of(DataField.ALTERNATIVE_SHADER_NAMES, String.join(",", knownNames)));
        }
    }

    /**
     * Removes remembered alternative shader names that are no longer configured.
     */
    public static void pruneAlternativeShaderNames(Set<String> allowedNames) {
        Set<String> normalizedAllowedNames = new TreeSet<>();
        if (allowedNames != null) {
            for (String name : allowedNames) {
                String normalizedName = normalizeAlternativeShaderName(name);
                if (normalizedName != null) {
                    normalizedAllowedNames.add(normalizedName);
                }
            }
        }

        Set<String> knownNames = new TreeSet<>(getKnownAlternativeShaderNames());
        if (knownNames.equals(normalizedAllowedNames)) {
            return;
        }

        knownNames.retainAll(normalizedAllowedNames);
        save(SaveData.of(DataField.ALTERNATIVE_SHADER_NAMES, String.join(",", knownNames)));
    }

    private static Set<String> parseAlternativeShaderNames(String rawNames) {
        Set<String> names = new HashSet<>();

        if (rawNames == null || rawNames.trim().isEmpty()) {
            return names;
        }

        for (String name : rawNames.split(",")) {
            String normalizedName = normalizeAlternativeShaderName(name);
            if (normalizedName != null) {
                names.add(normalizedName);
            }
        }

        return names;
    }

    private static String normalizeAlternativeShaderName(String name) {
        if (name == null) {
            return null;
        }

        String trimmedName = name.trim();
        return trimmedName.isEmpty() ? null : trimmedName;
    }

    /**
     * Convenience method to reset both shader styles to false
     */
    public static void resetShaderStyles() {
        save(
                SaveData.of(DataField.STYLE_REIMAGINED, false),
                SaveData.of(DataField.STYLE_UNBOUND, false)
        );
    }

    /**
     * Checks if the current patch version matches the stored version.
     * If versions don't match, updates the stored version.
     *
     * @return true if version has changed (incorrect), false if version matches (correct)
     */
    public static boolean isIncorrectVersion() {
        debugLog("Checking patch version");

        String currentVersion = EuphoriaPatcher.PATCH_VERSION.replace("_", "");
        debugLog("Current patch version: " + currentVersion);

        if (!dataFileExists()) {
            debugLog("Data file does not exist, will create with current patch version");
            save(SaveData.of(DataField.EP_VERSION, currentVersion));
            return false; // Version is now correct as we just saved it
        }

        PersistentShaderData data = load();

        if (data.EPVersion == null) {
            debugLog("No patch version stored in data file, will save current one");
            save(SaveData.of(DataField.EP_VERSION, currentVersion));
            return true; // Version was incorrect (missing) and has now been set
        }

        if (!data.EPVersion.equals(currentVersion)) {
            debugLog("Patch version mismatch! Stored: " + data.EPVersion + ", Current: " + currentVersion);
            save(SaveData.of(DataField.EP_VERSION, currentVersion));
            return true; // Version was incorrect (has changed)
        }

        debugLog("Patch version matches: " + currentVersion);
        return false; // Version is correct
    }

    /**
     * Validates that the stored shaderpacks directory hash matches the current one.
     * If the hash differs, deletes the data file to prevent cross-user data usage.
     * If no hash is stored, saves the current one.
     */
    public static void validateShaderDataHash() {
        debugLog("Validating shaderpacks directory hash");
        setDataFileWritable();

        // The default path, not the live one: profiles sharing a config dir (Dawn) can redirect shaderpacks to
        // different folders, which would reset the data file on every switch. Still unique per user and instance.
        String currentShadersDir = ModLoaderSpecifics.defaultShaderpacks().toString();
        debugLog("Current shaderpacks directory: " + currentShadersDir);

        String currentShaderHash = HashUtils.calculateSHA256(currentShadersDir);
        if (currentShaderHash == null) {
            debugLog("Failed to calculate hash for current shaderpacks directory");
            return;
        }
        debugLog("Current shaderpacks directory hash: " + currentShaderHash);

        if (!dataFileExists()) {
            debugLog("Data file does not exist, will create with current shaderpacks hash");
            save(SaveData.of(DataField.SHADER_HASH, currentShaderHash));
            return;
        }

        PersistentShaderData data = load();

        if (data.shaderHash == null) {
            debugLog("No shaderpacks hash stored in data file, will save current one");
            save(SaveData.of(DataField.SHADER_HASH, currentShaderHash));
            return;
        }

        if (!data.shaderHash.equals(currentShaderHash)) {
            debugLog("Shaderpacks hash mismatch! Stored: " + data.shaderHash + ", Current: " + currentShaderHash);
            debugLog("Deleting data file to ensure user-specific data");
            deleteDataFile();
            save(SaveData.of(DataField.SHADER_HASH, currentShaderHash));
        } else {
            debugLog("Current shader data directory hash: " + data.shaderHash);
            debugLog("Shaderpacks hash matches, data file is valid for this user");
        }
    }
}
