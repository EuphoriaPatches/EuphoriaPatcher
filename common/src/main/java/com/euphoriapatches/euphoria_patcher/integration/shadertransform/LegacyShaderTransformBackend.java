package com.euphoriapatches.euphoria_patcher.integration.shadertransform;

import com.euphoriapatches.euphoria_patcher.config.ConfigHandler;
import com.euphoriapatches.euphoria_patcher.integration.DefineHelper;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shader transforming for 1.18.2 to 1.21.1 (only checked on those XD)
 * <p>
 * Modded shaders can't reach the shaderpack, only vanilla ones can, iris only accounts for those
 * This gets the vanilla getter that the shader's {@link ShaderTransformTable} names. Iris' own mixin on that getter then returns
 * the correct Iris shader, and that is used instead.
 * <p>
 * Only active with a Euphoria Patches shader and when the config option is on. Any failure disables the backend.
 */
public final class LegacyShaderTransformBackend {
    private static final String[] GET_NAME = {"getName", "m_173365_", "method_35787"};
    private static final String[] GET_VERTEX_FORMAT = {"getVertexFormat", "m_173364_", "method_35786"};
    private static final String[] GAME_RENDERER_CLASSES = {"net.minecraft.client.renderer.GameRenderer", "net.minecraft.class_757"};
    private static final String[] DEFAULT_VERTEX_FORMAT_CLASSES = {"com.mojang.blaze3d.vertex.DefaultVertexFormat", "net.minecraft.class_290"};
    private static final String[] IRIS_SHADER_PACKAGES = {"net.irisshaders.iris.", "net.coderbot.iris."};

    // Placeholder for "nothing to do", ConcurrentHashMap and the shader cache can't hold null
    private static final Object NONE = new Object();

    // Shader instance -> TransformTarget or NONE.
    private static final Map<Object, Object> classified = Collections.synchronizedMap(new WeakHashMap<Object, Object>());
    private static final Map<TransformTarget, Object> getters = new ConcurrentHashMap<TransformTarget, Object>();
    private static final Map<TransformTarget, Object> expectedFormats = new ConcurrentHashMap<TransformTarget, Object>();
    private static final Set<String> loggedNames = ConcurrentHashMap.newKeySet();

    private static volatile Method nameMethod;
    private static volatile Method vertexFormatMethod;
    private static volatile boolean disabled;
    private static boolean hookReported;
    private static volatile Class<?> lastIrisShaderClass;

    private LegacyShaderTransformBackend() {}

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[LegacyShaderTransformBackend] " + message);
    }

    /**
     * Called with the {@code ShaderInstance} that {@code RenderSystem.setShader} is about to store.
     *
     * @return the Iris shader to use instead, or {@code shader} itself if it isn't transformed
     */
    public static Object transform(Object shader) {
        if (shader == null || disabled) {
            return shader;
        }
        try {
            if (!ConfigHandler.doTransformCustomShaders || !DefineHelper.currentShaderpackIsEuphoria) {
                return shader;
            }
            if (!hookReported) {
                hookReported = true;
                debugLog("RenderSystem.setShader hook is active");
            }
            Object entry = classified.get(shader);
            if (entry == null) {
                entry = classify(shader);
                classified.put(shader, entry);
            }
            if (entry == NONE) {
                return shader;
            }
            Object replacement = resolveReplacement((TransformTarget) entry);
            // Iris does not answer when shaders are disabled, so we need to return the original shader to not break mods when shaders are disabled
            return isIrisShader(replacement) ? replacement : shader;
        } catch (Throwable t) {
            disabled = true;
            debugLog("Shader transforming disabled after an error: " + t);
            return shader;
        }
    }

    private static Object classify(Object shader) throws ReflectiveOperationException {
        String name = (String) nameMethod(shader).invoke(shader);
        if (name == null) {
            return NONE;
        }

        TransformTarget target = ShaderTransformTable.get(name);
        if (target == null) {
            // vanilla names have neither a namespace nor a double underscore (old 1.18.2 version use __ as it was not possible to use a different namespace)
            if ((name.indexOf(':') >= 0 || name.contains("__")) && loggedNames.add(name)) {
                debugLog("Custom shader '" + name + "' is not in the transform table, leaving it alone (vertex format: " + vertexFormatMethod(shader).invoke(shader) + ")");
            }
            return NONE;
        }

        Object actualFormat = vertexFormatMethod(shader).invoke(shader);
        Object expectedFormat = expectedFormat(target);
        if (expectedFormat == null) {
            debugLog("Not transforming '" + name + "' to " + target + ", the vertex format it needs could not be resolved");
            return NONE;
        }
        if (!expectedFormat.equals(actualFormat)) {
            debugLog("Not transforming '" + name + "' to " + target + ", its vertex format " + actualFormat + " is not " + expectedFormat);
            return NONE;
        }

        debugLog("Transforming '" + name + "' to " + target);
        return target;
    }

    private static Object resolveReplacement(TransformTarget target) throws ReflectiveOperationException {
        Object getter = getters.get(target);
        if (getter == null) {
            getter = findGetter(target);
            getters.put(target, getter);
        }
        return getter == NONE ? null : ((Method) getter).invoke(null);
    }

    private static Object findGetter(TransformTarget target) {
        Class<?> gameRenderer = ReflectionUtils.firstClass(GAME_RENDERER_CLASSES);
        if (gameRenderer == null) {
            debugLog("GameRenderer not found, cannot transform to " + target);
            return NONE;
        }
        try {
            return ReflectionUtils.accessible(ReflectionUtils.tryMethods(gameRenderer, target.legacyGetterNames()));
        } catch (NoSuchMethodException e) {
            debugLog("No GameRenderer getter found for " + target + ": " + e.getMessage());
            return NONE;
        }
    }

    private static Object expectedFormat(TransformTarget target) throws ReflectiveOperationException {
        Object format = expectedFormats.get(target);
        if (format == null) {
            Class<?> formats = ReflectionUtils.firstClass(DEFAULT_VERTEX_FORMAT_CLASSES);
            format = NONE;
            if (formats != null) {
                try {
                    Field field = ReflectionUtils.tryFields(formats, target.expectedFormatFields());
                    format = field.get(null);
                } catch (NoSuchFieldException e) {
                    debugLog("No DefaultVertexFormat field found for " + target + ": " + e.getMessage());
                }
            }
            expectedFormats.put(target, format);
        }
        return format == NONE ? null : format;
    }

    private static boolean isIrisShader(Object shader) {
        if (shader == null) {
            return false;
        }
        Class<?> shaderClass = shader.getClass();
        if (shaderClass == lastIrisShaderClass) {
            return true;
        }
        String className = shaderClass.getName();
        for (String irisPackage : IRIS_SHADER_PACKAGES) {
            if (className.startsWith(irisPackage)) {
                lastIrisShaderClass = shaderClass;
                return true;
            }
        }
        return false;
    }

    private static Method nameMethod(Object shader) throws NoSuchMethodException {
        Method method = nameMethod;
        if (method == null) {
            method = nameMethod = ReflectionUtils.accessible(ReflectionUtils.tryMethods(shader.getClass(), GET_NAME));
        }
        return method;
    }

    private static Method vertexFormatMethod(Object shader) throws NoSuchMethodException {
        Method method = vertexFormatMethod;
        if (method == null) {
            method = vertexFormatMethod = ReflectionUtils.accessible(ReflectionUtils.tryMethods(shader.getClass(), GET_VERTEX_FORMAT));
        }
        return method;
    }
}
