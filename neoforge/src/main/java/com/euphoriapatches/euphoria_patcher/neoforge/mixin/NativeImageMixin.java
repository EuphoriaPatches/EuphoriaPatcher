package com.euphoriapatches.euphoria_patcher.neoforge.mixin;

import com.euphoriapatches.euphoria_patcher.features.steganography.NativeImageEmbedHelper;
import com.mojang.blaze3d.platform.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;

// Embeds the current shaderpack's companion preset text (if any) into the screenshot's pixels
// via LSB steganography, right before it's serialized to disk
@Mixin(NativeImage.class)
public class NativeImageMixin {
    @Inject(method = "writeToFile", at = @At("HEAD"))
    private void euphoriaPatcher$onWriteToFile(File file, CallbackInfo ci) {
        NativeImageEmbedHelper.embed(this, file);
    }

    @Inject(method = "writeToFile", at = @At("RETURN"))
    private void euphoriaPatcher$onWriteToFileReturn(File file, CallbackInfo ci) {
        NativeImageEmbedHelper.createDebugScreenshot(this, file);
    }
}
