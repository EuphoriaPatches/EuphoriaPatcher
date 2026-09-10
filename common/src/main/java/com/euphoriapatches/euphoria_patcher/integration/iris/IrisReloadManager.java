package com.euphoriapatches.euphoria_patcher.integration.iris;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.mod.ModChecker;

/**
 * Centralizes all functionality related to Iris shader reloading
 */
public class IrisReloadManager {
    private static volatile boolean pendingReload = false;
    private static volatile Class<?> pendingIrisClass = null;

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[IrisReloadManager] " + message);
    }

    /**
     * Schedules an Iris shader reload to happen on the next game tick
     * @param irisClass The Iris class to use for reloading
     */
    public static void scheduleReload(Class<?> irisClass) {
        if (irisClass != null) {
            debugLog("Scheduling shader reload");
            pendingIrisClass = irisClass;
            pendingReload = true;
        } else {
            debugLog("Cannot schedule reload - Iris class is null");
        }
    }

    /**
     * Convenience method to find and schedule a reload in one step
     */
    public static void findAndScheduleReload() {
        Class<?> irisClass = ModChecker.findIrisClass();
        if (irisClass != null) {
            scheduleReload(irisClass);
        }
    }

    /**
     * Checks if there's a pending reload and executes it if there is
     * This should be called from the main game thread
     */
    public static void checkPendingReload() {
        DimensionShaderRefresh.processPending();

        if (pendingReload && pendingIrisClass != null) {
            try {
                debugLog("Processing pending shader reload on main thread");
                pendingIrisClass.getMethod("reload").invoke(null);
                debugLog("Successfully reloaded shaders");
            } catch (Exception e) {
                EuphoriaPatcher.log(2, 0, "Error reloading Iris shaders: " + e.getMessage());
            } finally {
                pendingReload = false;
                pendingIrisClass = null;
            }
        }
    }
}
