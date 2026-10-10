package com.euphoriapatches.euphoria_patcher.integration.mace;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;


/**
 * Utility class for tracking the position and strength of mace smash attacks.
 *
 * <p>This class gets data from two sources:
 * <ul>
 *   <li>Vanilla smash particle events, which supply the hit coordinates.</li>
 *   <li>Player attack inputs, which supply the strength.</li>
 * </ul>
 */
public final class MaceTracker {

    private static final float SMASH_FALL_THRESHOLD = 1.5F;
    private static final float MAX_FALL_HEIGHT = 10.0F; // Magic number since vanilla scales up to infinity and 10 seems good
    private static final double WEAKEST_SMASH_SECONDS = 0.85; // animation length at the smash threshold
    private static final double STRONGEST_SMASH_SECONDS = 0.5; // animation length at MAX_FALL_HEIGHT or more
    private static final double NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);

    private static final int WAVE_SLOTS = 2;
    private static final float OTHER_PLAYER_STRENGTH = 0.5F; // vanilla event has no strength and other players' fall distance isn't synced
    private static final long OWN_SMASH_MATCH_NANOS = TimeUnit.MILLISECONDS.toNanos(1500); // click to particle delay, vanilla has no way to sync the two so we just guess

    private static final String[] FALL_DISTANCE_FIELDS = {"fallDistance", "field_6017", "f_19789_"};
    private static final String[] IS_FALL_FLYING_METHODS = {"isFallFlying", "method_6128", "m_21255_"};
    private static final String[] MAIN_HAND_METHODS = {"getMainHandItem", "getMainHandStack", "method_6047", "m_21205_"};
    private static final String[] GET_ITEM_METHODS = {"getItem", "method_7909", "m_41720_"};
    private static final String[] MACE_ITEM_CLASSES = {"net.minecraft.world.item.MaceItem", "net.minecraft.class_9362"};
    private static final String[] GET_X_METHODS = {"getX", "method_10263", "m_123341_"};
    private static final String[] GET_Y_METHODS = {"getY", "method_10264", "m_123342_"};
    private static final String[] GET_Z_METHODS = {"getZ", "method_10260", "m_123343_"};

    // Strength of our last click that was a smash, waiting for the vanilla event to give it a position
    private static long pendingStrengthNanos = 0L;
    private static float pendingStrength = 0.0F;

    // Active waves, a slot with waveStartNanos 0 is free
    private static final int[] waveX = new int[WAVE_SLOTS];
    private static final int[] waveY = new int[WAVE_SLOTS];
    private static final int[] waveZ = new int[WAVE_SLOTS];
    private static final long[] waveStartNanos = new long[WAVE_SLOTS];
    private static final double[] waveSeconds = new double[WAVE_SLOTS];
    private static final float[] waveStrength = new float[WAVE_SLOTS];

    private static Class<?> maceItemClass;
    private static Method mainHandMethod;
    private static Method getItemMethod;
    private static Field fallDistanceField;
    private static Method isFallFlyingMethod;
    private static Method getXMethod;
    private static Method getYMethod;
    private static Method getZMethod;
    private static boolean reflectionUnsupported = false;
    private static boolean positionUnsupported = false;

    private MaceTracker() {}

    // Our own attack click, runs before the server decides if it was a smash
    public static void onAttack(Object player) {
        if (player == null || reflectionUnsupported) return;
        try {
            if (!isHoldingMace(player)) return;
            float strength = smashStrength(player);
            if (strength < 0.0F) return;
            pendingStrength = strength;
            pendingStrengthNanos = System.nanoTime();
            debugLog("Mace smash click, strength = " + strength + ", animation = " + smashSeconds(strength) + "s");
        } catch (NoSuchFieldException | NoSuchMethodException | ClassNotFoundException | LinkageError e) {
            // Mappings don't match this version, don't retry on every hit
            debugLog("Mace strength unsupported: " + e);
            reflectionUnsupported = true;
        } catch (Throwable t) {
            debugLog("Error calculating mace strength: " + t);
        }
    }

    // Vanilla smash particle event, position is the block the hit entity stands on
    public static void onSmashEvent(Object blockPos) {
        if (blockPos == null || positionUnsupported) return;
        try {
            if (getXMethod == null) {
                Class<?> posClass = blockPos.getClass();
                getXMethod = ReflectionUtils.accessible(ReflectionUtils.tryMethods(posClass, GET_X_METHODS));
                getYMethod = ReflectionUtils.accessible(ReflectionUtils.tryMethods(posClass, GET_Y_METHODS));
                getZMethod = ReflectionUtils.accessible(ReflectionUtils.tryMethods(posClass, GET_Z_METHODS));
            }
            int x = (int) getXMethod.invoke(blockPos);
            int y = (int) getYMethod.invoke(blockPos);
            int z = (int) getZMethod.invoke(blockPos);

            long now = System.nanoTime();
            float strength = OTHER_PLAYER_STRENGTH;
            if (pendingStrengthNanos != 0L) {
                // Only our own smash can have a click waiting, hand its strength to this event
                if (now - pendingStrengthNanos <= OWN_SMASH_MATCH_NANOS) {
                    strength = pendingStrength;
                }
                pendingStrengthNanos = 0L;
            }
            startWave(x, y, z, strength, now);
            debugLog("Mace shockwave at " + x + " " + y + " " + z + ", strength = " + strength);
        } catch (NoSuchMethodException | LinkageError e) {
            debugLog("Mace smash position unsupported: " + e);
            positionUnsupported = true;
        } catch (Throwable t) {
            debugLog("Error reading mace smash position: " + t);
        }
    }

    private static void startWave(int x, int y, int z, float strength, long now) {
        int slot = 0;
        for (int i = 0; i < WAVE_SLOTS; i++) {
            if (waveStartNanos[i] == 0L) { // free slot
                slot = i;
                break;
            }
            if (waveStartNanos[i] < waveStartNanos[slot]) { // all busy, replace the oldest
                slot = i;
            }
        }
        waveX[slot] = x;
        waveY[slot] = y;
        waveZ[slot] = z;
        waveSeconds[slot] = smashSeconds(strength);
        waveStrength[slot] = strength;
        waveStartNanos[slot] = now;
    }

    private static double smashSeconds(float strength) {
        return WEAKEST_SMASH_SECONDS + (STRONGEST_SMASH_SECONDS - WEAKEST_SMASH_SECONDS) * strength;
    }

    private static boolean isHoldingMace(Object player) throws Exception {
        if (maceItemClass == null) {
            maceItemClass = ReflectionUtils.firstClass(MACE_ITEM_CLASSES);
            if (maceItemClass == null) {
                throw new ClassNotFoundException(String.join(" / ", MACE_ITEM_CLASSES));
            }
        }
        if (mainHandMethod == null) {
            mainHandMethod = ReflectionUtils.tryMethods(player.getClass(), MAIN_HAND_METHODS);
        }
        Object stack = mainHandMethod.invoke(player);
        if (stack == null) return false;
        if (getItemMethod == null) {
            getItemMethod = ReflectionUtils.tryMethods(stack.getClass(), GET_ITEM_METHODS);
        }
        return maceItemClass.isInstance(getItemMethod.invoke(stack));
    }

    // Block position of the wave in slot, only meaningful while getWaveProgress(slot) >= 0
    public static int getWaveX(int slot) { return waveX[slot]; }

    public static int getWaveY(int slot) { return waveY[slot]; }

    public static int getWaveZ(int slot) { return waveZ[slot]; }

    // 0 to 1 over the wave's duration, -1 if the slot has no active wave
    public static float getWaveProgress(int slot) {
        long start = waveStartNanos[slot];
        if (start == 0L) return -1.0F;
        double progress = (System.nanoTime() - start) / NANOS_PER_SECOND / waveSeconds[slot];
        if (progress >= 1.0) {
            waveStartNanos[slot] = 0L;
            return -1.0F;
        }
        return (float) progress;
    }

    public static float getWaveStrength(int slot) {
        return waveStartNanos[slot] == 0L ? 0.0F : waveStrength[slot];
    }

    // -1 = not a smash attack (fall <= 1.5 blocks or elytra), otherwise 0 (weakest smash) to 1 (fall >= 10 blocks)
    private static float smashStrength(Object player) throws Exception {
        if (fallDistanceField == null) {
            fallDistanceField = ReflectionUtils.tryFields(player.getClass(), FALL_DISTANCE_FIELDS);
        }
        double fallDistance = fallDistanceField.getDouble(player);
        if (fallDistance <= SMASH_FALL_THRESHOLD) return -1.0F;

        // While flying with an elytra vanilla apparently does not do any mace shockwave
        if (isFallFlyingMethod == null) {
            isFallFlyingMethod = ReflectionUtils.tryMethods(player.getClass(), IS_FALL_FLYING_METHODS);
        }
        if ((boolean) isFallFlyingMethod.invoke(player)) return -1.0F;

        float clampedFall = (float) Math.min(fallDistance, MAX_FALL_HEIGHT);
        return (clampedFall - SMASH_FALL_THRESHOLD) / (MAX_FALL_HEIGHT - SMASH_FALL_THRESHOLD);
    }

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[MaceTracker] " + message);
    }
}
