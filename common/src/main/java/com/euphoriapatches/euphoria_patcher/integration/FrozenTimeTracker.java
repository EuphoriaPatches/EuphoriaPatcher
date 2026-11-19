package com.euphoriapatches.euphoria_patcher.integration;

import com.euphoriapatches.euphoria_patcher.util.mod.ModLoaderSpecifics;

/**
 * Tracks the real seconds spent with the daylight cycle frozen.
 */
public final class FrozenTimeTracker {

    private static final double MAX_STEP_SECONDS = 0.25;

    private static long lastNanos = 0;
    private static double offsetSeconds = 0.0;

    private FrozenTimeTracker() {}

    public static synchronized double getOffsetSeconds() {
        long now = System.nanoTime();
        if (lastNanos != 0 && !ModLoaderSpecifics.isTimeAdvancingStatic()) {
            offsetSeconds += Math.min((now - lastNanos) * 1.0e-9, MAX_STEP_SECONDS);
        }
        lastNanos = now;
        return offsetSeconds;
    }
}
