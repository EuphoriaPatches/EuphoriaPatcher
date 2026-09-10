package com.euphoriapatches.euphoria_patcher.fabric;

import com.euphoriapatches.euphoria_patcher.util.Dimensions;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.mod.ModLoaderSpecifics;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;

public class FabricModLoaderSpecifics extends ModLoaderSpecifics {

    private final Path shaderpacksPath;
    private final Path configDirectory;
    private static Boolean useYarnMappings = null; // null = not yet determined

    // Cached reflection handles for the modern branch, since getLevel() runs every frame
    private static Class<?> modernMcClass;
    private static Method modernGetInstance;
    private static Field modernLevelField;
    private static Method modernDimensionMethod;
    private static Object modernMcInstance; // Minecraft singleton - set once, never reassigned

    public FabricModLoaderSpecifics() {
        this.shaderpacksPath = FabricLoader.getInstance().getGameDir().resolve("shaderpacks");
        this.configDirectory = FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public Path getShaderpacksPath() {
        return shaderpacksPath;
    }

    @Override
    public String getInstanceName() {
        return ModLoaderSpecifics.FABRIC;
    }

    @Override
    public Path getConfigDirectory() {
        return configDirectory;
    }

    @Override
    public boolean serverCheck() {
        try {
            if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
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
        return Dimensions.getCurrentDimension(getCurrentDimensionID());
    }

    @Override
    public boolean isCurrentDimensionInMappings() {
        return Dimensions.isCurrentDimensionInMappings(getCurrentDimensionID());
    }

    @Override
    public boolean setClipboard(String str) {
        if (useYarnMappings == null) discoverMappingBranch();

        try {
            long windowHandle;
            if (useYarnMappings) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client == null || client.getWindow() == null) {
                    debugLog("Client or window is null, cannot set clipboard");
                    return false;
                }
                windowHandle = client.getWindow().getHandle();
            } else {
                // Reflection: get window handle from net.minecraft.client.Minecraft
                Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
                Object mcInstance = mcClass.getMethod("getInstance").invoke(null);
                if (mcInstance == null) {
                    debugLog("Minecraft instance is null, cannot set clipboard");
                    return false;
                }

                Object window = mcClass.getMethod("getWindow").invoke(mcInstance);
                if (window == null) {
                    debugLog("Window is null, cannot set clipboard");
                    return false;
                }

                // Try to get the window handle
                Class<?> windowClass = window.getClass();
                debugLog("Window class: " + windowClass.getName());
                try {
                    windowHandle = (long) windowClass.getMethod("handle").invoke(window);
                    debugLog("Accessed window handle via handle() method");
                } catch (NoSuchMethodException e1) {
                    debugLog("handle() method not found");
                    return false;
                }
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
        if (useYarnMappings == null) discoverMappingBranch();

        // Use cached result
        if (useYarnMappings != null && useYarnMappings) {
            return getLevelYarn();
        } else if (useYarnMappings != null && !useYarnMappings) {
            return getLevelModern();
        }
        return null;
    }

    private String getCurrentDimensionID(){
        if (useYarnMappings == null) discoverMappingBranch();

        // Use cached result
        if (useYarnMappings != null && useYarnMappings) {
            return getCurrentDimensionIDYarn();
        } else if (useYarnMappings != null && !useYarnMappings) {
            return getCurrentDimensionIDModern();
        }
        return "minecraft:overworld";
    }

    /**
     * Discovers which mapping type is being used (Yarn vs Reflection) and caches the result.
     * Tries Yarn first, then falls back to reflection.
     */
    private void discoverMappingBranch() {
        if (useYarnMappings != null) {
            return; // Already discovered
        }

        // Try Yarn first
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                useYarnMappings = true;
                debugLog("Using Yarn mappings");
                return;
            }
        } catch (Throwable t) {
            debugLog("Yarn method failed, trying reflection: " + t.getMessage());
        }

        // Try reflection
        try {
            Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
            Object mcInstance = mcClass.getMethod("getInstance").invoke(null);
            if (mcInstance != null) {
                useYarnMappings = false;
                debugLog("Using reflection");
                return;
            }
        } catch (Throwable t) {
            debugLog("Reflection method also failed: " + t.getMessage());
        }
    }

    private String getCurrentDimensionIDYarn() {
        debugLog("Getting current dimension (Yarn)");
        try {
            Object world = getLevelYarn();

            if (world == null) {
                debugLog("Client or world is null, defaulting to 'minecraft:overworld'");
                return "minecraft:overworld";
            }

            Identifier dimensionId = ((net.minecraft.world.World) world).getRegistryKey().getValue();
            String currentDimensionId = dimensionId.toString();
            debugLog("Current dimension ID: " + currentDimensionId);

            return currentDimensionId;
        } catch (Exception e) {
            debugLog("Error in Yarn method: " + e.getMessage());
            return "minecraft:overworld";
        }
    }

    private String getCurrentDimensionIDModern() {
        debugLog("Getting current dimension (Reflection for net.minecraft.client.Minecraft)");

        try {
            Object level = getLevelModern();
            if (level == null) {
                debugLog("Level is null, defaulting to 'minecraft:overworld'");
                return "minecraft:overworld";
            }

            if (modernDimensionMethod == null) {
                modernDimensionMethod = level.getClass().getMethod("dimension");
            }
            Object dimension = modernDimensionMethod.invoke(level);
            String dimensionString = dimension.toString();
            debugLog("Dimension toString(): " + dimensionString);

            // Format: "ResourceKey[minecraft:dimension / minecraft:overworld]"
            String currentDimensionId;
            if (dimensionString.contains("/")) {
                currentDimensionId = dimensionString.substring(dimensionString.indexOf("/") + 1)
                        .replace("]", "").trim();
            } else {
                currentDimensionId = "minecraft:overworld";
            }

            debugLog("Current dimension ID (parsed): " + currentDimensionId);
            return currentDimensionId;
        } catch (Exception e) {
            debugLog("Error in reflection method: " + e.getMessage());
            return "minecraft:overworld";
        }
    }

    private Object getLevelYarn() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            return client != null ? client.world : null;
        } catch (Exception e) {
            debugLog("Error getting level (Yarn): " + e.getMessage());
            return null;
        }
    }

    private Object getLevelModern() {
        try {
            Object mcInstance = modernMinecraftInstance();
            if (mcInstance == null) {
                return null;
            }
            if (modernLevelField == null) {
                modernLevelField = modernMcClass.getField("level");
            }
            return modernLevelField.get(mcInstance);
        } catch (Exception e) {
            debugLog("Error getting level (reflection): " + e.getMessage());
            return null;
        }
    }

    private Object modernMinecraftInstance() throws Exception {
        if (modernMcInstance != null) {
            return modernMcInstance;
        }
        if (modernMcClass == null) {
            modernMcClass = Class.forName("net.minecraft.client.Minecraft");
        }
        if (modernGetInstance == null) {
            modernGetInstance = modernMcClass.getMethod("getInstance");
        }
        Object mcInstance = modernGetInstance.invoke(null);
        if (mcInstance != null) {
            modernMcInstance = mcInstance;
        }
        return mcInstance;
    }

    private void debugLog(String message) {
        EuphoriaLogger.debugLog("[FabricModLoaderSpecifics] " + message);
    }
}
