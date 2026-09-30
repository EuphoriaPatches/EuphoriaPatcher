package com.euphoriapatches.euphoria_patcher.neoforge;

import com.euphoriapatches.euphoria_patcher.util.Biomes;
import com.euphoriapatches.euphoria_patcher.util.Dimensions;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.mod.ModLoaderSpecifics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import java.util.Objects;

public class NeoforgeModLoaderSpecifics extends ModLoaderSpecifics {

    private final Path shaderpacksPath;
    private final Path configDirectory;

    // Biome only needs re-querying when the player changes block or level
    private static final Biomes.Cache biomeCache = new Biomes.Cache();

    public NeoforgeModLoaderSpecifics() {
        this.shaderpacksPath = FMLPaths.GAMEDIR.get().resolve("shaderpacks");
        this.configDirectory = FMLPaths.CONFIGDIR.get();
    }

    @Override
    public Path getShaderpacksPath() {
        return shaderpacksPath;
    }

    @Override
    public String getInstanceName() {
        return ModLoaderSpecifics.NEOFORGE;
    }

    @Override
    public Path getConfigDirectory() {
        return configDirectory;
    }

    @Override
    public boolean serverCheck() {
        try {
            // Try to use getDist() if available (NeoForge 1.21.10+)
            java.lang.reflect.Method getDistMethod = FMLEnvironment.class.getMethod("getDist");
            Object dist = getDistMethod.invoke(null);
            if (dist == Dist.DEDICATED_SERVER) {
                System.err.println("[EuphoriaPatcher] Server Detected! The Euphoria Patcher Mod disables itself gracefully on a server. Disabling...");
                return true;
            }
        } catch (NoSuchMethodException e) {
            // Fallback for older NeoForge versions
            if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
                System.err.println("[EuphoriaPatcher] Server Detected! The Euphoria Patcher Mod disables itself gracefully on a server. Disabling...");
                return true;
            }
        } catch (Throwable t) {
            // Any other error, assume not a server
        }
        return false;
    }

    @Override
    public String getCurrentDimension() {
        return Dimensions.getCurrentDimension(getCurrentDimensionID());
    }

    @Override
    public boolean isCurrentDimensionInMappings() {
        return Dimensions.isCurrentDimensionInMappings(getCurrentDimensionID());
    }

    @Override
    public boolean setClipboard(String str) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            Object window = minecraft.getWindow();
            long windowHandle;

            Class<?> windowClass = window.getClass();
            try {
                // Try handle() first (newer versions)
                windowHandle = (long) windowClass.getMethod("handle").invoke(window);
                debugLog("Using handle() method to get window handle");
            } catch (NoSuchMethodException e1) { // Fallback to getWindow()
                windowHandle = (long) windowClass.getMethod("getWindow").invoke(window);
                debugLog("Using getWindow() method to get window handle");
            }

            try {
                org.lwjgl.glfw.GLFW.glfwSetClipboardString(windowHandle, str);
            } catch (Throwable t) {
                // Modern Minecraft versions switched their windowing backend from GLFW to SDL3
                debugLog("GLFW clipboard failed, trying SDL3: " + t.getMessage());
                Class<?> sdlClipboardClass = Class.forName("org.lwjgl.sdl.SDLClipboard");
                sdlClipboardClass.getMethod("SDL_SetClipboardText", CharSequence.class).invoke(null, str);
            }
            return true;
        } catch (Throwable e) {
            debugLog("Error setting clipboard: " + e.getMessage());
        }
        return false;
    }

    @Override
    public boolean isTimeAdvancing() {
        return GameRuleChecker.getInstance().isTimeAdvancing();
    }

    @Override
    public Object getLevel() {
        try {
            return Minecraft.getInstance().level;
        } catch (Throwable t) {
            debugLog("Error getting level: " + t.getMessage());
            return null;
        }
    }

    @Override
    public String getCurrentBiomeName() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            Level level = minecraft.level;
            LocalPlayer player = minecraft.player;
            if (level == null || player == null) {
                return null;
            }

            BlockPos pos = player.blockPosition();
            if (biomeCache.isValid(level, pos)) {
                return biomeCache.getBiomeId();
            }

            // ResourceKey is parsed from toString() since location() was renamed in newer versions
            String biomeId = Biomes.parseId(level.getBiome(pos).unwrapKey());
            if (!Objects.equals(biomeId, biomeCache.getBiomeId())) {
                debugLog("Current biome ID: " + biomeId);
            }
            return biomeCache.update(level, pos, biomeId);
        } catch (Throwable t) {
            debugLog("Error getting current biome: " + t.getMessage());
            return null;
        }
    }

    @Override
    public boolean isCurrentBiomeModded() {
        return Biomes.isModded(getCurrentBiomeName());
    }

    private String getCurrentDimensionID() {
        try {
            debugLog("Getting current dimension ID");

            Level level = (Level) getLevel();
            if (level == null) {
                debugLog("Minecraft or level is null, defaulting to 'minecraft:overworld'");
                return "minecraft:overworld";
            }

            String currentDimensionId;
            String dimensionString = level.dimension().toString();
            debugLog("Dimension toString(): " + dimensionString);
            // Format: "ResourceKey[minecraft:dimension / minecraft:overworld]"
            if (dimensionString.contains("/")) {
                currentDimensionId = dimensionString.substring(dimensionString.indexOf("/") + 1)
                        .replace("]", "").trim();
            } else {
                currentDimensionId = "minecraft:overworld";
            }

            debugLog("Current dimension ID: " + currentDimensionId);
            return currentDimensionId;
        } catch (Throwable t) {
            debugLog("Unexpected error getting current dimension ID: " + t.getMessage());
            return "minecraft:overworld";
        }
    }

    private void debugLog(String message) {
        EuphoriaLogger.debugLog("[NeoforgeModLoaderSpecifics] " + message);
    }
}
