package com.euphoriapatches.euphoria_patcher.fabric.mixin;

import com.euphoriapatches.euphoria_patcher.integration.iris.ShaderErrorReporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds a "report it on discord" chat link next to Iris' own failure message for EP.
 */
@Pseudo
@Mixin(targets = EuphoriaMixinPlugin.MODERN_IRIS_MAIN_CLASS, remap = false)
public class IrisModernErrorReportMixin {

    @Inject(method = "handleException", at = @At("TAIL"), remap = false, require = 0)
    private static void euphoriaPatcher$reportShaderError(Exception e, CallbackInfo ci) {
        ShaderErrorReporter.onShaderError(EuphoriaMixinPlugin.MODERN_IRIS_MAIN_CLASS);
    }

    @Inject(method = "createPipeline", at = @At("RETURN"), remap = false, require = 0)
    private static void euphoriaPatcher$reportFallbackPipeline(@Coerce Object dimension, CallbackInfoReturnable<Object> cir) {
        ShaderErrorReporter.onPipelineCreated(EuphoriaMixinPlugin.MODERN_IRIS_MAIN_CLASS, cir.getReturnValue());
    }
}
