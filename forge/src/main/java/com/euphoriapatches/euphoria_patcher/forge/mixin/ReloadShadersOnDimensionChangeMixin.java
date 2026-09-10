package com.euphoriapatches.euphoria_patcher.forge.mixin;

import com.euphoriapatches.euphoria_patcher.integration.iris.DimensionShaderRefresh;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
@Pseudo
public class ReloadShadersOnDimensionChangeMixin {

    @Dynamic("Bypasses compiler checks for alternative mapping variants")
    @Inject(
        method = {
                "setLevel",
                "setLevel(Lnet/minecraft/client/multiplayer/ClientLevel;)V",
                "setLevel(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/client/gui/screens/ReceivingLevelScreen$Reason;)V",
                "m_91156_(Lnet/minecraft/client/multiplayer/ClientLevel;)V",
                "func_71403_a(Lnet/minecraft/client/world/ClientWorld;)V"
        },
        at = @At("RETURN"),
        require = 0,
        remap = false
    )
    private void onDimensionChange(CallbackInfo ci) {
        DimensionShaderRefresh.onSetLevelReturn();
    }
}
