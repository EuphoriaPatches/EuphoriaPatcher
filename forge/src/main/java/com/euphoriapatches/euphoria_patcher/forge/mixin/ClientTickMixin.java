package com.euphoriapatches.euphoria_patcher.forge.mixin;

import com.euphoriapatches.euphoria_patcher.integration.iris.IrisReloadManager;
import com.euphoriapatches.euphoria_patcher.util.mod.ClipboardManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Debug(export = true)
@Mixin(Minecraft.class)
@Pseudo
public class ClientTickMixin {
    @Dynamic("Bypasses compiler checks for alternative mapping variants")
    @Inject(
            method = {
                    "tick",
                    "runTick",
                    "runTick()V",
                    "m_91398_()V",
                    "func_195542_b(Z)V",
                    "func_238201_a_(ZLnet/minecraft/profiler/LongTickDetector;)V"
            },
            at = @At("HEAD"),
            require = 0, remap = false)
    private void onClientTick(CallbackInfo ci) {
        IrisReloadManager.checkPendingReload();
        ClipboardManager.checkPending();
    }
}
