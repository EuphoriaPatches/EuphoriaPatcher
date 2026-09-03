package com.euphoriapatches.euphoria_patcher.fabric.mixin;

import com.euphoriapatches.euphoria_patcher.features.steganography.NativeImageEmbedHelper;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

import static com.euphoriapatches.euphoria_patcher.fabric.mixin.EuphoriaMixinPlugin.SCREENSHOT_CLASS_YARN;

@Pseudo
@Mixin(targets = SCREENSHOT_CLASS_YARN, remap = false)
public class ScreenshotMixinYarn {
    @Dynamic("Bypasses compiler checks for alternative mapping variants")
    @ModifyVariable(
            method = "method_1663(Lnet/minecraft/class_276;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"),
            argsOnly = true,
            remap = false,
            require = 0
    )
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Consumer euphoriaPatcher$markCapturedImage2Arg(Consumer callback) {
        return (Consumer<Object>) image -> {
            NativeImageEmbedHelper.markAsScreenshot(image);
            debugLog("Marked NativeImage as screenshot (2-arg method_1663): " + image);
            callback.accept(image);
        };
    }

    @Dynamic("Bypasses compiler checks for alternative mapping variants")
    @ModifyVariable(
            method = "method_71641(Lnet/minecraft/class_276;ILjava/util/function/Consumer;)V",
            at = @At("HEAD"),
            argsOnly = true,
            remap = false,
            require = 0
    )
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Consumer euphoriaPatcher$markCapturedImage3Arg(Consumer callback) {
        return (Consumer<Object>) image -> {
            NativeImageEmbedHelper.markAsScreenshot(image);
            debugLog("Marked NativeImage as screenshot (3-arg method_71641): " + image);
            callback.accept(image);
        };
    }

    // 1.20.1 and 1.21.1-era shape: no callback at all, NativeImage is the direct return value.
    @Dynamic("Bypasses compiler checks for alternative mapping variants")
    @Inject(
            method = "method_1663(Lnet/minecraft/class_276;)Lnet/minecraft/class_1011;",
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private static void euphoriaPatcher$markReturnedImage(@Coerce Object renderTarget, CallbackInfoReturnable<Object> cir) {
        Object image = cir.getReturnValue();
        NativeImageEmbedHelper.markAsScreenshot(image);
        debugLog("Marked NativeImage as screenshot (1-arg synchronous method_1663 return): " + image);
    }

    @Unique
    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[ScreenshotMixinYarn] " + message);
    }
}
