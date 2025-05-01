package mc.euphoria_patches.euphoria_patcher.util;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import mc.euphoria_patches.euphoria_patcher.features.UpdateShaderConfig;
import mc.euphoria_patches.euphoria_patcher.features.UpdateShaderLoaderConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;

public class IrisDefineHelper {
    private static int injectCount = 0;
    private static boolean injectedOnce = false;

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[IrisDefineHelper] " + message);
    }

    public static void addEuphoriaDefines(List<?> standardDefines, boolean isLegacy,
                                         BiConsumer<List<?>, String> defineKey,
                                         BiConsumer<List<?>, String[]> defineKeyValue) {
        try {
            injectCount++;
            debugLog("Adding Euphoria Patches defines to Iris" + (isLegacy ? " (Legacy)" : ""));

            if (EuphoriaPatcher.isSpacEagle()) defineKey.accept(standardDefines, "SPACEAGLE17");
            debugLog("Adding SPACEEAGLE17 define");

            String currentVersion = formatVersion(EuphoriaPatcher.PATCH_VERSION);
            defineKeyValue.accept(standardDefines, new String[]{"CURRENT_EUPHORIA_PATCHES_VERSION", currentVersion});
            debugLog("Adding CURRENT_EUPHORIA_PATCHES_VERSION define");

            defineKey.accept(standardDefines, "EUPHORIA_PATCHES_MOD_INSTALLED");
            debugLog("Adding EUPHORIA_PATCHES_MOD_INSTALLED define");
            
            String currentDimension = "CURRENT_EUPHORIA_PATCHES_DIMENSION_" + ModLoaderSpecifics.getCurrentDimension().toUpperCase();
            defineKey.accept(standardDefines, currentDimension);
            debugLog("Adding " + currentDimension + " define");

            if (ShaderLoader.getShaderLoader().equals(ShaderLoader.IRIS)) {
                defineKey.accept(standardDefines, "EUPHORIA_PATCHES_IRIS");
                debugLog("Adding EUPHORIA_PATCHES_IRIS define");
            }
            
            // Check for potato.png file and add the define if it doesn't exist
            Path currentShaderpack = UpdateShaderLoaderConfig.getCurrentShaderpackPath();
            if (currentShaderpack != null) {
                boolean potatoExists = checkPotatoExists(currentShaderpack);
                
                if (!potatoExists) {
                    defineKey.accept(standardDefines, "EUPHORIA_PATCHES_POTATO_REMOVED");
                    debugLog("Adding EUPHORIA_PATCHES_POTATO_REMOVED define - potato.png not found");
                } else {
                    debugLog("Not adding EUPHORIA_PATCHES_POTATO_REMOVED define - potato.png found");
                }
            } else {
                debugLog("Cannot check for potato.png - currentShaderpack is null");
            }

            if (UpdateChecker.NEW_VERSION_AVAILABLE && EuphoriaPatcher.doUpdateChecking
                && EuphoriaPatcher.doDisplayShaderInGameMessage && !injectedOnce) {
                    
                defineKey.accept(standardDefines, "NEW_EUPHORIA_PATCHES_UPDATE");
                debugLog("Adding NEW_EUPHORIA_PATCHES_UPDATE define");

                if (UpdateChecker.NEW_MOD_VERSION != null) {
                    String nextVersionFormatted = formatVersion(UpdateChecker.NEW_MOD_VERSION);
                    defineKeyValue.accept(standardDefines, new String[]{"NEXT_EUPHORIA_PATCHES_VERSION", nextVersionFormatted});
                    debugLog("Adding NEXT_EUPHORIA_PATCHES_VERSION: " + nextVersionFormatted + " define");
                }
            }

            UpdateShaderConfig.markEuphoriaPatchesSettingsFiles();

            if (injectCount == 1) {
                EuphoriaPatcher.log(0, "Added Euphoria Patches defines to Iris" + (isLegacy ? " (Legacy)" : ""));
            }

            injectedOnce = true;
        } catch (Exception e) {
            debugLog("Exception while adding defines: " + e.getMessage());
        }
    }
    
    /**
     * Checks whether potato.png exists in the given shaderpack path.
     * Handles both directory and ZIP file shaderpacks.
     */
    private static boolean checkPotatoExists(Path shaderpackPath) {
        String potatoRelativePath = "shaders/lib/textures/potato.png";
        debugLog("Checking for potato.png in shaderpack: " + shaderpackPath);
        
        try {
            // Check if it's a directory
            if (Files.isDirectory(shaderpackPath)) {
                Path potatoPath = shaderpackPath.resolve(potatoRelativePath);
                boolean exists = Files.exists(potatoPath);
                debugLog("Directory shaderpack: potato.png " + (exists ? "exists" : "does not exist") + " at " + potatoPath);
                return exists;
            }
            
            // Check if it's a ZIP file
            if (Files.isRegularFile(shaderpackPath) && 
                shaderpackPath.toString().toLowerCase().endsWith(".zip")) {
                
                try (ZipFile zipFile = new ZipFile(shaderpackPath.toFile())) {
                    ZipEntry entry = zipFile.getEntry(potatoRelativePath);
                    boolean exists = entry != null;
                    debugLog("ZIP shaderpack: potato.png " + (exists ? "exists" : "does not exist") + " in " + shaderpackPath);
                    return exists;
                } catch (IOException e) {
                    debugLog("Error reading ZIP file: " + e.getMessage());
                    return false;
                }
            }
            
            debugLog("Shaderpack is neither a directory nor a ZIP file: " + shaderpackPath);
            return false;
        } catch (Exception e) {
            debugLog("Exception checking for potato.png: " + e.getMessage());
            return false;
        }
    }

    public static String formatVersion(String version) {
        String[] versionParts = version.replace("_", "").split("\\.");
        StringBuilder versionBuilder = new StringBuilder();
        for (int i = 0; i < versionParts.length; i++) {
            versionBuilder.append("_").append(versionParts[i]);
            if (i < versionParts.length - 1) {
                versionBuilder.append(", _dot, ");
            }
        }
        return versionBuilder.toString();
    }
}