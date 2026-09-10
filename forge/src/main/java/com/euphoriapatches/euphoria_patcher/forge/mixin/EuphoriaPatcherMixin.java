package com.euphoriapatches.euphoria_patcher.forge.mixin;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.forge.ForgeModLoaderSpecifics;
import com.euphoriapatches.euphoria_patcher.util.mod.ModLoaderSpecifics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = net.minecraft.client.main.Main.class, remap = false)
public class EuphoriaPatcherMixin {
    @Inject(method = "main([Ljava/lang/String;)V", at = @At("HEAD"))
    private static void onGameStart(String[] args, CallbackInfo ci) {
        ForgeModLoaderSpecifics forgeSpecifics = new ForgeModLoaderSpecifics();
        ModLoaderSpecifics.setInstance(forgeSpecifics);

        if(ModLoaderSpecifics.serverCheckStatic()) return;
        new EuphoriaPatcher();
    }
}
