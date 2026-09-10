package com.euphoriapatches.euphoria_patcher.integration.iris;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.mod.ModLoaderSpecifics;

/**
 * Updates dimension-specific shader defines when changing dimensions without triggering a full {@code Iris.reload()}.
 * <p>
 * <ul>
 *   <li><b>Fast path:</b> {@link #beforePreparePipeline()} runs before pipeline construction to apply changes in one frame without visual stutter.</li>
 *   <li><b>Fallback path:</b> {@link #processPending()} handles delayed updates or triggers a full reload if the fast path is bypassed.</li>
 * </ul>
 */
public final class DimensionShaderRefresh {

    private DimensionShaderRefresh() {}

    /** How long after the last fast-path invocation we still consider the fast path "alive". */
    private static final long FAST_PATH_ALIVE_MS = 3000L;

    private static final Object LOCK = new Object();

    private static volatile String lastHandledDim = null;
    private static volatile Object lastLevel = null;
    private static volatile long fastPathHeartbeat = 0L;
    private static volatile String pendingSetLevelDim = null;
    private static volatile boolean fallbackScheduled = false;

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[DimensionShaderRefresh] " + message);
    }

    // Fast path
    public static void beforePreparePipeline() {
        fastPathHeartbeat = System.currentTimeMillis();
        try {
            Object level = ModLoaderSpecifics.getLevelStatic();
            if (level == lastLevel) {
                return; // steady state: one reference compare per frame
            }
            lastLevel = level;
            if (level == null) {
                return;
            }

            String dim = safeDimension();
            if (dim == null) {
                return;
            }
            if (dim.equals(lastHandledDim)) {
                return;
            }
            applySwap(dim, true);
        } catch (Throwable t) {
            debugLog("beforePreparePipeline error: " + t);
        }
    }


    // Client tick when fast path was bypassed/failed
    public static void processPending() {
        String pending = pendingSetLevelDim;
        if (pending == null) {
            return;
        }

        boolean fastPathAlive = System.currentTimeMillis() - fastPathHeartbeat < FAST_PATH_ALIVE_MS;
        if (fastPathAlive && pending.equals(lastHandledDim)) {
            pendingSetLevelDim = null; // fast path already handled it
            fallbackScheduled = false;
            return;
        }

        pendingSetLevelDim = null;
        String dim = safeDimension();
        if (dim == null || dim.equals(lastHandledDim)) {
            return;
        }
        debugLog("Fast path did not cover dimension change; handling on tick");
        applySwap(dim, false);
    }


    public static void onSetLevelReturn() {
        try {
            String dim = safeDimension();
            if (dim == null) {
                return;
            }
            if (lastHandledDim == null) {
                lastHandledDim = dim; // first world join - nothing to refresh against
                return;
            }
            if (!dim.equals(lastHandledDim)) {
                pendingSetLevelDim = dim;
            }
        } catch (Throwable t) {
            debugLog("onSetLevelReturn error: " + t);
        }
    }

    // Genuine Iris reload observed
    public static void onGenuineReload(String dimensionAtReload) {
        lastHandledDim = dimensionAtReload;
        lastLevel = null; // force the fast path to re-evaluate on the next frame
        pendingSetLevelDim = null;
        fallbackScheduled = false;
        debugLog("Observed genuine reload; baseline dimension = " + dimensionAtReload);
    }

    private static void applySwap(String dim, boolean fastPath) {
        synchronized (LOCK) {
            if (dim.equals(lastHandledDim)) {
                return;
            }

            EuphoriaShaderPackCache.SwapResult result = EuphoriaShaderPackCache.swapToDimension(dim);
            switch (result) {
                case DONE:
                    if (fastPath) {
                        EuphoriaShaderPackCache.destroyPipeline();
                    } else {
                        EuphoriaShaderPackCache.forcePipelineRebuild();
                    }
                    lastHandledDim = dim;
                    fallbackScheduled = false;
                    debugLog("Refreshed dimension define -> " + dim + (fastPath ? " (fast path)" : " (tick)"));
                    break;
                case SKIPPED:
                    lastHandledDim = dim; // shaders off / not initialised - nothing to do
                    break;
                case FALLBACK:
                default:
                    lastHandledDim = dim;
                    if (!fallbackScheduled) {
                        fallbackScheduled = true;
                        debugLog("Lean dimension swap unavailable - falling back to full Iris reload");
                        IrisReloadManager.findAndScheduleReload();
                    }
                    break;
            }
        }
    }

    private static String safeDimension() {
        try {
            return ModLoaderSpecifics.getCurrentDimensionStatic();
        } catch (Throwable t) {
            return null;
        }
    }
}
