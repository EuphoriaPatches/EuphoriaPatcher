package com.euphoriapatches.euphoria_patcher.forge_legacy;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.mod.ModLoaderSpecifics;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.nio.file.Path;

public class ForgeLegacyModLoaderSpecifics extends ModLoaderSpecifics {

    private final Path shaderpacksPath;
    private final Path configDirectory;

    public ForgeLegacyModLoaderSpecifics() {
        this.shaderpacksPath = Minecraft.getMinecraft().gameDir.toPath().resolve("shaderpacks");
        this.configDirectory = Minecraft.getMinecraft().gameDir.toPath().resolve("config");
    }

    @Override
    public Path getShaderpacksPath() {
        return shaderpacksPath;
    }

    @Override
    public String getInstanceName() {
        return ModLoaderSpecifics.FORGE_LEGACY;
    }

    @Override
    public Path getConfigDirectory() {
        return configDirectory;
    }

    @Override
    public boolean serverCheck() {
        try {
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            if (server != null && server.isDedicatedServer()) {
                System.err.println("[EuphoriaPatcher] Server Detected! The Euphoria Patcher Mod disables itself gracefully on a server. Disabling...");
                return true;
            }
        } catch (Throwable t) {
            // Any error, assume not a server
        }
        return false;
    }

    @Override
    public String getCurrentDimension() {
        return "overworld";
    }

    @Override
    public boolean isCurrentDimensionInMappings() {
        return true;
    }

    @Override
    public boolean setClipboard(String str) {
        return false;
    }

    @Override
    public boolean isTimeAdvancing() {
        return true;
    }

    @Override
    public Object getLevel() {
        return null;
    }

    @Override
    public String getCurrentBiomeName() {
        return null;
    }

    @Override
    public boolean isCurrentBiomeModded() {
        return false;
    }

    @SuppressWarnings("unused")
    private void debugLog(String message) {
        EuphoriaLogger.debugLog("[ForgeLegacyModLoaderSpecifics] " + message);
    }
}
