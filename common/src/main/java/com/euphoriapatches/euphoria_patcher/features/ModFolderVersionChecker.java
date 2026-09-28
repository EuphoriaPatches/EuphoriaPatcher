package com.euphoriapatches.euphoria_patcher.features;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.mod.ModsDirectory;
import com.euphoriapatches.euphoria_patcher.util.VersionComparator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModFolderVersionChecker {
    private static final String MOD_PREFIX = "EuphoriaPatcher-";
    private static final Pattern VERSION_PATTERN = Pattern.compile("EuphoriaPatcher-(\\d+\\.\\d+\\.\\d+)(?:-r.*)?-.*\\.jar");

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[ModFolderVersionChecker] " + message);
    }

    public static boolean existsNewerModInFolder() {
        debugLog("Checking for newer mod versions in folder");
        String currentVersion = EuphoriaPatcher.PATCH_VERSION.substring(1); // Remove leading underscore
        debugLog("Current version: " + currentVersion);
        File folder = ModsDirectory.get().toFile();
        debugLog("Checking mods directory: " + folder);
        File[] modFiles = folder.listFiles((dir, name) -> name.startsWith(MOD_PREFIX) && name.endsWith(".jar"));

        if (modFiles == null || modFiles.length == 0) {
            debugLog("No EuphoriaPatcher mod files found in directory");
            return false;
        }

        if (modFiles.length == 1) {
            debugLog("Only one EuphoriaPatcher mod file found (current version), no check needed");
            return false;
        }

        debugLog("Found " + modFiles.length + " EuphoriaPatcher mod file(s)");

        boolean foundNewer = false;

        for (File modFile : modFiles) {
            Matcher matcher = VERSION_PATTERN.matcher(modFile.getName());
            if (matcher.find()) {
                String fileMainVersion = matcher.group(1);
                debugLog("Comparing file version " + fileMainVersion + " with current " + currentVersion);

                int mainComparison = VersionComparator.compareVersionStrings(fileMainVersion, currentVersion);

                if (mainComparison > 0) {
                    debugLog("Found newer version: " + fileMainVersion);
                    EuphoriaPatcher.log(0, "Found newer version: " + modFile.getName());
                    foundNewer = true;
                } else if (mainComparison < 0) {
                    debugLog("Found older version: " + fileMainVersion + ", attempting to delete");
                    try {
                        Files.delete(modFile.toPath());
                        debugLog("Successfully deleted: " + modFile.getName());
                        EuphoriaPatcher.log(0, "Successfully deleted older version: " + modFile.getName());
                    } catch (IOException e) {
                        debugLog("Failed to delete: " + modFile.getName() + " - " + e.getMessage());
                        EuphoriaPatcher.log(2, 0, "Failed to delete older version: " + modFile.getName() + " - " + e.getMessage());
                    }
                } else {
                    debugLog("Same version found: " + fileMainVersion);
                }
            }
        }
        debugLog(foundNewer ? "Newer version found" : "No newer version found");
        return foundNewer;
    }
}
