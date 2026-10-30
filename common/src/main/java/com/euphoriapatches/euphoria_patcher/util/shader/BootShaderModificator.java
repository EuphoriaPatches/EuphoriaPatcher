package com.euphoriapatches.euphoria_patcher.util.shader;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.config.ConfigHandler;
import com.euphoriapatches.euphoria_patcher.features.ModifyPatchedShaderpacks;
import com.euphoriapatches.euphoria_patcher.features.properties.PropertiesWatcher;
import com.euphoriapatches.euphoria_patcher.integration.ShaderLoader;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.services.ShaderNamingService;
import com.euphoriapatches.euphoria_patcher.util.SpacEagle17;
import com.euphoriapatches.euphoria_patcher.util.UpdateChecker;
import com.euphoriapatches.euphoria_patcher.util.VersionComparator;
import com.euphoriapatches.euphoria_patcher.util.mod.ModChecker;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

public class BootShaderModificator {

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[BootShaderModificator] " + message);
    }

    public static void applyShaderModifications(Path shader, boolean styleUnbound, boolean styleReimagined, boolean isAlreadyInstalled) {
        applyUpdateNotification(shader, styleUnbound, styleReimagined);
        applyCompatibilityModifications(shader, styleUnbound, styleReimagined);
        applyDeveloperModifications(shader, styleUnbound, styleReimagined);

        EuphoriaPatcher instance = EuphoriaPatcher.getInstance();
        ShaderNamingService namingService = instance.getNamingService();

        namingService.createAlternativeShaderNames(shader, isAlreadyInstalled, ConfigHandler.alternativeShaderNames);

        // Initialize properties watcher if auto-merge is enabled
        if (ConfigHandler.autoMergeBlockProperties) {
            debugLog("Auto-merge block properties is enabled, starting properties watcher");
            PropertiesWatcher.startWatcher();
        } else {
            debugLog("Auto-merge block properties is disabled, not starting properties watcher");
        }
    }

    private static void applyUpdateNotification(Path shader, boolean styleUnbound, boolean styleReimagined) {
        if (!UpdateChecker.shouldUserUpdate()) {
            return;
        }

        boolean isOculus = ShaderLoader.getShaderLoader().equals(ShaderLoader.OCULUS);
        boolean isOptifine = ShaderLoader.getShaderLoader().equals(ShaderLoader.OPTIFINE);

        String newVersionText = "value.info19.0=§c" + EuphoriaPatcher.PATCH_VERSION.replace("_", "") + " §r->§a " + UpdateChecker.getNewModVersion();
        if (isOculus || isOptifine && !ShaderLoader.isMinecraftVersionAtLeast("1.21.1")) {
            newVersionText = "value.info19.0=§c" + EuphoriaPatcher.PATCH_VERSION.replace("_", "") + " -> " + UpdateChecker.getNewModVersion();
        }

        try {
            // Shaders Properties Modifications
            ModifyPatchedShaderpacks.modifyFiles(shader, styleUnbound, styleReimagined, EuphoriaPatcher.SHADERS_PROPERTIES_LOCATION, null, "screen=<empty> <empty>", "screen=info19 info20");
            ModifyPatchedShaderpacks.modifyFiles(shader, styleUnbound, styleReimagined, EuphoriaPatcher.LANG_LOCATION, ".lang", "value\\.info19\\.0=.*", newVersionText);

            // Iris Shader Folder Mod Description
            // 4 \\\\ have to be used to represent 1 \ in the final JSON due to multiple layers of escaping
            String shaderDescriptionText = "\\\\u00A7cComplementary Shaders " + EuphoriaPatcher.VERSION.replace("_", "") + "\\\\u00A7r + \\\\u00A7dEuphoria Patches " + EuphoriaPatcher.PATCH_VERSION.replace("_", "") + "\\\\u00A7r Complementary add-on by SpacEagle17 extending it with many more unique optional features and settings.\\\\nDev versions: \\\\u00A7dwww.euphoriapatches.com/support\\\\u00A7r";

            ModifyPatchedShaderpacks.modifyFiles(shader, styleUnbound, styleReimagined, "shaders/pack.json", null,
                    "\"shaderDescription\":.*", "\"shaderDescription\": \"" + "\\\\u00A7aUPDATE AVAILABLE!\\\\u00A7r\\\\nCurrent version: \\\\u00A7c" + EuphoriaPatcher.PATCH_VERSION.replace("_", "") + "\\\\u00A7r -> " + "New version: \\\\u00A7a" + UpdateChecker.getNewModVersion() + "\\\\u00A7r\\\\n----------------\\\\n" + shaderDescriptionText + "\",");

        } catch (IOException e) {
            EuphoriaPatcher.log(3, 0, "Could not modify the shader to show the user that a new version is available" + e.getMessage());
        }
    }

    /**
     * Iris 1.12.0 Vulkan supports these features where they did not work on OpenGL only Iris
     */
    static boolean needsMacOSWorkarounds(String osName, String shaderLoader, int shaderLoaderVersion) {
        if (osName == null || !osName.toLowerCase(Locale.ROOT).contains("mac")) {
            return false;
        }
        boolean irisSupportsMacOS = ShaderLoader.IRIS.equals(shaderLoader)
                && VersionComparator.isNewerOrEqual(shaderLoaderVersion, "1.12.0");
        return !irisSupportsMacOS;
    }

    private static void applyCompatibilityModifications(Path shader, boolean styleUnbound, boolean styleReimagined) {
        boolean isOptifine = ShaderLoader.getShaderLoader().equals(ShaderLoader.OPTIFINE);
        boolean isAngelica = ShaderLoader.getShaderLoader().equals(ShaderLoader.ANGELICA);
        boolean isMacOS = needsMacOSWorkarounds(System.getProperty("os.name"), ShaderLoader.getShaderLoader(), ShaderLoader.getShaderLoaderVersion());
        boolean isPhotonicsInstalled = ModChecker.isModPresent(ModChecker.ModNames.PHOTONICS);

        // This changes the popular settings profile dynamically
        if (isOptifine || isMacOS) {
            removeColoredLighting(shader, styleUnbound, styleReimagined);
            removeEndCrystalVortex(shader, styleUnbound, styleReimagined);
            removeDragonDeathEffect(shader, styleUnbound, styleReimagined);
            removeEndPortalBeam(shader, styleUnbound, styleReimagined);
        } else if (isAngelica) {
            removeEndCrystalVortex(shader, styleUnbound, styleReimagined);
            removeDragonDeathEffect(shader, styleUnbound, styleReimagined);
            removeEndPortalBeam(shader, styleUnbound, styleReimagined);
        } else if (isPhotonicsInstalled) {
            removeColoredLighting(shader, styleUnbound, styleReimagined);
        }
    }

    private static void removeColoredLighting(Path shader, boolean styleUnbound, boolean styleReimagined) {
        try {
            // Change COLORED_LIGHTING from 192 to 0
            ModifyPatchedShaderpacks.modifyFiles(shader, styleUnbound, styleReimagined, EuphoriaPatcher.SHADERS_PROPERTIES_LOCATION, null,
                    "(profile2\\.POPULAR\\s+=\\s+.*?COLORED_LIGHTING=)192(\\s+.*)", "$10  $2");
            debugLog("Removed COLORED_LIGHTING=192 from POPULAR profile");
        } catch (IOException e) {
            EuphoriaPatcher.log(3, 0, "Could not remove COLORED_LIGHTING=192 from POPULAR profile: " + e.getMessage());
        }
    }

    private static void removeEndCrystalVortex(Path shader, boolean styleUnbound, boolean styleReimagined) {
        try {
            // Change END_CRYSTAL_VORTEX from 3 to 0
            ModifyPatchedShaderpacks.modifyFiles(shader, styleUnbound, styleReimagined, EuphoriaPatcher.SHADERS_PROPERTIES_LOCATION, null,
                    "(profile2\\.POPULAR\\s+=\\s+.*?END_CRYSTAL_VORTEX=)3(\\s+.*)", "$10$2");
            debugLog("Removed END_CRYSTAL_VORTEX=3 from POPULAR profile");
        } catch (IOException e) {
            EuphoriaPatcher.log(3, 0, "Could not remove END_CRYSTAL_VORTEX=3 from POPULAR profile: " + e.getMessage());
        }
    }

    private static void removeEndPortalBeam(Path shader, boolean styleUnbound, boolean styleReimagined) {
        try {
            // Change END_PORTAL_BEAM to !END_PORTAL_BEAM
            ModifyPatchedShaderpacks.modifyFiles(shader, styleUnbound, styleReimagined, EuphoriaPatcher.SHADERS_PROPERTIES_LOCATION, null,
                    "(profile2\\.POPULAR\\s+=\\s+.*?)\\s+END_PORTAL_BEAM(\\s+.*)", "$1 !END_PORTAL_BEAM$2");
            debugLog("Removed END_PORTAL_BEAM from POPULAR profile");
        } catch (IOException e) {
            EuphoriaPatcher.log(3, 0, "Could not remove END_PORTAL_BEAM=1 from POPULAR profile: " + e.getMessage());
        }
    }

    private static void removeDragonDeathEffect(Path shader, boolean styleUnbound, boolean styleReimagined) {
        try {
            // Change DRAGON_DEATH_EFFECT=1 to DRAGON_DEATH_EFFECT=0
            ModifyPatchedShaderpacks.modifyFiles(shader, styleUnbound, styleReimagined, EuphoriaPatcher.SHADERS_PROPERTIES_LOCATION, null,
                    "(profile2\\.POPULAR\\s+=\\s+.*?)\\s+DRAGON_DEATH_EFFECT=1(\\s+.*)", "$1 DRAGON_DEATH_EFFECT=0$2");
            debugLog("Removed DRAGON_DEATH_EFFECT=1 from POPULAR profile");
        } catch (IOException e) {
            EuphoriaPatcher.log(3, 0, "Could not remove DRAGON_DEATH_EFFECT=1 from POPULAR profile: " + e.getMessage());
        }
    }

    private static void applyDeveloperModifications(Path shader, boolean styleUnbound, boolean styleReimagined) {
        if (SpacEagle17.check()) {
            try {
                ModifyPatchedShaderpacks.modifyFiles(shader, styleUnbound, styleReimagined, EuphoriaPatcher.SHADER_MYFILE_LOCATION, null, "\\/\\/ Developed by SpacEagle17", "#define SPACEAGLE17");
            } catch (IOException e) {
                EuphoriaPatcher.log(3, 0, "Could not modify the shader for SpacEagle17" + e.getMessage());
            }
        }
    }
}
