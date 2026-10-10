package com.euphoriapatches.euphoria_patcher.fabric.mixin;

import com.euphoriapatches.euphoria_patcher.integration.mace.MaceTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.euphoriapatches.euphoria_patcher.fabric.mixin.EuphoriaMixinPlugin.PARTICLE_UTILS_CLASS_YARN;

// Vanilla mace smash event, sent to every client in range. For the euphoriaPatchesMaceWave uniforms
@Pseudo
@Mixin(targets = PARTICLE_UTILS_CLASS_YARN)
public class MaceParticlesMixinYarn {

    @Inject(method = "method_58595", at = @At("HEAD"), remap = false, require = 0)
    private static void euphoriaPatcher$onSmash(@Coerce Object level, @Coerce Object pos, int data, CallbackInfo ci) {
        MaceTracker.onSmashEvent(pos);
    }
}
