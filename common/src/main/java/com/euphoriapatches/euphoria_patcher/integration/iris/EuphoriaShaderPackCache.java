package com.euphoriapatches.euphoria_patcher.integration.iris;

import com.euphoriapatches.euphoria_patcher.config.ConfigHandler;
import com.euphoriapatches.euphoria_patcher.integration.DefineHelper;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.ReflectionUtils;
import com.euphoriapatches.euphoria_patcher.util.mod.ModChecker;
import com.euphoriapatches.euphoria_patcher.util.mod.ModLoaderSpecifics;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds and caches dimension-specific Iris {@code ShaderPack} instances to update dimension defines.
 * <p>
 * Re-evaluates dimension-dependent GLSL and {@code shaders.properties} macros without the overhead
 * of a full {@code Iris.reload()}
 * <p>
 * Returns {@link SwapResult#FALLBACK} on reflection failure to let the caller trigger a standard full reload.
 */
public final class EuphoriaShaderPackCache {

    private EuphoriaShaderPackCache() {}

    public enum SwapResult {
        /** {@code Iris.currentPack} now matches the requested dimension. */
        DONE,
        /** Nothing to do - shaders off, or no genuine build observed yet. */
        SKIPPED,
        /** Reflection failed - caller should fall back to a full {@code Iris.reload()}. */
        FALLBACK
    }

    private static final Object LOCK = new Object();
    private static final int MAX_CACHE_LIMIT = 10;

    private static int cacheLimit() {
        return Math.max(1, Math.min(MAX_CACHE_LIMIT, ConfigHandler.extraDimensionShaderCacheSize + 1));
    }

    // Captured from the last genuine (Iris-driven) ShaderPack construction.
    private static volatile boolean initialised = false;
    private static volatile boolean currentPackIsEuphoria = false;
    private static Object capturedRoot;              // java.nio.file.Path
    private static Object capturedChangedConfigs;    // Map<String,String>, defensive copy
    private static boolean capturedIsZip;
    private static Class<?> capturedPackClass;
    private static int generation = 0;

    private static final Map<String, Object> packsByDimension =
            new LinkedHashMap<String, Object>(8, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Object> eldest) {
                    return size() > cacheLimit();
                }
            };

    private static volatile boolean rebuildInProgress = false;

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[EuphoriaShaderPackCache] " + message);
    }


    /**
     * @param root           the {@code Path} Iris passed to the {@code ShaderPack} constructor
     * @param changedConfigs the option-override {@code Map} Iris passed
     * @param isZip          whether the pack is a zip (modern Iris only; {@code false} for legacy)
     * @param builtPack      the freshly constructed {@code ShaderPack}
     */
    public static void onGenuineShaderPackBuilt(Object root, Object changedConfigs, boolean isZip, Object builtPack) {
        if (rebuildInProgress) {
            return; // our own rebuild
        }
        if (builtPack == null) {
            return;
        }

        boolean isEuphoria = DefineHelper.currentShaderpackIsEuphoria;
        String dimension = safeCurrentDimension();
        synchronized (LOCK) {
            generation++;
            packsByDimension.clear();
            currentPackIsEuphoria = isEuphoria;
            capturedRoot = root;
            capturedChangedConfigs = copyMap(changedConfigs);
            capturedIsZip = isZip;
            capturedPackClass = builtPack.getClass();
            if (isEuphoria && dimension != null) {
                cachePut(dimension, builtPack);
            }
            initialised = capturedRoot != null;
        }
        debugLog("Captured genuine ShaderPack build (gen " + generation + ", dimension " + dimension
                + ", zip " + isZip + ", pack " + capturedPackClass.getName() + ", euphoria " + isEuphoria + ")");
        DimensionShaderRefresh.onGenuineReload(dimension);
    }

    public static boolean isInitialised() {
        return initialised;
    }

    /**
     * Ensures {@code Iris.currentPack} is a {@code ShaderPack} built with the define for
     * {@code dimension}. Uses a cached pack when one exists for the current generation, otherwise
     * builds a fresh one (reusing the already-open pack files - no disk re-read).
     */
    public static SwapResult swapToDimension(String dimension) {
        if (dimension == null) {
            return SwapResult.SKIPPED;
        }
        synchronized (LOCK) {
            if (!currentPackIsEuphoria) {
                // Non-EP packs never read CURRENT_EUPHORIA_PATCHES_DIMENSION_*; leave Iris alone.
                return SwapResult.SKIPPED;
            }
            if (irisMainClass() == null) {
                return SwapResult.FALLBACK;
            }

            Object currentPack = getCurrentPack();
            if (currentPack == null) {
                return SwapResult.SKIPPED; // shaders are off
            }

            if (!initialised) {
                return SwapResult.FALLBACK;
            }

            Object cached = cacheGet(dimension);
            if (cached != null) {
                if (cached == currentPack) {
                    return SwapResult.DONE;
                }
                if (!setCurrentPack(cached)) {
                    return SwapResult.FALLBACK;
                }
                debugLog("Swapped to cached ShaderPack for '" + dimension + "' (gen " + generation
                        + ", cache " + packsByDimension.size() + "/" + cacheLimit() + ")");
                return SwapResult.DONE;
            }

            Object built = buildPack();
            if (built == null) {
                return SwapResult.FALLBACK;
            }
            cachePut(dimension, built);
            if (!setCurrentPack(built)) {
                return SwapResult.FALLBACK;
            }
            debugLog("Built + swapped ShaderPack for '" + dimension + "' (gen " + generation
                    + ", cache " + packsByDimension.size() + "/" + cacheLimit() + ")");
            return SwapResult.DONE;
        }
    }

    private static Object cacheGet(String dimension) {
        return packsByDimension.get(dimension);
    }

    private static void cachePut(String dimension, Object pack) {
        if (DefineHelper.currentShaderpackFirstLoaded) {
            // EUPHORIA_PATCHES_FIRST_LOADED breaks if we cache the first build, skip it :)
            debugLog("Not caching '" + dimension + "' - built during the first-load window");
            return;
        }
        packsByDimension.put(dimension, pack);
    }

    private static Object buildPack() {
        try {
            Constructor<?> ctor = packConstructor();
            if (ctor == null) {
                return null;
            }
            Object defines = standardEnvironmentDefines();
            if (defines == null) {
                return null;
            }
            Object configs = copyMap(capturedChangedConfigs);
            rebuildInProgress = true;
            long startNanos = System.nanoTime();
            try {
                Object pack = ctor.getParameterCount() == 4
                        ? ctor.newInstance(capturedRoot, configs, defines, capturedIsZip)
                        : ctor.newInstance(capturedRoot, configs, defines);
                debugLog("Rebuilt ShaderPack in " + ((System.nanoTime() - startNanos) / 1_000_000L) + " ms");
                return pack;
            } finally {
                rebuildInProgress = false;
            }
        } catch (Throwable t) {
            debugLog("ShaderPack rebuild failed: " + t);
            return null;
        }
    }

    /**
     * {@code PipelineManager.destroyPipeline()} - drops Iris's cached per-dimension pipelines so it
     * rebuilds from the swapped {@code ShaderPack} on the fast path.
     */
    public static void destroyPipeline() {
        ReflectionUtils.invokeMethod(pipelineManager(), "destroyPipeline");
    }

    /**
     * {@code destroyPipeline()} + {@code preparePipeline(getCurrentDimension())}. Used by the tick path.
     *
     * @return {@code true} if a rebuild was issued
     */
    public static boolean forcePipelineRebuild() {
        Object pm = pipelineManager();
        Object dimension = ReflectionUtils.invokeMethod(irisMainClass(), "getCurrentDimension");
        if (pm == null || dimension == null) {
            return false;
        }
        ReflectionUtils.invokeMethod(pm, "destroyPipeline");
        ReflectionUtils.invokeMethod(pm, "preparePipeline", new Class<?>[]{dimension.getClass()}, dimension);
        return true;
    }

    private static Class<?> irisMainClass() {
        return ModChecker.findIrisClass();
    }

    private static Object pipelineManager() {
        return ReflectionUtils.invokeMethod(irisMainClass(), "getPipelineManager");
    }

    private static Object getCurrentPack() {
        return ReflectionUtils.getFieldValue(irisMainClass(), "currentPack");
    }

    private static boolean setCurrentPack(Object pack) {
        return ReflectionUtils.setFieldValue(irisMainClass(), "currentPack", pack);
    }

    private static Object standardEnvironmentDefines() {
        Class<?> macros = ReflectionUtils.firstClass(
                "net.irisshaders.iris.gl.shader.StandardMacros",
                "net.coderbot.iris.gl.shader.StandardMacros");
        return ReflectionUtils.invokeMethod(macros, "createStandardEnvironmentDefines");
    }

    /**
     * The {@code ShaderPack} constructor to rebuild with: {@code (Path, Map, ImmutableList)} on Iris
     * 1.20.1 and older, {@code (Path, Map, ImmutableList, boolean)} on newer. Identified by its {@code Map} second parameter.
     */
    private static Constructor<?> packConstructor() {
        if (capturedPackClass == null) {
            return null;
        }
        for (Constructor<?> ctor : capturedPackClass.getDeclaredConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length >= 3 && Map.class.isAssignableFrom(params[1])) {
                ctor.setAccessible(true);
                return ctor;
            }
        }
        debugLog("No (Path, Map, ImmutableList[, boolean]) constructor on " + capturedPackClass.getName());
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object copyMap(Object map) {
        if (map instanceof Map) {
            return new HashMap((Map) map);
        }
        return new HashMap<String, String>();
    }

    private static String safeCurrentDimension() {
        try {
            return ModLoaderSpecifics.getCurrentDimensionStatic();
        } catch (Throwable t) {
            return null;
        }
    }
}
