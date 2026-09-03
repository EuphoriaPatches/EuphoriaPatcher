package com.euphoriapatches.euphoria_patcher.fabric.mixin;

import com.euphoriapatches.euphoria_patcher.features.steganography.NativeImageEmbedHelper;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;

import static com.euphoriapatches.euphoria_patcher.fabric.mixin.EuphoriaMixinPlugin.NATIVE_IMAGE_CLASS_YARN;

// Embeds the current shaderpack's companion preset text (if any) into the screenshot's pixels
// via LSB steganography, right before it's serialized to disk
@Pseudo
@Mixin(targets = NATIVE_IMAGE_CLASS_YARN, remap = false)
public class NativeImageMixinYarn {
    @Dynamic("Bypasses compiler checks for alternative mapping variants")
    @Inject(method = "method_4325", at = @At("HEAD"), remap = false, require = 0)
    private void euphoriaPatcher$onWriteToFile(File file, CallbackInfo ci) {
        NativeImageEmbedHelper.embed(this, file);
    }

    @Dynamic("Bypasses compiler checks for alternative mapping variants")
    @Inject(method = "method_4325", at = @At("RETURN"), remap = false, require = 0)
    private void euphoriaPatcher$onWriteToFileReturn(File file, CallbackInfo ci) {
        NativeImageEmbedHelper.createDebugScreenshot(this, file);
    }
}
