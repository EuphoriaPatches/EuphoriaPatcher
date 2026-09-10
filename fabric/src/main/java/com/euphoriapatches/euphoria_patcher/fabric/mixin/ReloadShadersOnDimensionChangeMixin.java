package com.euphoriapatches.euphoria_patcher.fabric.mixin;

import com.euphoriapatches.euphoria_patcher.integration.iris.DimensionShaderRefresh;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.Minecraft", remap = false)
public class ReloadShadersOnDimensionChangeMixin {

    @Dynamic("Bypasses compiler checks for alternative mapping variants")
    @Inject(
            method = {
                    "setLevel"
            },
            at = @At("RETURN"),
            require = 0,
            remap = false)
    private void onDimensionChange(CallbackInfo ci) {
        DimensionShaderRefresh.onSetLevelReturn();
    }
}
