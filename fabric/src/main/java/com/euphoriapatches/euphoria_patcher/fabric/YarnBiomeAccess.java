package com.euphoriapatches.euphoria_patcher.fabric;

import com.euphoriapatches.euphoria_patcher.util.Biomes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

final class YarnBiomeAccess {
    private YarnBiomeAccess() {}

    static Object getPlayerBlockPos(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        return player != null ? player.getBlockPos() : null;
    }

    /**
     * 1.18.2+: getBiome returns a RegistryEntry, whose key is parsed from RegistryKey.toString().
     */
    static String getBiomeId(Object world, Object pos) {
        return Biomes.parseId(((World) world).getBiome((BlockPos) pos).getKey());
    }
}
