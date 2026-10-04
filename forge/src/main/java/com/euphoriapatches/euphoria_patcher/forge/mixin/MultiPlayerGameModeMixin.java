package com.euphoriapatches.euphoria_patcher.forge.mixin;

import com.euphoriapatches.euphoria_patcher.integration.mace.MaceTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.euphoriapatches.euphoria_patcher.forge.mixin.EuphoriaMixinPlugin.MULTI_PLAYER_GAME_MODE_CLASS;

// Stuff for the euphoriaPatchesMaceStrength uniform
@SuppressWarnings("MixinAnnotationTarget")
@Pseudo
@Mixin(targets = MULTI_PLAYER_GAME_MODE_CLASS)
public class MultiPlayerGameModeMixin {

    @Inject(method = {"attack", "m_105223_"}, at = @At("HEAD"), remap = false, require = 0)
    private void euphoriaPatcher$onAttack(@Coerce Object player, @Coerce Object target, CallbackInfo ci) {
        MaceTracker.onAttack(player);
    }
}
