package com.euphoriapatches.euphoria_patcher.fabric.mixin;

import com.euphoriapatches.euphoria_patcher.integration.iris.IrisReloadManager;
import com.euphoriapatches.euphoria_patcher.integration.iris.ShaderErrorReporter;
import com.euphoriapatches.euphoria_patcher.util.mod.ClipboardManager;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class ClientTickMixinYarn {
    @Inject(method = "tick()V", at = @At("HEAD"))
    private void onClientTick(CallbackInfo ci) {
        IrisReloadManager.checkPendingReload();
        ClipboardManager.checkPending();
        ShaderErrorReporter.checkPending();
    }
}
