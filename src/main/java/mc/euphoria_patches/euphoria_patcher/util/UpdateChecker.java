package mc.euphoria_patches.euphoria_patcher.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {
    private static final String UPDATE_URL = "https://api.github.com/repos/EuphoriaPatches/PatcherUpdateChecker/releases";
    private static final String MOD_VERSION = EuphoriaPatcher.PATCH_VERSION.replace("_","");
    public static String NEW_MOD_VERSION = null;
    public static boolean NEW_VERSION_AVAILABLE = false;
    private static boolean UPDATE_CHECK_PERFORMED = false;

    public static boolean isUpdateAvailable() {
        try {
            return NEW_VERSION_AVAILABLE;
        } catch (Exception e) {
            return false;
        }
    }

    public static void checkForUpdates() {
        if (UPDATE_CHECK_PERFORMED) {
            return;
        }
        UPDATE_CHECK_PERFORMED = true;
        try {
            NEW_MOD_VERSION = fetchLatestVersion();
            if (NEW_MOD_VERSION == null) {
                EuphoriaPatcher.log(2, 0, "[UPDATE CHECKER] Failed to fetch the latest version.");
                return;
            }

            if (isNewerVersion(NEW_MOD_VERSION)) {
                NEW_VERSION_AVAILABLE = true;
                boolean isIris = ShaderLoader.getShaderLoader().equals(ShaderLoader.IRIS);

                EuphoriaPatcher.log(isIris ? 0 : 2, "[UPDATE CHECKER] A new version of the EuphoriaPatcher Mod is available: " + NEW_MOD_VERSION);
                EuphoriaPatcher.log(isIris ? 0 : 2, "[UPDATE CHECKER] Download it from Modrinth: https://euphoriapatches.com/download");
                EuphoriaPatcher.log(isIris ? 0 : 1, 0, "[UPDATE CHECKER] Current Version: " + MOD_VERSION);
            } else {
                EuphoriaPatcher.log(0, "[UPDATE CHECKER] The EuphoriaPatcher Mod is up to date");
            }
        } catch (Exception e) {
            EuphoriaPatcher.log(2, 0, "[UPDATE CHECKER] Update check failed: " + e.getMessage());
        }
    }

    // Fetch the latest version from the GitHub API
    private static String fetchLatestVersion() throws Exception {
        URL url = new URL(UPDATE_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json");

        if (connection.getResponseCode() != 200) {
            EuphoriaPatcher.log(2, 0, "[UPDATE CHECKER] Connection timed out.");
            return null;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            JsonElement jsonElement = new JsonParser().parse(reader); // Old method used to make it work with Java 8 for old MC versions
            JsonArray releases = jsonElement.getAsJsonArray();

            JsonObject latestRelease = null;
            for (JsonElement releaseElement : releases) { // Yep GitHub sorts by tag name and not by date by default :(
                JsonObject release = releaseElement.getAsJsonObject();

                if (latestRelease == null ||
                        release.get("published_at").getAsString().compareTo(latestRelease.get("published_at").getAsString()) > 0) {
                    latestRelease = release;
                }
            }

            if (latestRelease == null) {
                EuphoriaPatcher.log(2, 0, "[UPDATE CHECKER] No releases found.");
                return MOD_VERSION;
            }

            return latestRelease.get("name").getAsString();
        } finally {
            connection.disconnect();
        }
    }

    // Compare the latest version with the current version
    private static boolean isNewerVersion(String latestVersion) {
        String[] latest = latestVersion.split("\\.");
        String[] current = MOD_VERSION.split("\\.");

        for (int i = 0; i < Math.min(latest.length, current.length); i++) {
            int latestPart = Integer.parseInt(latest[i]);
            int currentPart = Integer.parseInt(current[i]);

            if (latestPart != currentPart) {
                return latestPart > currentPart; // return true when bigger otherwise false
            }
        }
        // Consider a version newer if it has more parts (e.g. 1.2.3 vs 1.2)
        return latest.length > current.length;
    }
}
