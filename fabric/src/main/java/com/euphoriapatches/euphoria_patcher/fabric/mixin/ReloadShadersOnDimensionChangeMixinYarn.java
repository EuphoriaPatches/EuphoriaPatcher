package com.euphoriapatches.euphoria_patcher.fabric.mixin;

import com.euphoriapatches.euphoria_patcher.integration.iris.DimensionShaderRefresh;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class ReloadShadersOnDimensionChangeMixinYarn {

    @Dynamic("Bypasses compiler checks for alternative mapping variants")
    @Inject(
            method = {
                "setWorld",
                "method_1481"
            },
            at = @At("RETURN"))
    private void onDimensionChange(CallbackInfo ci) {
        DimensionShaderRefresh.onSetLevelReturn();
    }
}
