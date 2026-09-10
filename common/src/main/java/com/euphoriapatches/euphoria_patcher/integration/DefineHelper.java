package com.euphoriapatches.euphoria_patcher.integration;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.config.ConfigHandler;
import com.euphoriapatches.euphoria_patcher.features.shader_settings.UpdateShaderConfig;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.monitoring.PotatoFileMonitor;
import com.euphoriapatches.euphoria_patcher.services.ShaderDetector;
import com.euphoriapatches.euphoria_patcher.util.mod.ModChecker;
import com.euphoriapatches.euphoria_patcher.util.mod.ModLoaderSpecifics;
import com.euphoriapatches.euphoria_patcher.util.SpacEagle17;
import com.euphoriapatches.euphoria_patcher.util.UpdateChecker;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

public final class DefineHelper {

    public interface DefineEmitter {
        void define(String key);

        void define(String key, String value);
    }

    private static int injectCount = 0;
    public static boolean isShaderLoaderRunning = false;
    public static volatile boolean currentShaderpackIsEuphoria = false;
    public static volatile boolean currentShaderpackFirstLoaded = false;

    private DefineHelper() {}

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[DefineHelper] " + message);
    }

    public static void addEuphoriaDefines(List<?> standardDefines,
                                          Target target,
                                          BiConsumer<List<?>, String> defineKey,
                                          BiConsumer<List<?>, String[]> defineKeyValue) {
        if (target == Target.OPTIFINE) {
            EuphoriaPatcher.log(3,0,"Use the string overload for OptiFine defines");
        }

        addCommonDefines(target, new DefineEmitter() {
            @Override
            public void define(String key) {
                defineKey.accept(standardDefines, key);
            }

            @Override
            public void define(String key, String value) {
                defineKeyValue.accept(standardDefines, new String[]{key, value});
            }
        });
    }

    public static String addEuphoriaDefines(String str, Target target) {
        if (target != Target.OPTIFINE) {
            EuphoriaPatcher.log(3,0,"Use the list overload for Iris defines");
        }

        StringBuilder sb = new StringBuilder(str);
        addCommonDefines(target, new DefineEmitter() {
            @Override
            public void define(String key) {
                appendMacroLine(sb, key);
            }

            @Override
            public void define(String key, String value) {
                appendMacroLine(sb, key, value);
            }
        });
        return sb.toString();
    }

    private static void addCommonDefines(Target target, DefineEmitter emitter) {
        try {
            EuphoriaPatcher instance = EuphoriaPatcher.getInstance();
            ShaderDetector shaderDetector = instance.getShaderDetector();

            boolean isLegacy = target == Target.IRIS_LEGACY;
            boolean isOptifine = target == Target.OPTIFINE;

            injectCount++;
            isShaderLoaderRunning = true;
            if (isOptifine) {
                debugLog("Adding Euphoria Patches defines to OptiFine");
            } else {
                debugLog("Adding Euphoria Patches defines to Iris" + (isLegacy ? " (Legacy)" : ""));
            }

            if (SpacEagle17.check()) {
                emitter.define("SPACEAGLE17");
                debugLog("Adding SPACEAGLE17 define");
            }

            String currentVersion = formatVersionAsGLSLString(EuphoriaPatcher.PATCH_VERSION);
            emitter.define("CURRENT_EUPHORIA_PATCHES_VERSION", currentVersion);
            debugLog("Adding CURRENT_EUPHORIA_PATCHES_VERSION = " + currentVersion + " define");

            emitter.define("EUPHORIA_PATCHES_VERSION_DEFINE", getFormattedEPVersion());
            debugLog("Adding EUPHORIA_PATCHES_VERSION_DEFINE = " + getFormattedEPVersion() + " define");

            emitter.define("EUPHORIA_PATCHES_MOD_INSTALLED");
            debugLog("Adding EUPHORIA_PATCHES_MOD_INSTALLED define");

            emitter.define("EUPHORIA_PATCHES_UNIFORMS");
            debugLog("Adding EUPHORIA_PATCHES_UNIFORMS define");

            // Thanks to GeForceLegend for finding this bug fix! https://github.com/IrisShaders/Iris/pull/3246
            emitter.define("EUPHORIA_PATCHES_AT_MIDBLOCK_FIX");
            debugLog("Adding EUPHORIA_PATCHES_AT_MIDBLOCK_FIX define");

            if (ModLoaderSpecifics.isCurrentDimensionInMappingsStatic()) {
                emitter.define("EUPHORIA_PATCHES_DIMENSION_IN_PROPERTIES");
                debugLog("Adding EUPHORIA_PATCHES_DIMENSION_IN_PROPERTIES define");
            } else {
                debugLog("Not adding EUPHORIA_PATCHES_DIMENSION_IN_PROPERTIES define - dimension not in dimensions.properties");
            }

            String currentDimension = "CURRENT_EUPHORIA_PATCHES_DIMENSION_" + ModLoaderSpecifics.getCurrentDimensionStatic().toUpperCase(Locale.ROOT);
            emitter.define(currentDimension);
            debugLog("Adding " + currentDimension + " define");

            String shaderLoader = ShaderLoader.getShaderLoader().toUpperCase(Locale.ROOT);
            emitter.define("EUPHORIA_PATCHES_" + shaderLoader);
            debugLog("Adding EUPHORIA_PATCHES_" + shaderLoader + " define");

            String[] shaderLoaderVersionDefine = ShaderLoader.getShaderLoaderVersionDefine();
            if (shaderLoaderVersionDefine != null) {
                emitter.define(shaderLoaderVersionDefine[0], shaderLoaderVersionDefine[1]);
                debugLog("Adding " + shaderLoaderVersionDefine[0] + " = " + shaderLoaderVersionDefine[1] + " define");
            }

            Path currentShaderpack = ShaderLoader.getCurrentShaderpackPath();
            boolean isEuphoriaShader = currentShaderpack != null && shaderDetector.isEuphoriaPatchesShader(currentShaderpack);
            currentShaderpackIsEuphoria = isEuphoriaShader;
            if (isEuphoriaShader) {
                if (PotatoFileMonitor.shouldAddPotatoRemovedDefine(currentShaderpack)) {
                    emitter.define("EUPHORIA_PATCHES_POTATO_REMOVED");
                    debugLog("Adding EUPHORIA_PATCHES_POTATO_REMOVED define - potato.png not found");
                } else {
                    debugLog("Not adding EUPHORIA_PATCHES_POTATO_REMOVED define - potato.png found");
                }
            } else {
                if (currentShaderpack == null) {
                    debugLog("Current shaderpack is null, skipping potato.png check");
                } else {
                    debugLog("Current shaderpack (" + currentShaderpack.getFileName() + ") is not an Euphoria Patches shader, skipping potato.png check");
                }
            }

            if (ModChecker.isModPresent(ModChecker.ModNames.PHOTONICS)) {
                emitter.define("EUPHORIA_PATCHES_IS_PHOTONICS_INSTALLED");
                debugLog("Adding EUPHORIA_PATCHES_IS_PHOTONICS_INSTALLED define");
            }

            if (ModChecker.isModPresent(ModChecker.ModNames.ASTROCRAFT)) {
                emitter.define("EUPHORIA_PATCHES_IS_ASTROCRAFT_INSTALLED");
                debugLog("Adding EUPHORIA_PATCHES_IS_ASTROCRAFT_INSTALLED define");
            }

            if (ModChecker.isModPresent(ModChecker.ModNames.SPYGLASS_ASTRONOMY)) {
                emitter.define("EUPHORIA_PATCHES_IS_SPYGLASS_ASTRONOMY_INSTALLED");
                debugLog("Adding EUPHORIA_PATCHES_IS_SPYGLASS_ASTRONOMY_INSTALLED define");
            }

            if (ModChecker.isModPresent(ModChecker.ModNames.THREE_D_SKIN_LAYERS)) {
                emitter.define("EUPHORIA_PATCHES_IS_3D_SKIN_LAYERS_INSTALLED");
                debugLog("Adding EUPHORIA_PATCHES_IS_3D_SKIN_LAYERS_INSTALLED define");
            }

            if (ModChecker.isModPresent(ModChecker.ModNames.CAELUM)) {
                emitter.define("EUPHORIA_PATCHES_IS_CAELUM_INSTALLED");
                debugLog("Adding EUPHORIA_PATCHES_IS_CAELUM_INSTALLED define");
            }

            if (ModChecker.isModPresent(ModChecker.ModNames.STELLAR_VIEW)) {
                emitter.define("EUPHORIA_PATCHES_IS_STELLAR_VIEW_INSTALLED");
                debugLog("Adding EUPHORIA_PATCHES_IS_STELLAR_VIEW_INSTALLED define");
            }

            if (isSeasonsModInstalled()) {
                emitter.define("EUPHORIA_PATCHES_IS_SEASONS_MOD_INSTALLED");
                debugLog("Adding EUPHORIA_PATCHES_IS_SEASONS_MOD_INSTALLED define");
            }

            if (isSkyboxModInstalled()){
                emitter.define("EUPHORIA_PATCHES_IS_SKYBOX_MOD_INSTALLED");
                debugLog("Adding EUPHORIA_PATCHES_IS_SKYBOX_MOD_INSTALLED define");
            }

            if (isSpaceModInstalled()) {
                emitter.define("EUPHORIA_PATCHES_IS_SPACE_MOD_INSTALLED");
                debugLog("Adding EUPHORIA_PATCHES_IS_SPACE_MOD_INSTALLED define");
            }

            int injectedCountAmount = isOptifine ? 100 : 2; //Yeah... OptiFine does MANY Macro injections
            currentShaderpackFirstLoaded = injectCount <= injectedCountAmount;
            if (currentShaderpackFirstLoaded) {
                emitter.define("EUPHORIA_PATCHES_FIRST_LOADED");
                debugLog("Adding EUPHORIA_PATCHES_FIRST_LOADED define");

                if (UpdateChecker.shouldUserUpdate() && ConfigHandler.doDisplayShaderInGameMessage) {
                    emitter.define("NEW_EUPHORIA_PATCHES_UPDATE");
                    debugLog("Adding NEW_EUPHORIA_PATCHES_UPDATE define");

                    if (UpdateChecker.getNewModVersion() != null) {
                        String nextVersionFormatted = formatVersionAsGLSLString(UpdateChecker.getNewModVersion());
                        emitter.define("NEXT_EUPHORIA_PATCHES_VERSION", nextVersionFormatted);
                        debugLog("Adding NEXT_EUPHORIA_PATCHES_VERSION: " + nextVersionFormatted + " define");
                    }
                } else if (!ConfigHandler.doDisplayShaderInGameMessage) {
                    debugLog("Not adding NEW_EUPHORIA_PATCHES_UPDATE define - in-game messages disabled");
                } else {
                    debugLog("Not adding NEW_EUPHORIA_PATCHES_UPDATE define - no update available");
                }

                if (ShaderLoader.getShaderLoader().equals(ShaderLoader.NEOCULUS)) {
                    emitter.define("EUPHORIA_PATCHES_NEOCULUS_WARNING");
                    debugLog("Adding EUPHORIA_PATCHES_NEOCULUS_WARNING define");
                }
            } else {
                debugLog("Not adding NEW_EUPHORIA_PATCHES_UPDATE define - already injected " + injectCount + " times");
                if (ShaderLoader.getShaderLoader().equals(ShaderLoader.NEOCULUS)) {
                    debugLog("Not adding EUPHORIA_PATCHES_NEOCULUS_WARNING define - already injected " + injectCount + " times");
                }
            }

            UpdateShaderConfig.markCurrentEPSettingsFile();

            if (injectCount == 1) {
                EuphoriaPatcher.log(0, "Added Euphoria Patches defines to " +
                        shaderLoader.substring(0, 1).toUpperCase(Locale.ROOT) + shaderLoader.substring(1).toLowerCase(Locale.ROOT));
            }
            debugLog("Injected count:" + injectCount);
        } catch (Exception e) {
            debugLog("Exception while adding defines: " + e.getMessage());
        }
    }

    private static String formatVersionAsGLSLString(String version) {
        String[] versionParts = version.replace("_", "").split("\\.");
        StringBuilder versionBuilder = new StringBuilder();
        for (int i = 0; i < versionParts.length; i++) {
            // Split each part into individual digits
            String part = versionParts[i];
            for (int j = 0; j < part.length(); j++) {
                if (j > 0) {
                    versionBuilder.append(", ");
                }
                versionBuilder.append("_").append(part.charAt(j));
            }
            if (i < versionParts.length - 1) {
                versionBuilder.append(", _dot, ");
            }
        }
        return versionBuilder.toString();
    }

    private static String getFormattedEPVersion() {
        String[] parts = EuphoriaPatcher.PATCH_VERSION.replace("_", "").split("\\.");
        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        int patch = Integer.parseInt(parts[2]);
        return String.format("%d%02d%02d", major, minor, patch);
    }

    private static boolean isSkyboxModInstalled() {
        return ModChecker.isModPresent(ModChecker.ModNames.FORGE_SKYBOXES) ||
               ModChecker.isModPresent(ModChecker.ModNames.FABRIC_SKYBOXES) ||
               ModChecker.isModPresent(ModChecker.ModNames.SKYBOXIFY) ||
               ModChecker.isModPresent(ModChecker.ModNames.CELESTIAL) ||
               ModChecker.isModPresent(ModChecker.ModNames.HORIZON);
    }

    private static boolean isSpaceModInstalled() {
        return ModChecker.isModPresent(ModChecker.ModNames.ASTROCRAFT) ||
               ModChecker.isModPresent(ModChecker.ModNames.STELLAR_VIEW) ||
               ModChecker.isModPresent(ModChecker.ModNames.CAELUM);
    }

    private static boolean isSeasonsModInstalled() {
        return ModChecker.isModPresent(ModChecker.ModNames.SERENE_SEASONS) ||
               ModChecker.isModPresent(ModChecker.ModNames.FABRIC_SEASONS) ||
               ModChecker.isModPresent(ModChecker.ModNames.ECLIPTIC_SEASONS);
    }

    private static void appendMacroLine(StringBuilder sb, String name) {
        sb.append("#define ");
        sb.append(name);
        sb.append("\n");
    }

    private static void appendMacroLine(StringBuilder sb, String name, String value) {
        sb.append("#define ");
        sb.append(name);
        sb.append(" ");
        sb.append(value);
        sb.append("\n");
    }
}
