package com.euphoriapatches.euphoria_patcher.neoforge.mixin;

import com.euphoriapatches.euphoria_patcher.features.steganography.NativeImageEmbedHelper;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

import static com.euphoriapatches.euphoria_patcher.neoforge.mixin.EuphoriaMixinPlugin.SCREENSHOT_CLASS;

@Pseudo
@Mixin(targets = SCREENSHOT_CLASS, remap = false)
public class ScreenshotMixin {
    @Dynamic("Bypasses compiler checks for alternative mapping variants")
    @ModifyVariable(
            method = "takeScreenshot(Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"),
            argsOnly = true,
            remap = false,
            require = 0
    )
    private static Consumer<NativeImage> euphoriaPatcher$markCapturedImage2Arg(Consumer<NativeImage> callback) {
        return image -> {
            NativeImageEmbedHelper.markAsScreenshot(image);
            debugLog("Marked NativeImage as screenshot (2-arg takeScreenshot): " + image);
            callback.accept(image);
        };
    }

    @Dynamic("Bypasses compiler checks for alternative mapping variants")
    @ModifyVariable(
            method = "takeScreenshot(Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V",
            at = @At("HEAD"),
            argsOnly = true,
            remap = false,
            require = 0
    )
    private static Consumer<NativeImage> euphoriaPatcher$markCapturedImage3Arg(Consumer<NativeImage> callback) {
        return image -> {
            NativeImageEmbedHelper.markAsScreenshot(image);
            debugLog("Marked NativeImage as screenshot (3-arg takeScreenshot): " + image);
            callback.accept(image);
        };
    }

    @Dynamic("Bypasses compiler checks for alternative mapping variants")
    @Inject(
            method = "takeScreenshot(Lcom/mojang/blaze3d/pipeline/RenderTarget;)Lcom/mojang/blaze3d/platform/NativeImage;",
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private static void euphoriaPatcher$markReturnedImage(RenderTarget target, CallbackInfoReturnable<NativeImage> cir) {
        NativeImage image = cir.getReturnValue();
        NativeImageEmbedHelper.markAsScreenshot(image);
        debugLog("Marked NativeImage as screenshot (1-arg synchronous takeScreenshot return): " + image);
    }

    @Unique
    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[ScreenshotMixin] " + message);
    }
}
