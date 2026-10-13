package com.euphoriapatches.euphoria_patcher.integration.shadertransform;

import com.euphoriapatches.euphoria_patcher.config.ConfigHandler;
import com.euphoriapatches.euphoria_patcher.integration.DefineHelper;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shader transforming for 1.21.5+, where mods draw with their own {@code RenderPipeline}.
 * <p>
 * Iris picks the shader pack program for a pipeline in {@code IrisPipelines.getPipeline}, which is called on every
 * draw. A modded pipeline it doesn't know gets no program. When {@link ShaderTransformTable} lists one, we return
 * the program Iris itself picks for the matching vanilla pipeline.
 * If a mod does in the future registers it via the Iris API, it will win over our transformation.
 */
public final class ModernShaderTransformBackend {
    private static final String IRIS_PIPELINES_CLASS = "net.irisshaders.iris.pipeline.IrisPipelines";
    private static final String[] RENDER_PIPELINES_CLASSES = {"net.minecraft.client.renderer.RenderPipelines", "net.minecraft.class_10799"};
    private static final String[] GET_LOCATION = {"getLocation"};
    private static final String[] GET_VERTEX_LAYOUT = {"getVertexFormat", "getVertexFormatBindings"};

    // Placeholder for "nothing to do", the pipeline cache can't hold null
    private static final Object NONE = new Object();

    // Custom pipeline -> the vanilla pipeline to use or NONE.
    private static final Map<Object, Object> classified = Collections.synchronizedMap(new WeakHashMap<Object, Object>());
    private static final Map<TransformTarget, Object> vanillaPipelines = new ConcurrentHashMap<TransformTarget, Object>();
    private static final Set<String> loggedLocations = ConcurrentHashMap.newKeySet();

    private static volatile Method irisGetPipeline;
    private static volatile boolean disabled;

    private ModernShaderTransformBackend() {}

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[ModernShaderTransformBackend] " + message);
    }

    /**
     * Called at the end of {@code IrisPipelines.getPipeline} when Iris has no program for the pipeline.
     */
    public static Object resolveKey(Object irisPipeline, Object pipeline) {
        if (pipeline == null || disabled) {
            return null;
        }
        try {
            if (!ConfigHandler.doTransformCustomShaders || !DefineHelper.currentShaderpackIsEuphoria) {
                return null;
            }
            Object entry = classified.get(pipeline);
            if (entry == null) {
                entry = classify(pipeline);
                classified.put(pipeline, entry);
            }
            if (entry == NONE) {
                return null;
            }
            // Ask Iris for the program it gives the vanilla equivalent, it already picks the right one for the current phase.
            // The equivalent is a vanilla pipeline Iris knows, so this goes through the it again without looping back here
            return irisGetPipeline().invoke(null, irisPipeline, entry);
        } catch (Throwable t) {
            disabled = true;
            debugLog("Shader transforming disabled after an error: " + t);
            return null;
        }
    }

    private static Object classify(Object pipeline) throws ReflectiveOperationException {
        String location = String.valueOf(ReflectionUtils.tryMethods(pipeline.getClass(), GET_LOCATION).invoke(pipeline));
        TransformTarget target = ShaderTransformTable.get(location);
        if (target == null) {
            // Only log the modded ones
            if (!location.startsWith("minecraft:") && loggedLocations.add(location)) {
                debugLog("Custom pipeline '" + location + "' is not in the transform table, leaving it alone");
            }
            return NONE;
        }

        Object vanilla = vanillaPipeline(target);
        if (vanilla == null) {
            debugLog("Not transforming '" + location + "' to " + target + ", that vanilla pipeline could not be found");
            return NONE;
        }

        Object customLayout = vertexLayout(pipeline);
        Object vanillaLayout = vertexLayout(vanilla);
        if (customLayout != null && vanillaLayout != null && !Objects.deepEquals(customLayout, vanillaLayout)) {
            debugLog("Not transforming '" + location + "' to " + target + ", its vertex format " + describe(customLayout) + " is not " + describe(vanillaLayout));
            return NONE;
        }

        debugLog("Transforming '" + location + "' to " + target);
        return vanilla;
    }

    private static Object vanillaPipeline(TransformTarget target) throws ReflectiveOperationException {
        Object pipeline = vanillaPipelines.get(target);
        if (pipeline == null) {
            pipeline = NONE;
            Class<?> renderPipelines = ReflectionUtils.firstClass(RENDER_PIPELINES_CLASSES);
            if (renderPipelines != null) {
                try {
                    Field field = ReflectionUtils.tryFields(renderPipelines, target.modernPipelineFields());
                    Object value = field.get(null);
                    if (value != null) {
                        pipeline = value;
                    }
                } catch (NoSuchFieldException e) {
                    debugLog("No RenderPipelines field found for " + target + ": " + e.getMessage());
                }
            }
            vanillaPipelines.put(target, pipeline);
        }
        return pipeline == NONE ? null : pipeline;
    }

    // The vertex format of a pipeline, or null if this version doesn't expose it under a name we know
    private static Object vertexLayout(Object pipeline) {
        try {
            return ReflectionUtils.tryMethods(pipeline.getClass(), GET_VERTEX_LAYOUT).invoke(pipeline);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static String describe(Object layout) {
        return layout instanceof Object[] ? Arrays.deepToString((Object[]) layout) : String.valueOf(layout);
    }

    private static Method irisGetPipeline() throws ReflectiveOperationException {
        Method method = irisGetPipeline;
        if (method == null) {
            Class<?> irisPipelines = Class.forName(IRIS_PIPELINES_CLASS);
            // The parameter types differ between versions (RenderPipeline moved packages on 26.3)
            for (Method candidate : irisPipelines.getMethods()) {
                if (candidate.getName().equals("getPipeline") && candidate.getParameterCount() == 2 && Modifier.isStatic(candidate.getModifiers())) {
                    method = candidate;
                    break;
                }
            }
            if (method == null) {
                throw new NoSuchMethodException(IRIS_PIPELINES_CLASS + ".getPipeline");
            }
            irisGetPipeline = method = ReflectionUtils.accessible(method);
        }
        return method;
    }
}
