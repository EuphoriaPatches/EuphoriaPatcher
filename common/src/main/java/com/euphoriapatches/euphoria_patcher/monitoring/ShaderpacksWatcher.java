package com.euphoriapatches.euphoria_patcher.monitoring;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.logging.ErrorShaderGenerator;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.services.PatchLibrary;
import com.euphoriapatches.euphoria_patcher.services.ShaderDetector;
import com.euphoriapatches.euphoria_patcher.services.ShaderNamingService;
import com.euphoriapatches.euphoria_patcher.services.ShaderValidator;
import com.euphoriapatches.euphoria_patcher.util.UserInstallErrorMessages;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ShaderpacksWatcher {
    private final Path shaderpacks;
    private final WatchService watchService;
    private final ScheduledExecutorService executor;
    private final ShaderpacksWatcherUtils utilsInstance;
    private boolean isRunning = false;
    // Track processed files to avoid duplicates
    private final Set<String> processedFiles = new HashSet<>();
    // Track files that failed byte size verification so we can recheck them
    private final Set<String> invalidByteSizeFiles = new HashSet<>();
    // Track file metadata to detect content changes even with the same filename
    private final Map<String, FileMetadata> fileMetadata = new HashMap<>();
    // Cache for byte size verification results to avoid repeated processing
    private final Map<String, Boolean> byteSizeVerificationCache = new HashMap<>();
    // Time of last byte size verification to prevent excessive checking
    private long lastByteSizeVerificationTime = 0;
    // Minimum time between byte size verifications (5 seconds)
    private static final long BYTE_SIZE_VERIFICATION_COOLDOWN = 5000;

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[ShaderpacksWatcher] " + message);
    }

    /**
     * Get the ShaderDetector service from the patcher instance
     */
    private ShaderDetector getShaderDetector() {
        EuphoriaPatcher euphoriaPatcher = EuphoriaPatcher.getInstance();
        return euphoriaPatcher != null ? euphoriaPatcher.getShaderDetector() : null;
    }

    /**
     * Get the ShaderNamingService from the patcher instance
     */
    private ShaderNamingService getNamingService() {
        EuphoriaPatcher euphoriaPatcher = EuphoriaPatcher.getInstance();
        return euphoriaPatcher != null ? euphoriaPatcher.getNamingService() : null;
    }

    public ShaderpacksWatcher(ShaderpacksWatcherUtils patcher) throws IOException {
        this(patcher, false);
    }

    public ShaderpacksWatcher(ShaderpacksWatcherUtils utilsInstance, boolean skipInitialScan) throws IOException {
        debugLog("Initializing ShaderpacksWatcher" + (skipInitialScan ? " (skipping initial scan)" : ""));
        this.utilsInstance = utilsInstance;
        this.shaderpacks = EuphoriaPatcher.shaderpacks;
        this.watchService = FileSystems.getDefault().newWatchService();
        // Add this flag to control initial scanning
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "EuphoriaPatches-FileWatcher");
            thread.setDaemon(true);
            return thread;
        });

        // Register the directory to watch for multiple event types
        debugLog("Registering watch events for directory: " + shaderpacks);
        shaderpacks.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.OVERFLOW
        );

        // Also do an initial scan of existing files just in case
        if (!skipInitialScan) {
            debugLog("Performing initial directory scan");
            initialScan();
        } else {
            debugLog("Skipping initial scan as requested");
        }
    }

    // Helper class to track file metadata for change detection
    private static class FileMetadata {
        final long size;
        final long lastModified;

        FileMetadata(long size, long lastModified) {
            this.size = size;
            this.lastModified = lastModified;
        }

        boolean hasChanged(FileMetadata other) {
            return this.size != other.size || this.lastModified != other.lastModified;
        }
    }

    private void initialScan() {
        debugLog("Starting initial scan of shaderpacks directory");
        int fileCount = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks)) {
            for (Path path : stream) {
                String fileName = path.getFileName().toString();
                debugLog("Checking file: " + fileName);
                if (isPotentialShaderPack(path)) {
                    processedFiles.add(fileName);
                    fileCount++;
                    debugLog("Added to processed files: " + fileName);
                    // Save metadata for this file
                    try {
                        @SuppressWarnings("null")
                        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                        fileMetadata.put(fileName, new FileMetadata(attrs.size(), attrs.lastModifiedTime().toMillis()));
                        debugLog("Saved file metadata for: " + fileName + " (size: " + attrs.size() + ", modified: " + attrs.lastModifiedTime().toMillis() + ")");
                    } catch (IOException e) {
                        debugLog("Error reading file attributes for " + fileName + ": " + e.getMessage());
                        EuphoriaPatcher.log(2, 0, "Error reading file attributes: " + e.getMessage());
                    }
                } else {
                    debugLog("Not a potential shader pack: " + fileName);
                }
            }
            debugLog("Initial scan complete. Found " + fileCount + " potential shader packs");
        } catch (IOException e) {
            debugLog("Error during initial directory scan: " + e.getMessage());
            EuphoriaPatcher.log(2, 0, "Error during initial directory scan: " + e.getMessage());
        }
    }

    public void startWatching() {
        if (isRunning) {
            debugLog("Watcher already running, ignoring start request");
            return;
        }

        debugLog("Starting to watch shaderpacks folder");
        isRunning = true;

        EuphoriaPatcher.log(0, "Watching shaderpacks folder for " + EuphoriaPatcher.BRAND_NAME + "Shaders" + EuphoriaPatcher.VERSION + "...");

        executor.scheduleWithFixedDelay(() -> {
            try {
                debugLog("Polling for file system events");
                WatchKey key = watchService.poll();
                if (key != null) {
                    debugLog("Processing watch events");
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();

                        // Get the file name from the event context
                        @SuppressWarnings("unchecked")
                        WatchEvent<Path> ev = (WatchEvent<Path>) event;
                        Path fileName = ev.context();
                        String fileNameStr = fileName.toString();
                        debugLog("Event: " + kind.name() + " for file: " + fileNameStr);

                        // Handle OVERFLOW by doing a full rescan
                        if (kind == StandardWatchEventKinds.OVERFLOW) {
                            debugLog("OVERFLOW detected, performing full directory rescan");
                            EuphoriaPatcher.log(0, "Detected filesystem overflow, rescanning directory");
                            scanDirectory();
                            continue;
                        }

                        // Handle DELETE events - remove from tracking
                        if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                            debugLog("DELETE event: removing " + fileNameStr + " from tracking");
                            processedFiles.remove(fileNameStr);
                            invalidByteSizeFiles.remove(fileNameStr);
                            fileMetadata.remove(fileNameStr);
                            byteSizeVerificationCache.remove(fileNameStr);
                            continue;
                        }

                        // Handle CREATE or MODIFY events
                        if (kind == StandardWatchEventKinds.ENTRY_CREATE ||
                                kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                            Path fullPath = shaderpacks.resolve(fileName);
                            debugLog((kind == StandardWatchEventKinds.ENTRY_CREATE ? "CREATE" : "MODIFY") +
                                     " event: Processing " + fileNameStr);

                            try {
                                // Give the file system a moment to finish copying the file
                                debugLog("Waiting for file system to settle");
                                Thread.sleep(1000);

                                // Check if we're still supposed to be running
                                if (!isRunning) {
                                    debugLog("Watcher stopped during event processing");
                                    return;
                                }

                                if (!Files.exists(fullPath)) {
                                    debugLog("File no longer exists: " + fileNameStr);
                                    continue;
                                }

                                // Check if this is a newer dev version FIRST before any other logic
                                ShaderDetector detector = getShaderDetector();
                                if (detector != null && detector.isNewerDevVersion(fullPath)) {
                                    debugLog("Dev version detected, processing and shutting down watcher: " + fileNameStr);
                                    EuphoriaPatcher.log(0, "Detected newer dev version: " + fileNameStr);
                                    utilsInstance.processNewShaderpack(fullPath);
                                    // Dev version found - stop watching entirely
                                    return;
                                }

                                if (!isPotentialShaderPack(fullPath)) {
                                    debugLog("Not a potential shader pack: " + fileNameStr);
                                    continue;
                                }

                                // Check if we need to process this file
                                boolean shouldProcess;

                                try {
                                    debugLog("Reading file attributes: " + fileNameStr);
                                    @SuppressWarnings("null")
                                    BasicFileAttributes attrs = Files.readAttributes(fullPath, BasicFileAttributes.class);
                                    FileMetadata newMetadata = new FileMetadata(attrs.size(), attrs.lastModifiedTime().toMillis());
                                    FileMetadata oldMetadata = fileMetadata.get(fileNameStr);

                                    // Process if:
                                    // 1. It's a new file (not in processedFiles)
                                    // 2. It was previously invalid
                                    // 3. Its metadata has changed
                                    boolean isNewFile = !processedFiles.contains(fileNameStr);
                                    boolean isInvalidByteSize = invalidByteSizeFiles.contains(fileNameStr);
                                    boolean hasChanged = oldMetadata != null && oldMetadata.hasChanged(newMetadata);

                                    debugLog("File status - new: " + isNewFile + ", invalid byte size: " +
                                             isInvalidByteSize + ", changed: " + hasChanged);

                                    shouldProcess = isNewFile || isInvalidByteSize || hasChanged;

                                    // Always update the metadata
                                    fileMetadata.put(fileNameStr, newMetadata);
                                    debugLog("Updated file metadata for: " + fileNameStr);

                                    if (shouldProcess) {
                                        if (isNewFile) {
                                            debugLog("Processing new shader pack: " + fileNameStr);
                                            EuphoriaPatcher.log(0, "Detected new shader pack: " + fileNameStr);
                                        } else if (hasChanged) {
                                            debugLog("Processing changed shader pack: " + fileNameStr);
                                            EuphoriaPatcher.log(0, "Detected changed shader pack: " + fileNameStr);
                                        } else {
                                            debugLog("Re-checking previously invalid shader pack: " + fileNameStr);
                                            EuphoriaPatcher.log(0, "Re-checking previously invalid shader pack: " + fileNameStr);
                                        }

                                        debugLog("Starting shader pack processing for: " + fileNameStr);
                                        boolean wasSuccessful = utilsInstance.processNewShaderpack(fullPath);
                                        debugLog("Shader pack processing " + (wasSuccessful ? "successful" : "failed") +
                                                 " for: " + fileNameStr);

                                        // Update tracking sets
                                        if (wasSuccessful) {
                                            processedFiles.add(fileNameStr);
                                            invalidByteSizeFiles.remove(fileNameStr);
                                            debugLog("Added to processed files: " + fileNameStr);
                                        } else {
                                            invalidByteSizeFiles.add(fileNameStr);
                                            debugLog("Added to invalid byte size files: " + fileNameStr);
                                        }
                                    } else {
                                        debugLog("Skipping already processed file: " + fileNameStr);
                                    }
                                } catch (IOException e) {
                                    debugLog("Error reading file attributes: " + e.getMessage());
                                    EuphoriaPatcher.log(2, 0, "Error reading file attributes: " + e.getMessage());
                                }
                            } catch (InterruptedException ie) {
                                debugLog("Thread interrupted, likely during shutdown");
                                // Thread was interrupted, likely during shutdown - no need to log an error
                                Thread.currentThread().interrupt(); // Restore the interrupted status
                                return;
                            } catch (Exception e) {
                                debugLog("Error in shader pack watcher: " + e.getMessage());
                                EuphoriaPatcher.log(3, 0, "Error in shader pack watcher: " + e.getMessage());
                            }
                        }
                    }
                    key.reset();
                    debugLog("Watch key reset, waiting for next events");
                } else {
                    debugLog("No events to process");
                }
            } catch (Exception e) {
                debugLog("Error in shader pack watcher main loop: " + e.getMessage());
                EuphoriaPatcher.log(3, 0, "Error in shader pack watcher: " + e.getMessage());
            }
        }, 0, 2, TimeUnit.SECONDS);

        debugLog("Watcher scheduled and running");
    }

    private void scanDirectory() {
        debugLog("Starting full directory scan");
        int scannedCount = 0;
        int processedCount = 0;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks)) {
            for (Path path : stream) {
                String fileName = path.getFileName().toString();
                scannedCount++;
                debugLog("Scanning file: " + fileName);

                // Check if this is a newer dev version FIRST before any other logic
                ShaderDetector detector = getShaderDetector();
                if (detector != null && detector.isNewerDevVersion(path)) {
                    debugLog("Dev version detected during scan, processing and shutting down watcher: " + fileName);
                    EuphoriaPatcher.log(0, "Detected newer dev version during scan: " + fileName);
                    utilsInstance.processNewShaderpack(path);
                    // Dev version found - stop scanning entirely
                    return;
                }

                if (!isPotentialShaderPack(path)) {
                    debugLog("Not a potential shader pack: " + fileName);
                    continue;
                }

                try {
                    @SuppressWarnings("null")
                    BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                    FileMetadata newMetadata = new FileMetadata(attrs.size(), attrs.lastModifiedTime().toMillis());
                    FileMetadata oldMetadata = fileMetadata.get(fileName);

                    boolean isNewFile = !processedFiles.contains(fileName);
                    boolean isInvalidByteSize = invalidByteSizeFiles.contains(fileName);
                    boolean hasChanged = oldMetadata != null && oldMetadata.hasChanged(newMetadata);

                    debugLog("File status - new: " + isNewFile + ", invalid byte size: " +
                             isInvalidByteSize + ", changed: " + hasChanged);

                    boolean shouldProcess = isNewFile || isInvalidByteSize || hasChanged;

                    // Update metadata regardless
                    fileMetadata.put(fileName, newMetadata);
                    debugLog("Updated file metadata for: " + fileName);

                    if (shouldProcess) {
                        if (isNewFile) {
                            debugLog("Processing new shader pack: " + fileName);
                            EuphoriaPatcher.log(0, "Found new shader pack during scan: " + fileName);
                        } else if (hasChanged) {
                            debugLog("Processing changed shader pack: " + fileName);
                            EuphoriaPatcher.log(0, "Found changed shader pack during scan: " + fileName);
                        } else {
                            debugLog("Re-checking previously invalid shader pack: " + fileName);
                            EuphoriaPatcher.log(0, "Re-checking previously invalid shader pack during scan: " + fileName);
                        }

                        debugLog("Starting shader pack processing for: " + fileName);
                        boolean wasSuccessful = utilsInstance.processNewShaderpack(path);
                        debugLog("Shader pack processing " + (wasSuccessful ? "successful" : "failed") +
                                 " for: " + fileName);
                        processedCount++;

                        // Update tracking based on success
                        if (wasSuccessful) {
                            processedFiles.add(fileName);
                            invalidByteSizeFiles.remove(fileName);
                            debugLog("Added to processed files: " + fileName);
                        } else {
                            invalidByteSizeFiles.add(fileName);
                            debugLog("Added to invalid byte size files: " + fileName);
                        }
                    } else {
                        debugLog("Skipping already processed file: " + fileName);
                    }
                } catch (IOException e) {
                    debugLog("Error reading file attributes during scan for " + fileName + ": " + e.getMessage());
                    EuphoriaPatcher.log(2, 0, "Error reading file attributes during scan: " + e.getMessage());
                }
            }
            debugLog("Directory scan complete. Scanned: " + scannedCount + " files, Processed: " + processedCount + " files");
        } catch (IOException e) {
            debugLog("Error scanning directory: " + e.getMessage());
            EuphoriaPatcher.log(2, 0, "Error scanning directory: " + e.getMessage());
        }
    }

    public void stopWatching() {
        if (!isRunning) {
            debugLog("Watcher not running, ignoring stop request");
            return;
        }

        debugLog("Stopping shader packs watcher");
        isRunning = false;

        EuphoriaPatcher.log(0, "Stopping shaderpacks folder watcher");
        executor.shutdownNow();
        debugLog("Executor service shutdown requested");

        try {
            watchService.close();
            debugLog("Watch service closed");
        } catch (IOException e) {
            debugLog("Error closing watch service: " + e.getMessage());
            EuphoriaPatcher.log(3, "Error closing watch service: " + e.getMessage());
        }
    }

    // Method to clear processed files and force a full rescan
    public void resetProcessedFiles() {
        debugLog("Resetting processed files tracking");
        processedFiles.clear();
        invalidByteSizeFiles.clear();
        fileMetadata.clear();
        EuphoriaPatcher.log(0, "Resetting file watcher to detect replacements");
        debugLog("File tracking reset complete");
    }

    // Method to handle byte size failure case
    public void resetAfterByeSizeFailure() {
        debugLog("Resetting after byte size failure");
        // Only clear processed files and metadata, keep invalid file tracking
        processedFiles.clear();
        fileMetadata.clear();
        debugLog("Processed files and metadata cleared, invalid byte size tracking maintained");

        if (!isRunning) {
            debugLog("Watcher stopped, attempting to restart");
            try {
                startWatching();
                debugLog("Watcher successfully restarted");
            } catch (Exception e) {
                debugLog("Failed to restart watcher: " + e.getMessage());
                EuphoriaPatcher.log(3, 0, "Failed to restart watcher after byte size failure: " + e.getMessage());
            }
        }
    }

    // Track a file that failed byte size verification
    public void trackInvalidByteSizeFile(String fileName) {
        if (fileName != null && !fileName.isEmpty()) {
            debugLog("Tracking invalid byte size file: " + fileName);
            invalidByteSizeFiles.add(fileName);
        } else {
            debugLog("Attempted to track null or empty filename as invalid");
        }
    }

    // Getter for the running state
    public boolean isRunning() {
        return isRunning;
    }

    // Create a static factory method that handles exceptions internally
    public static ShaderpacksWatcher createAndStart(ShaderpacksWatcherUtils utilsInstance) {
        return createAndStart(utilsInstance, false);
    }

    public static ShaderpacksWatcher createAndStart(ShaderpacksWatcherUtils utilsInstance, boolean skipInitialScan) {
        debugLog("Creating and starting new ShaderpacksWatcher" + (skipInitialScan ? " (skipping initial scan)" : ""));
        try {
            ShaderpacksWatcher watcher = new ShaderpacksWatcher(utilsInstance, skipInitialScan);
            watcher.startWatching();
            debugLog("ShaderpacksWatcher successfully created and started");
            return watcher;
        } catch (IOException e) {
            debugLog("Failed to create shaderpacks watcher: " + e.getMessage());
            EuphoriaPatcher.log(3, 0, "Failed to create shaderpacks watcher: " + e.getMessage());
            return null;
        }
    }

    private boolean isPotentialShaderPack(Path path) {
        String fileName = path.getFileName().toString();

        if (fileName.contains(ErrorShaderGenerator.ERROR_SHADER_FOLDER)) {
            debugLog("Skipping error shader from processing: " + fileName);
            return false;
        }

        try {
            debugLog("Evaluating potential shader pack: " + fileName);

            // Check if the name matches what we're looking for
            boolean nameMatches = fileName.matches(EuphoriaPatcher.BRAND_NAME + ".*" + EuphoriaPatcher.VERSION + ".*") &&
                    !fileName.contains(EuphoriaPatcher.PATCH_NAME);

            // Fast path: if name matches, return immediately
            if (nameMatches) {
                debugLog("File name matches shader pack pattern: " + fileName);
                // For zip files
                if (fileName.endsWith(".zip")) {
                    boolean validZip = Files.exists(path) && Files.size(path) > 0;
                    debugLog("ZIP file validation: " + (validZip ? "valid" : "invalid") + " - " + fileName);
                    return validZip;
                }
                // For directories
                boolean isDir = Files.isDirectory(path);
                debugLog("Directory validation: " + (isDir ? "valid" : "invalid") + " - " + fileName);
                return isDir;
            }

            // The byte size check needs the bundled patch, without it no file can match
            if (PatchLibrary.getFailure() != null) {
                debugLog("Bundled patch is unusable, skipping byte size verification: " + fileName);
                return false;
            }

            // If name doesn't match, check if we've already verified this file
            if (byteSizeVerificationCache.containsKey(fileName)) {
                boolean cached = byteSizeVerificationCache.get(fileName);
                debugLog("Using cached verification result for " + fileName + ": " + cached);
                return cached;
            }

            // Throttle byte size verification to avoid excessive CPU usage
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastByteSizeVerificationTime < BYTE_SIZE_VERIFICATION_COOLDOWN) {
                debugLog("Skipping byte size verification due to cooldown: " + fileName);
                return false; // Skip verification during cooldown
            }

            // Update last verification time
            lastByteSizeVerificationTime = currentTime;
            debugLog("Updated verification time, proceeding with byte size check: " + fileName);

            // For files of reasonable size or directories, verify by byte size
            if ((fileName.endsWith(".zip") && Files.exists(path) && Files.size(path) > 100000) ||
                    Files.isDirectory(path)) {

                debugLog("Starting byte size verification for: " + fileName);
                EuphoriaPatcher.log(0, "Checking if file matches by byte size (watcher): " + fileName);

                boolean isValidByByteSize = false;
                ShaderDetector detector = getShaderDetector();
                if (detector != null) {
                    // Use ShaderValidator with progress tracking (this is a single file check)
                    ShaderValidator validator = new ShaderValidator();
                    isValidByByteSize = validator.validateByByteSize(path, 1, 1);
                    debugLog("Byte size verification result: " + (isValidByByteSize ? "valid" : "invalid") + " - " + fileName);

                    // Add result logging
                    if (isValidByByteSize) {
                        EuphoriaPatcher.log(0, "File passed byte size verification: " + fileName);
                    } else {
                        EuphoriaPatcher.log(3, "The " + fileName + " shaderpack which just got added did not pass the byte size verification for " +
                                EuphoriaPatcher.BRAND_NAME + "Shaders" + EuphoriaPatcher.VERSION +
                                ". It may be an incorrect version or modified.");
                        EuphoriaPatcher.log(3, "Please download the correct and official version from " + EuphoriaPatcher.COMP_DOWNLOAD_URL);
                        UserInstallErrorMessages.copyLinkMessage();

                    }

                    // If valid by byte size, rename the file to the correct format
                    if (isValidByByteSize) {
                        debugLog("Found valid shader by byte size, renaming: " + fileName);
                        EuphoriaPatcher.log(0, "Found valid shader by byte size in watcher: " + fileName);

                        // Rename the file
                        ShaderNamingService namingService = getNamingService();
                        Path renamedPath = namingService != null ? namingService.renameToCorrectShaderName(path) : path;
                        String newFileName = renamedPath.getFileName().toString();
                        debugLog("Renamed from " + fileName + " to " + newFileName);

                        // Update the cache with both old and new names
                        byteSizeVerificationCache.put(fileName, true);
                        debugLog("Added to byte size cache: " + fileName);

                        if (!fileName.equals(newFileName)) {
                            byteSizeVerificationCache.put(newFileName, true);
                            debugLog("Added new name to byte size cache: " + newFileName);

                            // Update tracking in case the file was renamed
                            processedFiles.remove(fileName);
                            invalidByteSizeFiles.remove(fileName);
                            fileMetadata.remove(fileName);
                            debugLog("Updated tracking for renamed file");
                        }
                    }
                } else {
                    debugLog("Patcher is null, cannot verify by byte size: " + fileName);
                }

                // Cache the result
                byteSizeVerificationCache.put(fileName, isValidByByteSize);
                debugLog("Cached byte size verification result: " + fileName + " = " + isValidByByteSize);

                return isValidByByteSize;
            } else {
                debugLog("File too small or invalid for byte size verification: " + fileName);
            }

            debugLog("File is not a potential shader pack: " + fileName);
            return false;

        } catch (IOException e) {
            debugLog("Error checking shader pack name: " + fileName + " - " + e.getMessage());
            EuphoriaPatcher.log(2, 0, "Error checking shader pack name: " + e.getMessage());
            return false;
        }
    }
}
