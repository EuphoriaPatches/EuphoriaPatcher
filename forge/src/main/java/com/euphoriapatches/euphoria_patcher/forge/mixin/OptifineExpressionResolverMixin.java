package com.euphoriapatches.euphoria_patcher.forge.mixin;

import com.euphoriapatches.euphoria_patcher.integration.uniforms.OptifineUniformBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

import static com.euphoriapatches.euphoria_patcher.forge.mixin.EuphoriaMixinPlugin.OPTIFINE_EXPRESSION_RESOLVER_CLASS;

// OptiFine creates a ShaderExpressionResolver for every custom uniform/variable in shaders.properties
// and only resolves names through it. Also register our uniforms there
@Pseudo
@Mixin(targets = OPTIFINE_EXPRESSION_RESOLVER_CLASS, remap = false)
public class OptifineExpressionResolverMixin {

    @Inject(
            method = "<init>(Ljava/util/Map;)V",
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private void onInit(Map<?, ?> map, CallbackInfo ci) {
        OptifineUniformBridge.registerExpressions(this);
    }
}
