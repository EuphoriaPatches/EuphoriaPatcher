package com.euphoriapatches.euphoria_patcher.fabric.mixin;

import com.euphoriapatches.euphoria_patcher.integration.iris.DimensionShaderRefresh;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = EuphoriaMixinPlugin.LEGACY_PIPELINE_MANAGER_CLASS, remap = false)
public class IrisLegacyPipelineManagerMixin {

    @Dynamic
    @Inject(method = "preparePipeline", at = @At("HEAD"), remap = false, require = 0)
    private void euphoriaPatcher$refreshDimensionDefine(@Coerce Object currentDimension,
                                                       CallbackInfoReturnable<Object> cir) {
        DimensionShaderRefresh.beforePreparePipeline();
    }
}
