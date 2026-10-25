package com.euphoriapatches.euphoria_patcher.util.mod;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;

import java.io.File;
import java.nio.file.Path;

public class ModsDirectory {

    private static Path modsDirectory;
    private static final Path defaultModsDir = ModLoaderSpecifics.gameDirectory().resolve("mods");


    /**
     * Get the mods directory path
     * @return Path to the mods directory
     */
    public static Path get() {
        if (modsDirectory == null) {
            modsDirectory = findModsDirectory();
            debugLog("Found mods directory: " + modsDirectory);
        } else {
            debugLog("Using cached mods directory: " + modsDirectory);
        }
        return modsDirectory;
    }

    private static Path findModsDirectory() {

        Path currentModLocation = getCurrentModLocation();
        if (currentModLocation != null) {
            debugLog("EuphoriaPatcher mod is running from: " + currentModLocation);
            if (currentModLocation.startsWith(defaultModsDir)) {
                debugLog("Mod is running from default mods directory, using it: " + currentModLocation);
            }
            return currentModLocation;

        }
        return defaultModsDir;
    }

    private static Path getCurrentModLocation() {
        try {
            java.net.URI uri = EuphoriaPatcher.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            String uriString = uri.toString();

            debugLog("Code source URI: " + uriString + " (scheme: " + uri.getScheme() + ")");

            // Find .jar in the URI and strip everything after it
            int jarIndex = uriString.indexOf(".jar");
            if (jarIndex != -1) {
                // Extract up to and including .jar
                String jarPath = uriString.substring(0, jarIndex + 4); // +4 for ".jar"

                // Remove known scheme prefixes
                if (jarPath.startsWith("union:/")) {
                    jarPath = jarPath.substring(7); // Remove "union:/"
                } else if (jarPath.startsWith("jar:file:/")) {
                    jarPath = jarPath.substring(10); // Remove "jar:file:/"
                } else if (jarPath.startsWith("file:/")) {
                    jarPath = jarPath.substring(6); // Remove "file:/"
                } else if (jarPath.startsWith("jar:/")) {
                    jarPath = jarPath.substring(5); // Remove "jar:/"
                }

                // Remove leading slash on Windows paths (e.g., /C:/ -> C:/)
                if (jarPath.startsWith("/") && jarPath.length() > 2 && jarPath.charAt(1) == ':') {
                    jarPath = jarPath.substring(1);
                }

                // URL decode the path (e.g., %20 -> space, %23 -> #)
                jarPath = java.net.URLDecoder.decode(jarPath, "UTF-8");

                debugLog("Extracted JAR path: " + jarPath);
                Path jarFile = new File(jarPath).toPath();
                debugLog("Mod JAR file: " + jarFile);
                return jarFile.getParent();
            }

            debugLog("Could not find .jar in URI");
            return null;

        } catch (Exception e) {
            debugLog("Could not determine current mod location: " + e.getMessage());
            return null;
        }
    }

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[ModDirectory] " + message);
    }
}
