package com.euphoriapatches.euphoria_patcher.neoforge.mixin;

import com.euphoriapatches.euphoria_patcher.integration.shadertransform.LegacyShaderTransformBackend;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Supplier;

import static com.euphoriapatches.euphoria_patcher.neoforge.mixin.EuphoriaMixinPlugin.RENDER_SYSTEM_CLASS;

// Swaps modded ShaderInstances that shader packs can't see for the Iris shader of the matching vanilla render path
@Pseudo
@Mixin(targets = RENDER_SYSTEM_CLASS, remap = false)
public class RenderSystemShaderTransformMixin {

    @Unique
    private static boolean euphoriaPatcher$failed;

    @Redirect(
            method = "setShader(Ljava/util/function/Supplier;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/function/Supplier;get()Ljava/lang/Object;"),
            remap = false,
            require = 0
    )
    private static Object euphoriaPatcher$transformShader(Supplier<?> shader) {
        Object original = shader.get();
        if (euphoriaPatcher$failed) {
            return original;
        }
        try {
            return LegacyShaderTransformBackend.transform(original);
        } catch (Throwable t) {
            euphoriaPatcher$failed = true;
            try {
                EuphoriaLogger.debugLog("[RenderSystemShaderTransformMixin] Shader transforming disabled after an error: " + t);
            } catch (Throwable ignored) {
            }
            return original;
        }
    }
}
