package com.euphoriapatches.euphoria_patcher.neoforge.mixin;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.ReflectionUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.Set;

import static com.euphoriapatches.euphoria_patcher.neoforge.mixin.EuphoriaMixinPlugin.RENDER_TYPE_CLASS;

// This Mixin is responsible for giving the dragon death beams an entity ID
// This one works from 1.21.11 to 26.1.2
@Pseudo
@Mixin(targets = RENDER_TYPE_CLASS)
public class RenderTypeMixin {

    @Unique
    private static final String euphoriaPatcher$DEATH_RAYS_ID = "dragon_death_rays";

    @Unique
    private static Object euphoriaPatcher$capturedRenderingState = null;
    @Unique
    private static Method euphoriaPatcher$getCurrentEntity = null;
    @Unique
    private static Method euphoriaPatcher$setCurrentEntity = null;
    @Unique
    private static Method euphoriaPatcher$runFallbackListener = null;
    @Unique
    private static int euphoriaPatcher$deathRaysEntityId = Integer.MIN_VALUE;

    @Unique
    private static int euphoriaPatcher$depth = 0;
    @Unique
    private static int euphoriaPatcher$backupEntityId = -1;

    @Shadow
    private String name;

    @Unique
    private static final Set<String> euphoriaPatcher$DEATH_RAY_TYPES =
            Set.of("dragon_rays", "dragon_rays_depth");

    @Inject(method = "draw", at = @At("HEAD"))
    private void euphoriaPatcher$beginDraw(@Coerce Object mesh, CallbackInfo ci) {
        if (!euphoriaPatcher$DEATH_RAY_TYPES.contains(this.name)) return;
        euphoriaPatcher$beginDeathRays();
    }

    @Inject(method = "draw", at = @At("TAIL"))
    private void euphoriaPatcher$endDraw(@Coerce Object  mesh, CallbackInfo ci) {
        if (!euphoriaPatcher$DEATH_RAY_TYPES.contains(this.name)) return;
        euphoriaPatcher$endDeathRays();
    }

    // This method is needed for 26.2 capture
    @Inject(method = "prepare", at = @At("RETURN"), remap = false, require = 0)
    private void euphoriaPatcher$captureNameOnPrepare(CallbackInfoReturnable<Object> cir) {
        if (!euphoriaPatcher$DEATH_RAY_TYPES.contains(this.name)) return;
        Object prepared = cir.getReturnValue();
        com.euphoriapatches.euphoria_patcher.integration.iris.RenderTypeTracker.put(prepared, this.name);
    }

    @Unique
    private static void euphoriaPatcher$beginDeathRays() {
        if (!euphoriaPatcher$resolveIris()) return;
        try {
            if (euphoriaPatcher$depth == 0) {
                euphoriaPatcher$backupEntityId = (int) euphoriaPatcher$getCurrentEntity.invoke(euphoriaPatcher$capturedRenderingState);
            }
            euphoriaPatcher$depth++;
            euphoriaPatcher$setCurrentEntity.invoke(euphoriaPatcher$capturedRenderingState, euphoriaPatcher$deathRaysEntityId);
            euphoriaPatcher$runFallbackListener.invoke(null);
        } catch (Throwable t) {
            euphoriaPatcher$debugLog("begin error: " + t);
        }
    }

    @Unique
    private static void euphoriaPatcher$endDeathRays() {
        if (euphoriaPatcher$capturedRenderingState == null || euphoriaPatcher$depth == 0) return;
        try {
            euphoriaPatcher$depth--;
            if (euphoriaPatcher$depth <= 0) {
                euphoriaPatcher$depth = 0;
                euphoriaPatcher$setCurrentEntity.invoke(euphoriaPatcher$capturedRenderingState, euphoriaPatcher$backupEntityId);
                euphoriaPatcher$runFallbackListener.invoke(null);
            }
        } catch (Throwable t) {
            euphoriaPatcher$debugLog("end error: " + t);
        }
    }

    @Unique
    private static boolean euphoriaPatcher$resolveIris() {
        if (euphoriaPatcher$deathRaysEntityId != Integer.MIN_VALUE && euphoriaPatcher$capturedRenderingState != null) {
            return true;
        }
        try {
            Object worldRenderingSettings = ReflectionUtils.getFieldValue("net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings", "INSTANCE");
            if (worldRenderingSettings == null) return false;
            Object entityIds = worldRenderingSettings.getClass().getMethod("getEntityIds").invoke(worldRenderingSettings);
            if (entityIds == null) return false;

            Object capturedRenderingState = ReflectionUtils.getFieldValue("net.irisshaders.iris.uniforms.CapturedRenderingState", "INSTANCE");
            if (capturedRenderingState == null) return false;

            Object deathRaysId = Class.forName("net.irisshaders.iris.shaderpack.materialmap.NamespacedId")
                    .getConstructor(String.class, String.class).newInstance("minecraft", euphoriaPatcher$DEATH_RAYS_ID);
            int id = (int) entityIds.getClass().getMethod("applyAsInt", Object.class).invoke(entityIds, deathRaysId);

            euphoriaPatcher$getCurrentEntity = capturedRenderingState.getClass().getMethod("getCurrentRenderedEntity");
            euphoriaPatcher$setCurrentEntity = capturedRenderingState.getClass().getMethod("setCurrentEntity", int.class);
            euphoriaPatcher$runFallbackListener = Class.forName("net.irisshaders.iris.layer.GbufferPrograms").getMethod("runFallbackEntityListener");

            euphoriaPatcher$capturedRenderingState = capturedRenderingState;
            euphoriaPatcher$deathRaysEntityId = id;
            euphoriaPatcher$debugLog("resolved dragon_death_rays entity id = " + id);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Unique
    private static void euphoriaPatcher$debugLog(String message) {
        EuphoriaLogger.debugLog("[RenderTypeMixin] " + message);
    }
}
