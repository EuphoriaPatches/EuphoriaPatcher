package com.euphoriapatches.euphoria_patcher.util;

import java.lang.reflect.Method;
import java.util.Optional;

public final class Biomes {
    // Biome is re-queried at most once per tick
    private static final long REVALIDATE_NANOS = 50_000_000L; // 50ms

    private Biomes() {}

    /**
     * Extracts a namespaced biome ID from a ResourceKey, not using direct methods and this abstraction as across MC versions this changes.
     * @return biome ID like "minecraft:plains", or null if unavailable
     */
    public static String parseId(Object keyOrId) {
        if (keyOrId instanceof Optional) {
            keyOrId = ((Optional<?>) keyOrId).orElse(null);
        }
        if (keyOrId == null) {
            return null;
        }
        String str = keyOrId.toString();
        // Format: "ResourceKey[minecraft:worldgen/biome / minecraft:plains]"
        int separator = str.indexOf(" / ");
        if (separator >= 0) {
            int end = str.endsWith("]") ? str.length() - 1 : str.length();
            return str.substring(separator + 3, end).trim();
        }
        return str;
    }

    /**
     * @return true if the biome ID is from a namespace other than "minecraft"
     */
    public static boolean isModded(String biomeId) {
        return biomeId != null && !biomeId.startsWith("minecraft:");
    }

    /**
     * Reuses the last biome lookup for one tick
     */
    public static final class Cache {
        private String biomeId;
        private long lastLookup;
        private boolean hasValue = false;

        public boolean isValid() {
            return hasValue && System.nanoTime() - lastLookup < REVALIDATE_NANOS;
        }

        public String getBiomeId() {
            return biomeId;
        }

        public String update(String biomeId) {
            this.hasValue = true;
            this.biomeId = biomeId;
            this.lastLookup = System.nanoTime();
            return biomeId;
        }
    }

    /**
     * Reflective biome lookup: {@code level.getBiome(pos)}, optionally followed by {@code holder.unwrapKey()},
     * then parsed to a biome ID.
     */
    public static final class ReflectiveLookup {
        private final String[] getBiomeNames;
        private final String holderClassName;
        private final String unwrapKeyName;

        private Method getBiome;
        private Method unwrapKey;

        /**
         * @param getBiomeNames   candidate names of {@code getBiome(BlockPos)} (or any method returning the biome key/holder for a position)
         * @param holderClassName class declaring {@code unwrapKey}, or null if getBiome already returns the key
         * @param unwrapKeyName   name of {@code Holder.unwrapKey()}, or null if getBiome already returns the key
         */
        public ReflectiveLookup(String[] getBiomeNames, String holderClassName, String unwrapKeyName) {
            this.getBiomeNames = getBiomeNames;
            this.holderClassName = holderClassName;
            this.unwrapKeyName = unwrapKeyName;
        }

        public String lookup(Object level, Object pos) throws Exception {
            if (getBiome == null) {
                getBiome = findGetBiome(level, pos);
                if (unwrapKeyName != null) {
                    unwrapKey = Class.forName(holderClassName).getMethod(unwrapKeyName);
                }
            }
            Object result = getBiome.invoke(level, pos);
            if (result != null && unwrapKey != null) {
                result = unwrapKey.invoke(result);
            }
            return parseId(result);
        }

        private Method findGetBiome(Object level, Object pos) throws NoSuchMethodException {
            NoSuchMethodException lastFailure = null;
            for (String name : getBiomeNames) {
                try {
                    return ReflectionUtils.findMethodForInstance(level.getClass(), name, pos);
                } catch (NoSuchMethodException e) {
                    lastFailure = e;
                }
            }
            throw lastFailure != null ? lastFailure : new NoSuchMethodException("no getBiome candidates provided");
        }
    }
}
