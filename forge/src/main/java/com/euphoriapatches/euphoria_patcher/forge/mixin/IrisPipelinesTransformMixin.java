package com.euphoriapatches.euphoria_patcher.forge.mixin;

import com.euphoriapatches.euphoria_patcher.integration.shadertransform.ModernShaderTransformBackend;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.euphoriapatches.euphoria_patcher.forge.mixin.EuphoriaMixinPlugin.IRIS_PIPELINES_CLASS;

// Gives modded RenderPipelines that Iris doesn't know the shader pack program of the matching vanilla pipeline
@Pseudo
@Mixin(targets = IRIS_PIPELINES_CLASS, remap = false)
public class IrisPipelinesTransformMixin {

    @Unique
    private static boolean euphoriaPatcher$failed;

    @Inject(method = "getPipeline", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void euphoriaPatcher$transformPipeline(@Coerce Object irisPipeline, @Coerce Object pipeline, CallbackInfoReturnable<Object> cir) {
        if (euphoriaPatcher$failed || cir.getReturnValue() != null) {
            return;
        }
        try {
            Object shaderKey = ModernShaderTransformBackend.resolveKey(irisPipeline, pipeline);
            if (shaderKey != null) {
                cir.setReturnValue(shaderKey);
            }
        } catch (Throwable t) {
            euphoriaPatcher$failed = true;
            try {
                EuphoriaLogger.debugLog("[IrisPipelinesTransformMixin] Shader transforming disabled after an error: " + t);
            } catch (Throwable ignored) {
            }
        }
    }
}
