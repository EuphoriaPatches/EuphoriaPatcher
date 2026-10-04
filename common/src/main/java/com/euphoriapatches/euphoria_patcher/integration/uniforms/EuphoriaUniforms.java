package com.euphoriapatches.euphoria_patcher.integration.uniforms;

import com.euphoriapatches.euphoria_patcher.integration.mace.MaceTracker;
import com.euphoriapatches.euphoria_patcher.integration.seasons.SeasonsProvider;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.mod.ModLoaderSpecifics;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

// Central registry of Euphoria Patches uniforms, dispatched to Iris or OptiFine
// backends through the shared UniformDeclarer interface.
public final class EuphoriaUniforms {

    private EuphoriaUniforms() {}

    public static void declareAll(UniformDeclarer declarer) {
        register(declarer);
        for (String name : uniformNames()) {
            debugLog("Declared uniform '" + name + "'");
        }
    }

    public static List<String> uniformNames() {
        NameCollector collector = new NameCollector();
        register(collector);
        return collector.names;
    }

    // All uniforms go here :)
    private static void register(UniformDeclarer declarer) {
        declarer.uniform1b("euphoriaPatchesIsDayAdvancing", ModLoaderSpecifics::isTimeAdvancingStatic);

        declarer.uniform1b("euphoriaPatchesIsCurrentBiomeModded", ModLoaderSpecifics::isCurrentBiomeModdedStatic, UniformDeclarer.Frequency.PER_TICK);

        declarer.uniform1i("euphoriaPatchesCurrentDayMillis", () -> (int) (System.currentTimeMillis() % 86400000));

        declarer.uniform1i("euphoriaPatchesCurrentDayMillisLocal", EuphoriaUniforms::msSinceMidnightLocal);

        // 0-1 progress of the animation started by the last mace smash, 0 if there never was one
        declarer.uniform1f("euphoriaPatchesMaceSmashProgress", MaceTracker::getSmashProgress);

        // Season-related uniforms

        // Get current tick of the season cycle
        declarer.uniform1i("euphoriaPatchesCurrentSeasonTick", SeasonsProvider::getSeasonCycleTicks);

        // Get current season duration in ticks
        declarer.uniform1i("euphoriaPatchesSeasonDuration", SeasonsProvider::getSeasonDuration);

        // Get total season duration in ticks of all 4 seasons
        declarer.uniform1i("euphoriaPatchesTotalSeasonDuration", SeasonsProvider::getTotalSeasonDuration);
    }

    // Records uniform names only; the frequency overloads default to these.
    private static final class NameCollector implements UniformDeclarer {
        private final List<String> names = new ArrayList<>();

        @Override
        public void uniform1b(String name, BooleanSupplier value) {
            names.add(name);
        }

        @Override
        public void uniform1i(String name, IntSupplier value) {
            names.add(name);
        }

        @Override
        public void uniform1f(String name, DoubleSupplier value) {
            names.add(name);
        }

        @Override
        public void uniform2f(String name, DoubleSupplier x, DoubleSupplier y) {
            names.add(name);
        }

        @Override
        public void uniform2i(String name, IntSupplier x, IntSupplier y) {
            names.add(name);
        }

        @Override
        public void uniform3f(String name, DoubleSupplier x, DoubleSupplier y, DoubleSupplier z) {
            names.add(name);
        }

        @Override
        public void uniform3i(String name, IntSupplier x, IntSupplier y, IntSupplier z) {
            names.add(name);
        }

        @Override
        public void uniform3d(String name, DoubleSupplier x, DoubleSupplier y, DoubleSupplier z) {
            names.add(name);
        }

        @Override
        public void uniform4f(String name, DoubleSupplier x, DoubleSupplier y, DoubleSupplier z, DoubleSupplier w) {
            names.add(name);
        }
    }

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[EuphoriaUniforms] " + message);
    }

    private static int msSinceMidnightLocal() {
        LocalDateTime now = LocalDateTime.now();
        return (now.getHour() * 3600000) + (now.getMinute() * 60000) + (now.getSecond() * 1000) + (now.getNano() / 1000000);
    }
}
