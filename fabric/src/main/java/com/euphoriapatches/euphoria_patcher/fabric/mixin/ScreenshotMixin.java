package com.euphoriapatches.euphoria_patcher.fabric.mixin;

import com.euphoriapatches.euphoria_patcher.features.steganography.NativeImageEmbedHelper;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Consumer;

import static com.euphoriapatches.euphoria_patcher.fabric.mixin.EuphoriaMixinPlugin.SCREENSHOT_CLASS;

@Pseudo
@Mixin(targets = SCREENSHOT_CLASS, remap = false)
public class ScreenshotMixin {
    @Dynamic("Bypasses compiler checks for alternative mapping variants")
    @ModifyVariable(
            method = "takeScreenshot(Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V",
            at = @At("HEAD"),
            argsOnly = true,
            remap = false,
            require = 0
    )
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Consumer euphoriaPatcher$markCapturedImage(Consumer callback) {
        return (Consumer<Object>) image -> {
            NativeImageEmbedHelper.markAsScreenshot(image);
            debugLog("Marked NativeImage as screenshot: " + image);
            callback.accept(image);
        };
    }

    @Unique
    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[ScreenshotMixin] " + message);
    }
}
