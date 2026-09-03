package com.euphoriapatches.euphoria_patcher.forge.mixin;

import com.euphoriapatches.euphoria_patcher.features.steganography.NativeImageEmbedHelper;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.euphoriapatches.euphoria_patcher.forge.mixin.EuphoriaMixinPlugin.SCREENSHOT_CLASS;

@Pseudo
@Mixin(targets = SCREENSHOT_CLASS, remap = false)
public class ScreenshotMixin {
    @Dynamic("Bypasses compiler checks for alternative mapping variants")
    @Inject(
            method = "m_92279_(Lcom/mojang/blaze3d/pipeline/RenderTarget;)Lcom/mojang/blaze3d/platform/NativeImage;",
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private static void euphoriaPatcher$markReturnedImage(@Coerce Object target, CallbackInfoReturnable<Object> cir) {
        Object image = cir.getReturnValue();
        NativeImageEmbedHelper.markAsScreenshot(image);
        debugLog("Marked NativeImage as screenshot (m_92279_ return): " + image);
    }

    @Unique
    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[ScreenshotMixin] " + message);
    }
}
