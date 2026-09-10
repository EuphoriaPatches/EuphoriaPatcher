package com.euphoriapatches.euphoria_patcher.neoforge.mixin;

import com.euphoriapatches.euphoria_patcher.integration.iris.DimensionShaderRefresh;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class ReloadShadersOnDimensionChangeMixin {

    @Inject(method = "setLevel", at = @At("RETURN"))
    private void onDimensionChange(CallbackInfo ci) {
        DimensionShaderRefresh.onSetLevelReturn();
    }
}
