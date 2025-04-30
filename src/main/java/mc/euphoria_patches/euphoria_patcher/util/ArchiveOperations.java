package mc.euphoria_patches.euphoria_patcher.util;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import org.apache.commons.compress.archivers.ArchiveException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ArchiveOperations {

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[ArchiveOperations] " + message);
    }

    public static Path extract(Path source, Path targetDir, String operationName) {
        try {
            debugLog("Extracting: " + source.getFileName() + " to " + targetDir);
            if (!Files.isDirectory(source)) {
                ArchiveUtils.extract(source, targetDir);
                debugLog("Extraction completed successfully");
            } else {
                debugLog("Source is already a directory, no extraction needed");
                return source; // Already a directory
            }
            return targetDir;
        } catch (IOException | ArchiveException e) {
            debugLog("Error " + operationName + ": " + e.getMessage());
            EuphoriaPatcher.log(2, "Error " + operationName + ": " + e.getMessage());
            return null;
        }
    }

    public static Path archive(Path source, Path targetArchive) {
        try {
            debugLog("Archiving: " + source.getFileName() + " to " + targetArchive);
            ArchiveUtils.archive(source, targetArchive);
            debugLog("Archiving completed successfully");
            return targetArchive;
        } catch (IOException e) {
            debugLog("Error creating archive: " + e.getMessage());
            EuphoriaPatcher.log(2, "Error creating archive: " + e.getMessage());
            return null;
        }
    }

    public static boolean verifyBaseArchive(Path baseArchived) {
        try {
            debugLog("Verifying archive: " + baseArchived.getFileName());
            if (EuphoriaPatcher.isDevFunc()) {
                long fileSize = Files.size(baseArchived);
                EuphoriaPatcher.log(0, "Archive Name: " +  baseArchived.getFileName() + " Archive size: " + fileSize + " bytes");
            } else {
                // Get the file size
                long fileSize = Files.size(baseArchived);
                debugLog("Archive size: " + fileSize + " bytes, expected: " + EuphoriaPatcher.BASE_TAR_SIZE + " bytes");
                
                // Define acceptable size range (±5 bytes as suggested)
                // long expectedSize = 1328640; // Same as BASE_TAR_SIZE
                // boolean isValidSize = Math.abs(fileSize - expectedSize) <= 5;
                
                // Exact match for now
                boolean isValidSize = fileSize == EuphoriaPatcher.BASE_TAR_SIZE;
                
                if (!isValidSize) {
                    debugLog("Invalid archive size: verification failed");
                    String fileName = baseArchived.getFileName().toString();

                    // First identify if it's a newer version
                    if (EuphoriaPatcher.isNewerShaderVersion(fileName)) {
                        // Get version string from filename
                        String detectedVersion = EuphoriaPatcher.getVersionStringFromFileName(fileName);
                        
                        // Newer version detected - recommend mod update if available
                        EuphoriaPatcher.log(3, 8, "Newer shader version in shaderpacks folder detected: " + fileName.replace(".tar", ""));
                        EuphoriaPatcher.log(3, 8, EuphoriaPatcher.PATCH_NAME + EuphoriaPatcher.PATCH_VERSION + " only works with " + 
                            EuphoriaPatcher.BRAND_NAME + "Shaders" + EuphoriaPatcher.VERSION);
                        
                        if (UpdateChecker.isUpdateAvailable() && EuphoriaPatcher.doUpdateChecking) {
                            // Update available - recommend updating the mod
                            EuphoriaPatcher.log(3, 8, "Please update " + EuphoriaPatcher.PATCH_NAME + " to the latest version to support this shader version: " + detectedVersion);
                            EuphoriaPatcher.log(3, 8, "Download it from Modrinth: https://euphoriapatches.com/download");
                        } else {
                            // No update available - need the specific version
                            EuphoriaPatcher.log(3, 8, "This version of " + EuphoriaPatcher.PATCH_NAME + " requires " + 
                                EuphoriaPatcher.BRAND_NAME + "Shaders" + EuphoriaPatcher.VERSION);
                            EuphoriaPatcher.log(3, 8, "Please download it from " + EuphoriaPatcher.DOWNLOAD_URL + " or wait for an update.");
                        }
                    }
                    // Check if it's the correct version with incorrect size
                    else if (fileName.matches(EuphoriaPatcher.BRAND_NAME + ".*" + EuphoriaPatcher.VERSION + ".*")) {
                        EuphoriaPatcher.log(3, 8, "The shader " + EuphoriaPatcher.BRAND_NAME + "Shaders" + " that was found in your shaderpacks folder can't be used as a base for " + EuphoriaPatcher.PATCH_NAME);
                        EuphoriaPatcher.log(3, 8, "The shader file appears to be incomplete or has been modified.");
                        EuphoriaPatcher.log(3, 8, "Please re-download " + EuphoriaPatcher.BRAND_NAME + "Shaders" + EuphoriaPatcher.VERSION + 
                            " from " + EuphoriaPatcher.DOWNLOAD_URL + " and place it in the shaderpacks folder.");
                    }
                    // Wrong version completely
                    else {
                        EuphoriaPatcher.log(3, 8, "The shader " + EuphoriaPatcher.BRAND_NAME + "Shaders" + " that was found in your shaderpacks folder can't be used as a base for " + EuphoriaPatcher.PATCH_NAME);
                        EuphoriaPatcher.log(3, 8, "Please download " + EuphoriaPatcher.BRAND_NAME + "Shaders" + EuphoriaPatcher.VERSION + 
                            " from " + EuphoriaPatcher.DOWNLOAD_URL + " and place it in the shaderpacks folder.");
                    }

                    EuphoriaPatcher.log(0, "Watching for the correct shader to be added...");

                    // Start the watcher
                    EuphoriaPatcher instance = EuphoriaPatcher.getInstance();
                    if (instance != null) {
                        instance.startWatcherAfterByteSizeFailure();
                        // If we have a watcher, track this file as invalid
                        ShaderpacksWatcher watcher = instance.getShaderpacksWatcher();
                        if (watcher != null) {
                            watcher.trackInvalidByteSizeFile(fileName);
                        }
                    }

                    return false;
                }
                debugLog("Archive size verification passed");
            }
        } catch (IOException e) {
            debugLog("Error during archive size verification: " + e.getMessage());
            EuphoriaPatcher.log(3, "Something went wrong during the file size verification: " + e.getMessage());
            return false;
        }
        return true;
    }

    public static boolean verifyBaseArchiveQuiet(Path baseArchived) {
        try {
            debugLog("Quietly verifying archive: " + baseArchived.getFileName());
            if (EuphoriaPatcher.isDevFunc()) {
                debugLog("Dev mode: bypassing size verification (returning true)");
                return true; // In dev mode, accept any file
            } else {
                long fileSize = Files.size(baseArchived);
                
                // Define acceptable size range (±5 bytes)
                // long expectedSize = 1328640; // Same as BASE_TAR_SIZE
                // return Math.abs(fileSize - expectedSize) <= 5;
                
                boolean isValid = fileSize == EuphoriaPatcher.BASE_TAR_SIZE;
                debugLog("Archive size: " + fileSize + " bytes, expected: " + EuphoriaPatcher.BASE_TAR_SIZE + " bytes, valid: " + isValid);
                return isValid;
            }
        } catch (IOException e) {
            debugLog("Error during quiet archive verification: " + e.getMessage());
            return false;
        }
    }
}