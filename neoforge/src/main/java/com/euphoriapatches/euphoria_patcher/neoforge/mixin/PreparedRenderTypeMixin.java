package com.euphoriapatches.euphoria_patcher.neoforge.mixin;

import com.euphoriapatches.euphoria_patcher.integration.iris.RenderTypeTracker;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.ReflectionUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.Set;

import static com.euphoriapatches.euphoria_patcher.neoforge.mixin.EuphoriaMixinPlugin.PREPARED_RENDER_TYPE_CLASS;

// This Mixin is responsible for giving the dragon death beams an entity ID
// This one works on 26.2+
@Pseudo
@Mixin(targets = PREPARED_RENDER_TYPE_CLASS, remap = false)
public class PreparedRenderTypeMixin {

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

    @Unique
    private static final Set<String> euphoriaPatcher$DEATH_RAY_TYPES = Set.of("dragon_rays", "dragon_rays_depth");

    // 26.2: drawFromBuffer(GpuBuffer, GpuBuffer, IndexType, int, int, int) does the actual draw
    @Inject(
            method = "drawFromBuffer(Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/IndexType;III)V",
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private void euphoriaPatcher$beginDraw(@Coerce Object vertexBuffer, @Coerce Object indexBuffer, @Coerce Object indexType, int baseVertex, int firstIndex, int indexCount, CallbackInfo ci) {
        euphoriaPatcher$beginDrawIfDeathRays();
    }

    @Inject(
            method = "drawFromBuffer(Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/IndexType;III)V",
            at = @At("TAIL"),
            remap = false,
            require = 0
    )
    private void euphoriaPatcher$endDraw(@Coerce Object vertexBuffer, @Coerce Object indexBuffer, @Coerce Object indexType, int baseVertex, int firstIndex, int indexCount, CallbackInfo ci) {
        euphoriaPatcher$endDrawIfDeathRays();
    }

    // 26.3+: drawFromBuffer/drawFromBufferOit were split out and both delegate to the private draw(ExecuteInfo, RenderPass, RenderPipeline) method
    @Inject(method = "draw", at = @At("HEAD"), remap = false, require = 0)
    private void euphoriaPatcher$beginDrawModern(@Coerce Object info, @Coerce Object renderPass, @Coerce Object renderPipeline, CallbackInfo ci) {
        euphoriaPatcher$beginDrawIfDeathRays();
    }

    @Inject(method = "draw", at = @At("TAIL"), remap = false, require = 0)
    private void euphoriaPatcher$endDrawModern(@Coerce Object info, @Coerce Object renderPass, @Coerce Object renderPipeline, CallbackInfo ci) {
        euphoriaPatcher$endDrawIfDeathRays();
    }

    @Unique
    private void euphoriaPatcher$beginDrawIfDeathRays() {
        String name = euphoriaPatcher$resolveName(this);
        if (name == null || !euphoriaPatcher$DEATH_RAY_TYPES.contains(name)) return;

        euphoriaPatcher$beginDeathRays();
    }

    @Unique
    private void euphoriaPatcher$endDrawIfDeathRays() {
        String name = euphoriaPatcher$resolveName(this);
        if (name == null || !euphoriaPatcher$DEATH_RAY_TYPES.contains(name)) return;

        euphoriaPatcher$endDeathRays();
    }

    @Unique
    private static String euphoriaPatcher$resolveName(Object preparedRenderType) {
        try {
            // 26.3+: PreparedRenderType carries its own name() record accessor
            return (String) preparedRenderType.getClass().getMethod("name").invoke(preparedRenderType);
        } catch (Exception e) {
            // 26.2: name is captured separately via RenderTypeMixin$captureNameOnPrepare
            return RenderTypeTracker.getName(preparedRenderType);
        }
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
        EuphoriaLogger.debugLog("[PreparedRenderTypeMixin] " + message);
    }
}