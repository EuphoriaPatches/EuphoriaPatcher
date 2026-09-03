package com.euphoriapatches.euphoria_patcher.forge.mixin;

import com.euphoriapatches.euphoria_patcher.features.steganography.NativeImageEmbedHelper;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;

// Embeds the current shaderpack's companion preset text (if any) into the screenshot's pixels
// via LSB steganography, right before it's serialized to disk
@Pseudo
@Mixin(targets = EuphoriaMixinPlugin.NATIVE_IMAGE_CLASS, remap = false)
public class NativeImageMixin {
    @Dynamic("Bypasses compiler checks for alternative mapping variants")
    @Inject(method = "m_85056_", at = @At("HEAD"), require = 0, remap = false)
    private void euphoriaPatcher$onWriteToFile(File file, CallbackInfo ci) {
        NativeImageEmbedHelper.embed(this, file);
    }

    @Dynamic("Bypasses compiler checks for alternative mapping variants")
    @Inject(method = "m_85056_", at = @At("RETURN"), require = 0, remap = false)
    private void euphoriaPatcher$onWriteToFileReturn(File file, CallbackInfo ci) {
        NativeImageEmbedHelper.createDebugScreenshot(this, file);
    }
}
