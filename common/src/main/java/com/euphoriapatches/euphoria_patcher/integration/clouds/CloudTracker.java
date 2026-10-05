package com.euphoriapatches.euphoria_patcher.integration.clouds;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.ReflectionUtils;
import com.euphoriapatches.euphoria_patcher.util.mod.ModLoaderSpecifics;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

// Check the alpha of clouds so we can detect when a dimension disabled clouds
public final class CloudTracker {

    private static final String[] MINECRAFT_CLASSES = {"net.minecraft.client.Minecraft", "net.minecraft.class_310"};
    private static final String[] GET_INSTANCE = {"getInstance", "method_1551"};
    private static final String[] GET_DELTA_TRACKER = {"getDeltaTracker", "method_61966"};
    private static final String[] GET_PARTIAL_TICK = {"getGameTimeDeltaPartialTick", "method_60637"};

    private static final String[] ENVIRONMENT_ATTRIBUTES_CLASSES = {"net.minecraft.world.attribute.EnvironmentAttributes", "net.minecraft.class_12206"};
    private static final String[] CLOUD_COLOR_FIELDS = {"CLOUD_COLOR", "field_64126"};
    private static final String[] GAME_RENDERER_FIELDS = {"gameRenderer", "field_1773"};
    private static final String[] GET_MAIN_CAMERA = {"getMainCamera", "mainCamera", "method_19418"}; // mainCamera since 26.2
    private static final String[] ATTRIBUTE_PROBE_METHODS = {"attributeProbe", "method_75756"};
    private static final String[] PROBE_GET_VALUE = {"getValue", "method_75689"};
    private static final String[] VECTOR_ALPHA = {"w"}; // JOML Vector4f

    private static boolean resolved = false;
    private static boolean unsupported = false;
    private static String lastError = null;
    private static float lastLoggedAlpha = Float.NaN; // NaN never equals a real alpha, so the first value is logged

    private static Object minecraft;
    private static Method deltaTrackerMethod;
    private static Method partialTickMethod;
    private static Field gameRendererField;
    private static Method mainCameraMethod;
    private static Method attributeProbeMethod;
    private static Method probeGetValueMethod;
    private static Object cloudColorAttribute;
    private static Method vectorAlphaMethod;

    private CloudTracker() {}

    public static float getAlpha() {
        if (unsupported) return 1.0F;
        if (ModLoaderSpecifics.getLevelStatic() == null) return 1.0F;
        try {
            if (!resolved) {
                resolve();
            }
            float partialTick = (float) partialTickMethod.invoke(deltaTrackerMethod.invoke(minecraft), false);
            Object camera = mainCameraMethod.invoke(gameRendererField.get(minecraft));
            Object probe = attributeProbeMethod.invoke(camera);
            Object color = probeGetValueMethod.invoke(probe, cloudColorAttribute, partialTick);
            lastError = null;
            return logIfChanged(alphaOf(color));
        } catch (NoSuchFieldException | NoSuchMethodException | ClassNotFoundException | LinkageError e) {
            // Older version or mappings that don't match, don't retry every frame
            debugLog("Cloud alpha unsupported: " + e);
            debugLog("Cloud alpha unsupported, using 1.0");
            unsupported = true;
            return 1.0F;
        } catch (Throwable t) {
            String error = String.valueOf(t);
            if (!error.equals(lastError)) {
                lastError = error;
                debugLog("Error reading cloud alpha: " + t);
            }
            return 1.0F;
        }
    }

    private static float logIfChanged(float alpha) {
        if (alpha != lastLoggedAlpha) {
            lastLoggedAlpha = alpha;
            debugLog("Current cloud alpha: " + alpha);
        }
        return alpha;
    }

    // The attribute is an ARGB int up to 26.2 and a Vector4f (rgba, 0-1) from 26.3 on
    private static float alphaOf(Object color) throws Exception {
        if (color instanceof Integer) {
            // ARGB packs the alpha into the top byte, so shift it down and scale 0-255 to 0-1
            return (((Integer) color >>> 24) & 0xFF) / 255.0F;
        }
        if (vectorAlphaMethod == null) {
            vectorAlphaMethod = ReflectionUtils.tryMethods(color.getClass(), VECTOR_ALPHA);
        }
        return (float) vectorAlphaMethod.invoke(color);
    }

    private static void resolve() throws Exception {
        Class<?> attributesClass = ReflectionUtils.firstClass(ENVIRONMENT_ATTRIBUTES_CLASSES);
        if (attributesClass == null) {
            throw new ClassNotFoundException(String.join(" / ", ENVIRONMENT_ATTRIBUTES_CLASSES));
        }
        Field cloudColorField = ReflectionUtils.tryFields(attributesClass, CLOUD_COLOR_FIELDS);
        cloudColorAttribute = cloudColorField.get(null);

        Class<?> minecraftClass = ReflectionUtils.firstClass(MINECRAFT_CLASSES);
        if (minecraftClass == null) {
            throw new ClassNotFoundException(String.join(" / ", MINECRAFT_CLASSES));
        }
        minecraft = ReflectionUtils.tryMethods(minecraftClass, GET_INSTANCE).invoke(null);
        if (minecraft == null) {
            throw new IllegalStateException("Minecraft instance is not available yet");
        }

        deltaTrackerMethod = ReflectionUtils.tryMethods(minecraftClass, GET_DELTA_TRACKER);
        partialTickMethod = ReflectionUtils.tryMethods(deltaTrackerMethod.getReturnType(), new Class<?>[]{boolean.class}, GET_PARTIAL_TICK);

        gameRendererField = ReflectionUtils.tryFields(minecraftClass, GAME_RENDERER_FIELDS);
        mainCameraMethod = ReflectionUtils.tryMethods(gameRendererField.getType(), GET_MAIN_CAMERA);
        attributeProbeMethod = ReflectionUtils.tryMethods(mainCameraMethod.getReturnType(), ATTRIBUTE_PROBE_METHODS);
        probeGetValueMethod = ReflectionUtils.tryMethods(attributeProbeMethod.getReturnType(),
                new Class<?>[]{cloudColorField.getType(), float.class}, PROBE_GET_VALUE);
        resolved = true;
        debugLog("Cloud alpha source resolved");
    }

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[CloudTracker] " + message);
    }
}
