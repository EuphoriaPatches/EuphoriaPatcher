package com.euphoriapatches.euphoria_patcher.integration.mace;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

// Progress of animation started by mace smash, faster fall = shorter animation, 0 if there never was a smash
public final class MaceTracker {

    private static final float SMASH_FALL_THRESHOLD = 1.5F;
    private static final float MAX_FALL_HEIGHT = 10.0F; // Magic number since vanilla scales up to infinity and 10 seems good
    private static final double WEAKEST_SMASH_SECONDS = 0.85; // animation length at the smash threshold
    private static final double STRONGEST_SMASH_SECONDS = 0.45; // animation length at MAX_FALL_HEIGHT or more
    private static final double NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);

    private static final String[] FALL_DISTANCE_FIELDS = {"fallDistance", "field_6017", "f_19789_"};
    private static final String[] IS_FALL_FLYING_METHODS = {"isFallFlying", "method_6128", "m_21255_"};
    private static final String[] MAIN_HAND_METHODS = {"getMainHandItem", "getMainHandStack", "method_6047", "m_21205_"};
    private static final String[] GET_ITEM_METHODS = {"getItem", "method_7909", "m_41720_"};
    private static final String[] MACE_ITEM_CLASSES = {"net.minecraft.world.item.MaceItem", "net.minecraft.class_9362"};

    // System.nanoTime() of the last mace smash, 0 if there never was one
    private static volatile long lastSmashNanos = 0L;
    private static volatile double lastSmashSeconds = WEAKEST_SMASH_SECONDS;
    private static volatile boolean animationFinished = false; // Early exit on animation finish

    private static Class<?> maceItemClass;
    private static Method mainHandMethod;
    private static Method getItemMethod;
    private static Field fallDistanceField;
    private static Method isFallFlyingMethod;
    private static boolean reflectionUnsupported = false;

    private MaceTracker() {}

    public static void onAttack(Object player) {
        if (player == null || reflectionUnsupported) return;
        try {
            if (!isHoldingMace(player)) return;
            float strength = smashStrength(player);
            if (strength < 0.0F) return;
            lastSmashSeconds = WEAKEST_SMASH_SECONDS + (STRONGEST_SMASH_SECONDS - WEAKEST_SMASH_SECONDS) * strength;
            lastSmashNanos = System.nanoTime();
            animationFinished = false;
            debugLog("Mace smash, strength = " + strength + ", animation = " + lastSmashSeconds + "s");
        } catch (NoSuchFieldException | NoSuchMethodException | ClassNotFoundException | LinkageError e) {
            // Mappings don't match this version, don't retry on every hit
            debugLog("Mace strength unsupported: " + e);
            reflectionUnsupported = true;
        } catch (Throwable t) {
            debugLog("Error calculating mace strength: " + t);
        }
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

    // Progress of the animation started by the last mace smash: no smash yet = 0
    // from 0 to 1 over time that shrinks with smash strength and stays at 1 afterwards. A new smash restarts it.
    public static float getSmashProgress() {
        if (animationFinished) return 1.0F;
        long smashNanos = lastSmashNanos;
        if (smashNanos == 0L) return 0.0F;
        double progress = (System.nanoTime() - smashNanos) / NANOS_PER_SECOND / lastSmashSeconds;
        if (progress >= 1.0) {
            animationFinished = true;
            return 1.0F;
        }
        return (float) progress;
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
