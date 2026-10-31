package com.euphoriapatches.euphoria_patcher.neoforge.mixin;

import com.euphoriapatches.euphoria_patcher.features.shader_settings.SettingsConverterUtil;
import com.euphoriapatches.euphoria_patcher.features.shader_settings.UpdateShaderConfig;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Intercepts {@code Iris.tryReadConfigProperties} before {@code ShaderPack} construction to load
 * converted settings upfront, preventing Iris from re-saving pre-conversion in-memory state.
 *
 * It also adds the Euphoria Patches identifiers right before Iris saves the settings, so the file is only ever
 * written once, by Iris.
 */
@Pseudo
@Mixin(targets = EuphoriaMixinPlugin.MODERN_IRIS_MAIN_CLASS, remap = false)
public class IrisConfigPropertiesMixin {
    @Redirect(
            method = "tryReadConfigProperties",
            at = @At(value = "INVOKE", target = "Ljava/nio/file/Files;newInputStream(Ljava/nio/file/Path;[Ljava/nio/file/OpenOption;)Ljava/io/InputStream;"),
            remap = false,
            require = 0
    )
    private static InputStream euphoriaPatcher$redirectConfigPropertiesInputStream(Path settingFile, OpenOption[] options) throws IOException {
        if (!euphoriaPatcher$shouldConvertSettings(settingFile)) {
            return Files.newInputStream(settingFile, options);
        }

        List<String> lines = Files.readAllLines(settingFile, StandardCharsets.ISO_8859_1);
        euphoriaPatcher$debugLog("Applied settings conversions before Iris read: " + settingFile.getFileName());
        return SettingsConverterUtil.convertToInputStreamAndUpdateSettings(lines);
    }

    @Inject(method = "tryUpdateConfigPropertiesFile", at = @At("HEAD"), remap = false, require = 0)
    private static void euphoriaPatcher$addIdentifiersBeforeSave(Path settingFile, Properties settings, CallbackInfo ci) {
        UpdateShaderConfig.addIdentifiersToSavedSettings(settingFile, settings);
    }

    @Unique
    private static boolean euphoriaPatcher$shouldConvertSettings(Path settingFile) {
        String fileName = settingFile.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".txt")) {
            return false;
        }
        // remove the ".txt" suffix to get the shaderpack name
        Path shaderPath = settingFile.resolveSibling(fileName.substring(0, fileName.length() - 4));
        return SettingsConverterUtil.shouldConvertSettings(shaderPath);
    }

    @Unique
    private static void euphoriaPatcher$debugLog(String message) {
        EuphoriaLogger.debugLog("[IrisConfigPropertiesMixin] " + message);
    }
}
