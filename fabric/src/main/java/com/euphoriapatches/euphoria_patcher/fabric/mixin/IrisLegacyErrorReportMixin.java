package com.euphoriapatches.euphoria_patcher.fabric.mixin;

import com.euphoriapatches.euphoria_patcher.integration.iris.ShaderErrorReporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = EuphoriaMixinPlugin.LEGACY_IRIS_MAIN_CLASS, remap = false)
public class IrisLegacyErrorReportMixin {

    @Inject(method = "handleException", at = @At("TAIL"), remap = false, require = 0)
    private static void euphoriaPatcher$reportShaderError(Exception e, CallbackInfo ci) {
        ShaderErrorReporter.onShaderError(EuphoriaMixinPlugin.LEGACY_IRIS_MAIN_CLASS);
    }

    /**
      * Capture the error thrown by iris so we can display it in chat just like modern iris does.
     */
    @ModifyArg(
            method = "createPipeline",
            at = @At(value = "INVOKE", target = "Lnet/coderbot/iris/IrisLogging;error(Ljava/lang/String;Ljava/lang/Throwable;)V"),
            index = 1,
            remap = false,
            require = 0
    )
    private static Throwable euphoriaPatcher$captureFailure(Throwable error) {
        ShaderErrorReporter.captureError(error);
        return error;
    }

    @Inject(method = "createPipeline", at = @At("RETURN"), remap = false, require = 0)
    private static void euphoriaPatcher$reportFallbackPipeline(@Coerce Object dimension, CallbackInfoReturnable<Object> cir) {
        ShaderErrorReporter.onPipelineCreated(EuphoriaMixinPlugin.LEGACY_IRIS_MAIN_CLASS, cir.getReturnValue());
    }
}
