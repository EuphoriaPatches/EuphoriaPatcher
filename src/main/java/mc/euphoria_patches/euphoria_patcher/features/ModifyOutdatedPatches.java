package mc.euphoria_patches.euphoria_patcher.features;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import mc.euphoria_patches.euphoria_patcher.util.EuphoriaLogger;
import mc.euphoria_patches.euphoria_patcher.util.UsefulFunctions;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class ModifyOutdatedPatches {
    
    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[ModifyOutdatedPatches] " + message);
    }
    
    public static void rename() {
        debugLog("Starting rename process for outdated patches");
        int renamedCount = 0;
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(EuphoriaPatcher.shaderpacks, path -> isOutdatedPatch(path, true))) {
            for (Path potentialFile : stream) {
                String name = potentialFile.getFileName().toString();
                debugLog("Found outdated pack to rename: " + name);
                
                String newName = name
                        .replaceFirst("(.*) \\+", "§cOutdated§r $1 +")
                        .replace("EuphoriaPatches_", "EP_");
                
                debugLog("Renaming to: " + newName);
                Files.move(potentialFile, potentialFile.resolveSibling(newName));
                EuphoriaPatcher.log(0, "Successfully renamed outdated " + name + " shaderpack file!");
                renamedCount++;
            }
            
            if (renamedCount == 0) {
                debugLog("No outdated patches found to rename");
            } else {
                debugLog("Renamed " + renamedCount + " outdated patch files");
            }
        } catch (IOException e) {
            debugLog("Error reading shaderpacks directory: " + e.getMessage());
            EuphoriaPatcher.log(3, 0, "Error reading shaderpacks directory: " + e.getMessage());
        }
    }

    public static void delete() {
        debugLog("Starting delete process for outdated patches");
        int deletedCount = 0;
        
        try (Stream<Path> stream = Files.list(EuphoriaPatcher.shaderpacks)) {
            stream.forEach(path -> {
                try {
                    if (isOutdatedPatch(path, false)) {
                        debugLog("Found outdated pack to delete: " + path.getFileName());
                        UsefulFunctions.deleteRecursively(path);
                        EuphoriaPatcher.log(0, "Successfully deleted outdated " + path.getFileName() + " shaderpack file!");
                        deletedCount++;
                    }
                } catch (IOException e) {
                    debugLog("Error processing path: " + path + " - " + e.getMessage());
                    EuphoriaPatcher.log(2, 0, "Error processing path: " + path + " - " + e.getMessage());
                }
            });
            
            if (deletedCount == 0) {
                debugLog("No outdated patches found to delete");
            } else {
                debugLog("Deleted " + deletedCount + " outdated patch files");
            }
        } catch (IOException e) {
            debugLog("Error reading shaderpacks directory: " + e.getMessage());
            EuphoriaPatcher.log(3, 0, "Error reading shaderpacks directory: " + e.getMessage());
        }
    }

    private static boolean isOutdatedPatch(Path path, boolean renameFile) {
        String name = path.getFileName().toString();
        debugLog("Checking if patch is outdated: " + name + " (for " + (renameFile ? "rename" : "delete") + ")");
        
        boolean result;
        if (renameFile) {
            result = name.contains(EuphoriaPatcher.PATCH_NAME) && !name.contains(EuphoriaPatcher.PATCH_VERSION);
        } else {
            result = (name.contains(EuphoriaPatcher.PATCH_NAME) || name.matches(".*Outdated.*" + EuphoriaPatcher.BRAND_NAME + ".* \\+ EP.*")) 
                    && !name.contains(EuphoriaPatcher.PATCH_VERSION);
        }
        
        debugLog("Patch " + name + " is " + (result ? "outdated" : "current"));
        return result;
    }
}
