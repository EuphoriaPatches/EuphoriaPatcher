package com.euphoriapatches.euphoria_patcher.forge;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.Dimensions;
import com.euphoriapatches.euphoria_patcher.util.mod.ModLoaderSpecifics;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;


public class ForgeModLoaderSpecifics extends ModLoaderSpecifics {

    private final Path shaderpacksPath;
    private final Path configDirectory;
    // 0 = unknown, 1 = obfuscated, 2 = modern, 3 = pre-1.16.5
    private static int mappingBranch = 0;

    // Cached reflection handles, since getLevel() runs every frame
    private static Method cachedGetInstance;
    private static Field cachedLevelField;
    private static Method cachedDimensionMethod;
    private static Object cachedMinecraft; // Minecraft singleton - set once, never reassigned

    public ForgeModLoaderSpecifics() {
        this.shaderpacksPath = FMLPaths.GAMEDIR.get().resolve("shaderpacks");
        this.configDirectory = FMLPaths.CONFIGDIR.get();
    }

    @Override
    public Path getShaderpacksPath() {
        return shaderpacksPath;
    }

    @Override
    public String getInstanceName() {
        return ModLoaderSpecifics.FORGE;
    }

    @Override
    public Path getConfigDirectory() {
        return configDirectory;
    }

    @Override
    public boolean serverCheck() {
        try {
            if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
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
        if (mappingBranch == 0) discoverMappingBranch();

        // Use cached result
        if (mappingBranch == 1) {
            return setClipboardObfuscated(str);
        } else if (mappingBranch == 2) {
            return setClipboardModern(str);
        } else if (mappingBranch == 3) {
            return setClipboardObfuscatedPre11605(str);
        }

        return false;
    }

    @Override
    public boolean isTimeAdvancing() {
        return GameRuleChecker.getInstance().isTimeAdvancing();
    }

    @Override
    public Object getLevel() {
        if (mappingBranch == 0) discoverMappingBranch();
        if (mappingBranch == 0) return null;

        try {
            Object minecraft = cachedMinecraft();
            if (minecraft == null) {
                return null;
            }
            if (cachedLevelField == null) {
                cachedLevelField = Minecraft.class.getField(mappingBranch == 1 ? "f_91073_"
                        : mappingBranch == 3 ? "field_71441_e" : "level");
            }
            return cachedLevelField.get(minecraft);
        } catch (Exception e) {
            debugLog("Error getting level (branch " + mappingBranch + "): " + e.getMessage());
            return null;
        }
    }

    private Object cachedMinecraft() throws Exception {
        if (cachedMinecraft != null) {
            return cachedMinecraft;
        }
        if (cachedGetInstance == null) {
            cachedGetInstance = Minecraft.class.getMethod(mappingBranch == 1 ? "m_91087_"
                    : mappingBranch == 3 ? "func_71410_x" : "getInstance");
        }
        Object minecraft = cachedGetInstance.invoke(null);
        if (minecraft != null) {
            cachedMinecraft = minecraft;
        }
        return minecraft;
    }

    private String getCurrentDimensionID() {
        if (mappingBranch == 0) discoverMappingBranch();

        // Use cached result
        if (mappingBranch == 1) {
            return getCurrentDimensionIDObfuscated();
        } else if (mappingBranch == 2) {
            return getCurrentDimensionIDModern();
        } else if (mappingBranch == 3) {
            return getCurrentDimensionIDObfuscatedPre11605();
        }
        return "minecraft:overworld";
    }

    private boolean setClipboardObfuscated(String str) {
        try {
            Class<?> minecraftClass = Minecraft.class;
            Object minecraft = minecraftClass.getMethod("m_91087_").invoke(null);

            if (minecraft == null) {
                debugLog("Minecraft instance is null, cannot set clipboard");
                return false;
            }

            Object window = minecraftClass.getMethod("m_91268_").invoke(minecraft);
            debugLog("Accessed window via m_91268_ method");

            if (window == null) {
                debugLog("Window is null, cannot set clipboard");
                return false;
            }

            Class<?> windowClass = window.getClass();
            long windowHandle = (long) windowClass.getMethod("m_85439_").invoke(window);
                debugLog("Accessed window handle via m_85439_ method");

            org.lwjgl.glfw.GLFW.glfwSetClipboardString(windowHandle, str);
            return true;
        } catch (Throwable e) {
            debugLog("Error setting clipboard (obfuscated): " + e.getMessage());
            return false;
        }
    }

    private boolean setClipboardModern(String str) {
        try {
            Class<?> minecraftClass = Minecraft.class;
            Object minecraft = minecraftClass.getMethod("getInstance").invoke(null);

            if (minecraft == null) {
                debugLog("Minecraft instance is null, cannot set clipboard");
                return false;
            }

            Object window = minecraftClass.getMethod("getWindow").invoke(minecraft);

            if (window == null) {
                debugLog("Window is null, cannot set clipboard");
                return false;
            }

            Class<?> windowClass = window.getClass();
            long windowHandle;
            try {
                windowHandle = (long) windowClass.getMethod("handle").invoke(window);
            } catch (NoSuchMethodException e1) {
                windowHandle = (long) windowClass.getMethod("getWindow").invoke(window);
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
            debugLog("Error setting clipboard (modern): " + e.getMessage());
            return false;
        }
    }

    private boolean setClipboardObfuscatedPre11605(String str) {
        try {
            Class<?> minecraftClass = Minecraft.class;
            Object minecraft = minecraftClass.getMethod("func_71410_x").invoke(null);

            if (minecraft == null) {
                debugLog("Minecraft instance is null, cannot set clipboard");
                return false;
            }

            Object window = minecraftClass.getMethod("func_228018_at_").invoke(minecraft);
            debugLog("Accessed window via func_228018_at_ method");

            if (window == null) {
                debugLog("Window is null, cannot set clipboard");
                return false;
            }

            Class<?> windowClass = window.getClass();
            long windowHandle = (long) windowClass.getMethod("func_198092_i").invoke(window);
            debugLog("Accessed window handle via func_198092_i method");

            org.lwjgl.glfw.GLFW.glfwSetClipboardString(windowHandle, str);
            return true;
        } catch (Throwable e) {
            debugLog("Error setting clipboard (pre-1.16.5): " + e.getMessage());
            return false;
        }
    }

    /**
     * Discovers which mapping branch is being used and caches the result.
     * Tries obfuscated, then modern, then pre-1.16.5.
     */
    private void discoverMappingBranch() {
        if (mappingBranch != 0) {
            return; // Already discovered
        }

        // Try obfuscated first
        try {
            Class<?> minecraftClass = Minecraft.class;
            minecraftClass.getMethod("m_91087_").invoke(null);
            mappingBranch = 1;
            debugLog("Using obfuscated mappings");
            return;
        } catch (Throwable t) {
            debugLog("Obfuscated method failed, trying modern: " + t.getMessage());
        }

        // Try modern
        try {
            Class<?> minecraftClass = Minecraft.class;
            minecraftClass.getMethod("getInstance").invoke(null);
            mappingBranch = 2;
            debugLog("Using modern mappings");
            return;
        } catch (Throwable t) {
            debugLog("Modern method failed, trying pre-1.16.5: " + t.getMessage());
        }

        // Try pre-1.16.5
        try {
            Class<?> minecraftClass = Minecraft.class;
            minecraftClass.getMethod("func_71410_x").invoke(null);
            mappingBranch = 3;
            debugLog("Using obfuscated pre-1.16.5 mappings");
            return;
        } catch (Throwable t) {
            debugLog("Pre-1.16.5 method also failed: " + t.getMessage());
        }
    }

    private String getCurrentDimensionIDObfuscated() {
        debugLog("Getting current dimension ID (obfuscated mappings)");

        try {
            Object level = getLevel();

            if (level == null) {
                return "minecraft:overworld";
            }

            // Get dimension key using obfuscated method
            Object dimensionKey = level.getClass().getMethod("m_46472_").invoke(level);
            debugLog("Got dimension key using m_46472_");

            // Get location using obfuscated method
            Object location = dimensionKey.getClass().getMethod("m_135782_").invoke(dimensionKey);
            debugLog("Got location using m_135782_");

            String currentDimensionId = location.toString();
            debugLog("Dimension ID: " + currentDimensionId);

            return currentDimensionId;
        } catch (Exception e) {
            debugLog("Error in obfuscated method: " + e.getClass().getName() + " - " + e.getMessage());
            return "minecraft:overworld";
        }
    }

    private String getCurrentDimensionIDModern() {
        debugLog("Getting current dimension ID (modern mappings)");

        try {
            Object level = getLevel();

            if (level == null) {
                return "minecraft:overworld";
            }

            // Get dimension key using modern method
            if (cachedDimensionMethod == null) {
                cachedDimensionMethod = level.getClass().getMethod("dimension");
            }
            Object dimensionKey = cachedDimensionMethod.invoke(level);
            debugLog("Got dimension key using dimension");

            // Modern version doesn't have working location(), parse from toString
            String dimensionString = dimensionKey.toString();
            debugLog("Dimension key toString(): " + dimensionString);

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
            debugLog("Error in modern method: " + e.getClass().getName() + " - " + e.getMessage());
            return "minecraft:overworld";
        }
    }

     private String getCurrentDimensionIDObfuscatedPre11605() {
        debugLog("Getting current dimension ID (obfuscated pre-1.16.5)");

        try {
            Object world = getLevel();

            if (world == null) {
                return "minecraft:overworld";
            }

            // Get dimension key using obfuscated method (1.16.5: func_234923_W_ = getDimensionKey)
            Object dimensionKey = world.getClass().getMethod("func_234923_W_").invoke(world);
            debugLog("Got dimension key using func_234923_W_ " + dimensionKey.toString());

            // Get location from dimension key (1.16.5: func_240901_a_ = getLocation)
            Object location = dimensionKey.getClass().getMethod("func_240901_a_").invoke(dimensionKey);
            debugLog("Got location using func_240901_a_ " + location.toString());

            String currentDimensionId = location.toString();
            debugLog("Dimension ID: " + currentDimensionId);

            return currentDimensionId;
        } catch (Exception e) {
            debugLog("Error in obfuscated pre-1.16.5 method: " + e.getClass().getName() + " - " + e.getMessage());
            return "minecraft:overworld";
        }
    }

    private void debugLog(String message) {
        EuphoriaLogger.debugLog("[ForgeModLoaderSpecifics] " + message);
    }
}
