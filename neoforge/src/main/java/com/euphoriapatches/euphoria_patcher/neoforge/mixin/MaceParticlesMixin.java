package com.euphoriapatches.euphoria_patcher.neoforge.mixin;

import com.euphoriapatches.euphoria_patcher.integration.mace.MaceTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.euphoriapatches.euphoria_patcher.neoforge.mixin.EuphoriaMixinPlugin.PARTICLE_UTILS_CLASS;

// Vanilla mace smash event, sent to every client in range. For the euphoriaPatchesMaceWave uniforms
@SuppressWarnings("MixinAnnotationTarget")
@Pseudo
@Mixin(targets = PARTICLE_UTILS_CLASS)
public class MaceParticlesMixin {

    @Inject(method = "spawnSmashAttackParticles", at = @At("HEAD"), remap = false, require = 0)
    private static void euphoriaPatcher$onSmash(@Coerce Object level, @Coerce Object pos, int data, CallbackInfo ci) {
        MaceTracker.onSmashEvent(pos);
    }
}
