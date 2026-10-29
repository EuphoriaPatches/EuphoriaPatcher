package com.euphoriapatches.euphoria_patcher.util;

import com.euphoriapatches.euphoria_patcher.config.ConfigHandler;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Checks Modrinth for a newer version of the mod.
 */
public class UpdateChecker {
    private static final String PROJECT_ID = "4H6sumDB";
    private static final String UPDATE_URL = "https://api.modrinth.com/v2/project/" + PROJECT_ID + "/version";
    private static final String MOD_VERSION = EuphoriaPatcher.PATCH_VERSION.replace("_","");
    private static final String USER_AGENT = "SpacEagle17/EuphoriaPatcher";
    private static final int TIMEOUT_MS = 10000;
    private static final long MAX_WAIT_MS = 30000;

    private static final AtomicBoolean CHECK_STARTED = new AtomicBoolean(false);
    private static final CountDownLatch CHECK_DONE = new CountDownLatch(1);

    // Written by the check thread before CHECK_DONE is released, read by everyone else after waiting for it
    private static volatile String NEW_MOD_VERSION = null;
    private static volatile String COMPLEMENTARY_VERSION = null;
    private static volatile boolean NEW_VERSION_AVAILABLE = false;
    private static volatile boolean IMPORTANT_UPDATE = false;
    private static volatile boolean PATCH_UPDATE_RECOMMENDED = false;
    // Set once a caller has waited the full MAX_WAIT_MS for a hanging check, so later callers don't wait again
    private static volatile boolean GAVE_UP_WAITING = false;

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[UpdateChecker] " + message);
    }

    /**
    * Checks if a new version of the mod is available.
    * @return true if a new version is available, false otherwise
    */
    public static boolean isUpdateAvailable() {
        awaitCheck();
        return NEW_VERSION_AVAILABLE;
    }

    /**
    * Returns the latest mod version, formatted "major.minor.patch".
    * @return The latest mod version, or null if it is not known (yet)
    */
    public static String getNewModVersion() {
        awaitCheck();
        return NEW_MOD_VERSION;
    }

    public static String getComplementaryVersion() {
        awaitCheck();
        return COMPLEMENTARY_VERSION;
    }

    /**
     * Checks if a newer version of Complementary Shaders is available.
     * @return true if a newer Complementary Shaders version is available, false otherwise
     */
    public static boolean isNewerCompVersionAvailable() {
        awaitCheck();
        String complementaryVersion = COMPLEMENTARY_VERSION;
        if (complementaryVersion == null || complementaryVersion.isEmpty()) {
            return false;
        }
        String currentCompVersion = EuphoriaPatcher.VERSION.replace("_r", "");
        boolean isNewer = VersionComparator.isNewerVersion(complementaryVersion, currentCompVersion);
        debugLog("Current Complementary Shaders version: " + currentCompVersion);
        debugLog("Latest Complementary Shaders version: " + complementaryVersion);
        debugLog("Is newer Complementary Shaders version available? " + isNewer);
        return isNewer;
    }

    /**
     * Determines if the user should update based on the type of update available.
     * @return true if an important update is available or a recommended patch update exists, false otherwise
     */
    public static boolean shouldUserUpdate() {
        return ConfigHandler.updateMode.equals(ConfigHandler.UpdateMode.ALL) ? isUpdateAvailable() : isUpdateAvailable() && (isImportantUpdate() || isPatchUpdateRecommended());
    }

    /**
     * Determines if the latest update is an important update by comparing major.minor version numbers.
     * An important update occurs when the major (X) or minor (Y) version number in X.Y.Z increases.
     * @return true if the update is important (X or Y increased), false if only patch version changed
     */
    public static boolean isImportantUpdate() {
        awaitCheck();
        return IMPORTANT_UPDATE;
    }

    /**
     * Determines if a recommended patch update is available in the current patch cycle.
     * Returns true if there's a recommended patch version (X.Y.Z) within the same X.Y cycle
     * that is newer than the current version.
     * In update mode ALL this is always false: every update is reported there anyway, so the big changelog download
     * that decides it is skipped.
     * @return true if a recommended patch update is available in the same major.minor version
     */
    public static boolean isPatchUpdateRecommended() {
        awaitCheck();
        return PATCH_UPDATE_RECOMMENDED;
    }

    /**
     * Starts the update check on a background thread. Only the first call does anything, so it is safe to call from
     * anywhere. Does not block.
     */
    public static void checkForUpdates() {
        if (ConfigHandler.updateMode.equals(ConfigHandler.UpdateMode.NONE)) {
            debugLog("Update check skipped, update checking mode set to: " + ConfigHandler.updateMode);
            return;
        }
        if (!CHECK_STARTED.compareAndSet(false, true)) {
            return;
        }
        debugLog("Starting update check...");
        debugLog("Current version: " + MOD_VERSION);

        Thread thread = new Thread(UpdateChecker::runCheck, "EuphoriaPatcher-UpdateChecker");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Makes sure the check has been started and gives it a bounded amount of time to finish.
     */
    private static void awaitCheck() {
        checkForUpdates();
        if (!CHECK_STARTED.get() || GAVE_UP_WAITING) {
            return; // Update checking is disabled, or the check hangs and was already waited for once
        }
        try {
            if (!CHECK_DONE.await(MAX_WAIT_MS, TimeUnit.MILLISECONDS)) {
                GAVE_UP_WAITING = true;
                debugLog("Update check has not finished within " + MAX_WAIT_MS + "ms, continuing without its result");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Gets the versions Modrinth has, newest first.
     */
    interface VersionSource {
        /**
         * @param includeChangelog whether the (much bigger) response should contain the changelogs
         * @return The versions, or null if they could not be fetched
         */
        JsonArray fetch(boolean includeChangelog) throws Exception;
    }

    private static void runCheck() {
        try {
            evaluate(MOD_VERSION, UpdateChecker::fetchVersions);
        } catch (Exception e) {
            String errorType = e.getClass().getSimpleName();
            String errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown error";
            EuphoriaPatcher.log(2, 0, "Update check failed: " + errorType + " - " + errorMessage);
            debugLog("Update check failed with " + errorType + ": " + errorMessage);
            debugLog("Full exception: " + e);
        } finally {
            CHECK_DONE.countDown();
        }
    }

    /**
     * Works out everything the getters report. Fetches the versions without changelogs first, the changelogs only if
     * they are needed to decide whether a patch update is recommended.
     * @param currentVersion The version of this mod, formatted "major.minor.patch"
     */
    static void evaluate(String currentVersion, VersionSource source) throws Exception {
        NEW_MOD_VERSION = null;
        COMPLEMENTARY_VERSION = null;
        NEW_VERSION_AVAILABLE = false;
        IMPORTANT_UPDATE = false;
        PATCH_UPDATE_RECOMMENDED = false;

        JsonArray versions = source.fetch(false);
        if (versions == null) {
            return;
        }
        debugLog("Fetched " + versions.size() + " versions from Modrinth");
        if (versions.size() == 0) {
            EuphoriaPatcher.log(2, 0, "No versions found on Modrinth.");
            return;
        }

        // The first version is the latest one
        String fullVersionNumber = versions.get(0).getAsJsonObject().get("version_number").getAsString();
        debugLog("Full version number from Modrinth: " + fullVersionNumber);
        String latestVersion = extractEuphoriaPatchesVersion(fullVersionNumber);
        NEW_MOD_VERSION = latestVersion;
        COMPLEMENTARY_VERSION = extractComplementaryVersion(fullVersionNumber);
        debugLog("Latest version from Modrinth: " + latestVersion);

        if (!VersionComparator.isNewerVersion(latestVersion, currentVersion)) {
            debugLog("Mod is up to date");
            EuphoriaPatcher.log(0, "The EuphoriaPatcher Mod is up to date");
            return;
        }

        NEW_VERSION_AVAILABLE = true;
        debugLog("New version available!");
        EuphoriaPatcher.log(2, 0, "A new version of the EuphoriaPatcher Mod is available: " + latestVersion);
        EuphoriaPatcher.log(2, 0, "Download it from Modrinth: " + EuphoriaPatcher.EP_DOWNLOAD_URL);
        EuphoriaPatcher.log(2, 0, "Current Version: " + currentVersion);

        int[] current = VersionComparator.parseVersionToIntArray(currentVersion);
        int[] latest = VersionComparator.parseVersionToIntArray(latestVersion);
        if (current == null || latest == null) {
            debugLog("Could not parse versions, current: " + currentVersion + ", latest: " + latestVersion);
            return;
        }

        IMPORTANT_UPDATE = latest[0] > current[0] || (latest[0] == current[0] && latest[1] > current[1]);
        debugLog((IMPORTANT_UPDATE ? "Important" : "Minor (patch)") + " update detected: " + currentVersion + " -> " + latestVersion);

        // A patch update is recommended when the latest version is still in the current X.Y cycle and a version of that
        // cycle which is newer than the current one carries the recommended marker in its changelog.
        if (current.length < 3 || latest.length < 3) {
            debugLog("Invalid version format, patch update not recommended");
            return;
        }
        if (current[0] != latest[0] || current[1] != latest[1]) {
            debugLog("Not in the same patch cycle: current " + current[0] + "." + current[1] + ", new " + latest[0] + "." + latest[1]);
            return;
        }
        if (ConfigHandler.updateMode.equals(ConfigHandler.UpdateMode.ALL)) {
            debugLog("Every update is reported in this mode, not fetching the changelogs");
            return;
        }

        JsonArray versionsWithChangelogs = source.fetch(true);
        if (versionsWithChangelogs != null) {
            PATCH_UPDATE_RECOMMENDED = isRecommendedPatchNewerThan(versionsWithChangelogs, current);
        }
    }

    private static boolean isRecommendedPatchNewerThan(JsonArray versions, int[] current) {
        String recommended = findRecommendedPatchVersion(versions, current[0], current[1]);
        if (recommended == null) {
            debugLog("No recommended patch version found in current cycle");
            return false;
        }
        int[] recommendedParts = VersionComparator.parseVersionToIntArray(recommended);
        if (recommendedParts != null && recommendedParts.length >= 3 && current[2] < recommendedParts[2]) {
            debugLog("Recommended patch update found: " + recommended + " (current: " + MOD_VERSION + ")");
            return true;
        }

        debugLog("Current version is up to date with recommended patches");
        return false;
    }

    /**
     * Finds the latest recommended patch version in the given patch cycle.
     * Since versions are ordered newest to oldest, returns the first version with the recommended flag.
     * @param major The major version number (X in X.Y.Z)
     * @param minor The minor version number (Y in X.Y.Z)
     * @return The latest recommended patch version in format "X.Y.Z", or null if none found
     */
    private static String findRecommendedPatchVersion(JsonArray versions, int major, int minor) {
        debugLog("Looking for a recommended patch version for " + major + "." + minor + ".x");

        String lastCheckedVersion = null;

        // Iterate through all versions (newest to oldest)
        for (int i = 0; i < versions.size(); i++) {
            JsonObject version = versions.get(i).getAsJsonObject();
            String mainVersion = extractEuphoriaPatchesVersion(version.get("version_number").getAsString());

            // Skip if we already checked this version (different mod loaders)
            if (mainVersion.equals(lastCheckedVersion)) {
                continue;
            }
            lastCheckedVersion = mainVersion;

            int[] parts = VersionComparator.parseVersionToIntArray(mainVersion);
            if (parts == null || parts.length < 3) {
                continue;
            }

            // Check if this version is in the current patch cycle
            if (parts[0] != major || parts[1] != minor) {
                // Stop when we go below the current patch cycle
                if (parts[0] < major || (parts[0] == major && parts[1] < minor)) {
                    debugLog("Reached versions below current patch cycle, stopping");
                    break;
                }
                continue;
            }

            debugLog("Checking version " + mainVersion + " in current patch cycle");

            // Check if this version has the recommended flag
            if (version.has("changelog") && !version.get("changelog").isJsonNull()) {
                String changelog = version.get("changelog").getAsString();
                debugLog("Fetched Changelog is:\n" + changelog);
                if (isRecommendedUpdate(changelog)) {
                    debugLog("Found recommended patch version: " + mainVersion);
                    return mainVersion; // Early return with the first (latest) recommended version
                }
            }
        }

        return null;
    }

    /**
     * Checks if a changelog contains the recommended update marker.
     * @param changelog The changelog text to check
     * @return true if the changelog indicates a recommended update
     */
    private static boolean isRecommendedUpdate(String changelog) {
        if (changelog == null || changelog.isEmpty()) {
            return false;
        }

        String[] lines = changelog.split("\n");
        for (String line : lines) {
            String lowerLine = line.toLowerCase(Locale.ROOT);
            if (lowerLine.contains("updated to complementary shaders") &&
                lowerLine.contains("euphoria patches") &&
                lowerLine.contains("update recommended")) {
                return true;
            }
        }
        return false;
    }

    // Extract Euphoria Patches version before the first dash (e.g., "1.7.4-r5.6.1-fabric" -> "1.7.4")
    private static String extractEuphoriaPatchesVersion(String versionString) {
        return versionString.split("-")[0];
    }

    // Extract Complementary Shaders version after the "-r" and remove that r (e.g., "1.7.4-r5.6.1-fabric" -> "5.6.1")
    // Returns null when the version string does not have that part
    private static String extractComplementaryVersion(String versionString) {
        String[] parts = versionString.split("-");
        if (parts.length < 2 || !parts[1].startsWith("r")) {
            return null;
        }
        return parts[1].substring(1);
    }

    // Fetch the versions from the Modrinth API, newest first. Returns null if the API did not answer with 200.
    @SuppressWarnings("deprecation")
    private static JsonArray fetchVersions(boolean includeChangelog) throws Exception {
        String url = UPDATE_URL + "?include_changelog=" + includeChangelog;
        debugLog("Fetching versions from Modrinth API: " + url);

        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);

            int responseCode = connection.getResponseCode();
            debugLog("Modrinth API response code: " + responseCode);
            if (responseCode != 200) {
                EuphoriaPatcher.log(2, 0, "Failed to connect to Modrinth API. Response code: " + responseCode);
                return null;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                return new JsonParser().parse(reader).getAsJsonArray(); // Old method used to make it work with Java 8 for old MC versions
            }
        } finally {
            connection.disconnect();
        }
    }
}
