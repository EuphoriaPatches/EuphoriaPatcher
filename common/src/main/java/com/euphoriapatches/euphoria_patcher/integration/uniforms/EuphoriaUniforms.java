package com.euphoriapatches.euphoria_patcher.integration.uniforms;

import com.euphoriapatches.euphoria_patcher.integration.seasons.SeasonsProvider;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.mod.ModLoaderSpecifics;

import java.time.LocalDateTime;

// Central registry of Euphoria Patches uniforms, dispatched to Iris or OptiFine
// backends through the shared UniformDeclarer interface.
public final class EuphoriaUniforms {

    private EuphoriaUniforms() {}

    public static void declareAll(UniformDeclarer declarer) {
        declarer.uniform1b("euphoriaPatchesIsDayAdvancing", ModLoaderSpecifics::isTimeAdvancingStatic);
        debugLog("Declared uniform 'euphoriaPatchesIsDayAdvancing'");

        declarer.uniform1b("euphoriaPatchesIsCurrentBiomeModded", ModLoaderSpecifics::isCurrentBiomeModdedStatic, UniformDeclarer.Frequency.PER_TICK);
        debugLog("Declared uniform 'euphoriaPatchesIsCurrentBiomeModded'");

        declarer.uniform1i("euphoriaPatchesCurrentDayMillis", () -> (int) (System.currentTimeMillis() % 86400000));
        debugLog("Declared uniform 'euphoriaPatchesCurrentDayMillis'");

        declarer.uniform1i("euphoriaPatchesCurrentDayMillisLocal", EuphoriaUniforms::msSinceMidnightLocal);
        debugLog("Declared uniform 'euphoriaPatchesCurrentDayMillisLocal'");


        // Season-related uniforms

        // Get current tick of the season cycle
        declarer.uniform1i("euphoriaPatchesCurrentSeasonTick", SeasonsProvider::getSeasonCycleTicks);
        debugLog("Declared uniform 'euphoriaPatchesCurrentSeasonTick'");

        // Get current season duration in ticks
        declarer.uniform1i("euphoriaPatchesSeasonDuration", SeasonsProvider::getSeasonDuration);
        debugLog("Declared uniform 'euphoriaPatchesSeasonDuration'");

        // Get total season duration in ticks of all 4 seasons
        declarer.uniform1i("euphoriaPatchesTotalSeasonDuration", SeasonsProvider::getTotalSeasonDuration);
        debugLog("Declared uniform 'euphoriaPatchesTotalSeasonDuration'");
    }

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[EuphoriaUniforms] " + message);
    }

    private static int msSinceMidnightLocal() {
        LocalDateTime now = LocalDateTime.now();
        return (now.getHour() * 3600000) + (now.getMinute() * 60000) + (now.getSecond() * 1000) + (now.getNano() / 1000000);
    }
}
