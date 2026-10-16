package com.euphoriapatches.euphoria_patcher.util.shader;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.services.ShaderDetector;
import com.euphoriapatches.euphoria_patcher.util.SpacEagle17;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class SettingsFileDefinesUpdater {

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[SettingsFileDefinesUpdater] " + message);
    }

    /**
     * I got too lazy to add the {@code AAB_FOR_EUPHORIA_PATCHES_VERSION_} defines in myself, handle it automatically.
     */
    public static void ensureVersionDefine(ShaderDetector shaderDetector) {
        if (!SpacEagle17.check()) return;
        try {
            Path devPack = EuphoriaPatcher.shaderpacks.resolve("Euphoria-Patches");
            Path defines = devPack.resolve("shaders").resolve("lib").resolve("shaderSettings").resolve("settingsFileDefines.glsl");
            String version = shaderDetector.readVersionFromPackJson(devPack);
            if (version == null || !Files.isRegularFile(defines)) {
                debugLog("No pack.json version or settingsFileDefines.glsl not found, skipping version defines");
                return;
            }
            String define = "AAB_FOR_EUPHORIA_PATCHES_VERSION_" + version.replace('.', '_');

            String content = new String(Files.readAllBytes(defines), StandardCharsets.UTF_8);
            if (content.contains(define)) {
                debugLog("settingsFileDefines.glsl already contains " + define);
                return;
            }

            String lines = "//#define " + define + "\n#ifdef " + define + "\n#endif\n";
            Files.write(defines, lines.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
            debugLog("Added " + define + " to settingsFileDefines.glsl");
        } catch (Exception e) {
            debugLog("Could not update settingsFileDefines.glsl: " + e.getMessage());
        }
    }
}
