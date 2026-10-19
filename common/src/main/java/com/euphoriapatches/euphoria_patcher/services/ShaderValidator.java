package com.euphoriapatches.euphoria_patcher.services;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.shader.ShaderVersionComparator;
import com.euphoriapatches.shaderpatcher.exception.ShaderPatcherException;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for validating shader files through various verification methods
 */
public class ShaderValidator {

    private static Boolean oshiAvailableCache = null;

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[ShaderValidator] " + message);
    }

    /**
     * Checks if OSHI library is available at runtime
     */
    private static boolean isOshiAvailable() {
        if (oshiAvailableCache != null) {
            return oshiAvailableCache;
        }

        try {
            Class<?> systemInfoClass = Class.forName("oshi.SystemInfo");

            // Get detailed information about where OSHI was loaded from
            java.security.ProtectionDomain protectionDomain = systemInfoClass.getProtectionDomain();
            java.security.CodeSource codeSource = protectionDomain.getCodeSource();

            if (codeSource != null) {
                java.net.URL location = codeSource.getLocation();
                debugLog("OSHI library found!");
                debugLog("  Location: " + location);
                debugLog("  Protocol: " + location.getProtocol());
                debugLog("  Path: " + location.getPath());

                // Try to get the actual file path
                try {
                    java.io.File file = new java.io.File(location.toURI());
                    debugLog("  File: " + file.getAbsolutePath());
                    debugLog("  File size: " + file.length() + " bytes");
                    debugLog("  Exists: " + file.exists());
                    debugLog("  Is file: " + file.isFile());
                } catch (Exception e) {
                    debugLog("  Could not resolve to file: " + e.getMessage());
                }
            } else {
                debugLog("OSHI library found, but code source is null (might be loaded from system classloader)");
            }

            // Get ClassLoader information
            ClassLoader classLoader = systemInfoClass.getClassLoader();
            if (classLoader != null) {
                debugLog("  ClassLoader: " + classLoader.getClass().getName());
                debugLog("  ClassLoader toString: " + classLoader);
            } else {
                debugLog("  ClassLoader: Bootstrap ClassLoader (null)");
            }

            // Get package information
            Package pkg = systemInfoClass.getPackage();
            if (pkg != null) {
                debugLog("  Package: " + pkg.getName());
                if (pkg.getImplementationTitle() != null) {
                    debugLog("  Implementation Title: " + pkg.getImplementationTitle());
                }
                if (pkg.getImplementationVersion() != null) {
                    debugLog("  Implementation Version: " + pkg.getImplementationVersion());
                }
                if (pkg.getImplementationVendor() != null) {
                    debugLog("  Implementation Vendor: " + pkg.getImplementationVendor());
                }
            }

            oshiAvailableCache = true;
            return true;
        } catch (ClassNotFoundException e) {
            debugLog("OSHI library not found: " + e.getMessage());
            oshiAvailableCache = false;
            return false;
        }
    }

    /**
     * Determines optimal thread count based on CPU usage using OSHI via reflection
     *
     * @return Thread count between 1-16 based on CPU load, or -1 on failure
     */
    private static int determineOptimalThreadCountWithOshi() {
        try {
            // Use reflection to avoid hard dependency on OSHI classes
            Class<?> systemInfoClass = Class.forName("oshi.SystemInfo");
            Object systemInfo = systemInfoClass.newInstance();

            Object hardware = systemInfoClass.getMethod("getHardware").invoke(systemInfo);
            Object processor = hardware.getClass().getMethod("getProcessor").invoke(hardware);

            // Get CPU load over a 1-second interval
            long[] prevTicks = (long[]) processor.getClass().getMethod("getSystemCpuLoadTicks").invoke(processor);
            Thread.sleep(1000);

            double cpuLoad = (Double) processor.getClass()
                .getMethod("getSystemCpuLoadBetweenTicks", long[].class)
                .invoke(processor, (Object) prevTicks);

            int availableProcessors = getAvailableProcessors();
            debugLog("CPU usage: " + String.format("%.1f%%", cpuLoad * 100) +
                    ", Available processors: " + availableProcessors);

            // Scale thread count based on CPU availability
            // Low usage (< 50%) -> use more threads (up to available processors)
            // Medium usage (50-80%) -> use half of available processors
            // High usage (> 80%) -> use quarter of available processors (minimum 1)
            int threadCount;
            if (cpuLoad < 0.5) {
                threadCount = Math.max(4, availableProcessors);
            } else if (cpuLoad < 0.8) {
                threadCount = Math.max(2, availableProcessors / 2);
            } else {
                threadCount = Math.max(1, availableProcessors / 4);
            }

            // Never exceed available processors
            threadCount = Math.min(threadCount, availableProcessors);

            debugLog("OSHI determined optimal thread count: " + threadCount);
            return threadCount;

        } catch (Exception e) {
            debugLog("Error using OSHI: " + e.getMessage());
            return -1; // Signal failure
        }
    }

    private static int getAvailableProcessors() {
        return Math.max(1, Runtime.getRuntime().availableProcessors());
    }

    /**
     * Determines optimal thread count based on CPU usage
     * Falls back to processor count if OSHI is not available
     *
     * @return Thread count between 1-maxThreadCount, with fallback to 1-4
     */
    private static int determineOptimalThreadCount() {
        // Try OSHI if available
        if (isOshiAvailable()) {
            int threadCount = determineOptimalThreadCountWithOshi();
            if (threadCount > 0) {
                return threadCount;
            }
        }

        // Fallback: simple processor-based calculation
        debugLog("OSHI not available, using fallback thread count calculation");
        int availableProcessors = getAvailableProcessors();
        debugLog("Available processors: " + availableProcessors);
        return Math.min(4, Math.max(1, availableProcessors));
    }

    /**
     * Validates multiple shaders in parallel by extracting, re-archiving, and checking byte size
     * This is a heavy operation used to identify unpatched base shaders
     *
     * @param paths List of paths to validate
     * @param progressCallback Callback for progress updates (filesScanned, totalFiles)
     * @return The first path that passes validation, or null if none pass
     */
    public Path validateByByteSizeParallel(List<Path> paths, ProgressCallback progressCallback) {
        if (paths == null || paths.isEmpty()) {
            return null;
        }

        int totalFiles = paths.size();
        AtomicInteger filesScanned = new AtomicInteger(0);

        // Determine optimal thread count based on CPU usage
        int threadCount = determineOptimalThreadCount();
        EuphoriaPatcher.log(0, "Using " + threadCount + " threads for parallel validation of " + totalFiles + " files");

        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            public Thread newThread(@NotNull Runnable r) {
                Thread t = new Thread(r, String.format("ShaderByteSizeFinder-%03d", threadNumber.getAndIncrement()));
                t.setDaemon(true);
                return t;
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(threadCount, threadFactory);
        CompletionService<Path> completionService = new ExecutorCompletionService<>(executor);

        try {
            // Submit all validation tasks
            for (Path path : paths) {
                completionService.submit(() -> {
                    int currentCount = filesScanned.incrementAndGet();

                    // Report progress every 5 files
                    if (currentCount % 5 == 0 && progressCallback != null) {
                        progressCallback.onProgress(currentCount, totalFiles);
                    }

                    if (validateByByteSize(path, currentCount, totalFiles)) {
                        return path; // Return the valid path
                    }
                    return null;
                });
            }

            // Wait for results and return the first valid one
            for (int i = 0; i < totalFiles; i++) {
                try {
                    Future<Path> future = completionService.poll(30, TimeUnit.SECONDS);
                    if (future != null) {
                        Path result = future.get();
                        if (result != null) {
                            debugLog("Found valid shader: " + result.getFileName());
                            // Cancel remaining tasks since we found a valid shader
                            executor.shutdownNow();
                            return result;
                        }
                    }
                } catch (InterruptedException | ExecutionException e) {
                    debugLog("Error during parallel validation: " + e.getMessage());
                }
            }

            debugLog("No valid shader found after checking all " + totalFiles + " files");
            return null;

        } finally {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    debugLog("Executor did not terminate cleanly");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Checks whether a shader pack has the byte size of the base shader this mod patches.
     * The ShaderPatcher library builds the archive of the pack and compares its size, which is a heavy operation
     * used to identify unpatched base shaders that have been renamed.
     *
     * @param path Path to the shader file or directory
     * @param filesScanned Current count of files scanned (for progress reporting)
     * @param totalFiles Total files to scan (for progress reporting)
     * @return true if the shader matches the expected byte size
     */
    public boolean validateByByteSize(Path path, int filesScanned, int totalFiles) {
        try {
            debugLog("Validating shader by byte size (" + filesScanned + "/" + totalFiles + "): " + path.getFileName());

            // Packs named like a dev/test version are never patched
            String baseName = path.getFileName().toString().replace(".zip", "");
            if (ShaderVersionComparator.isTestOrDevVersion(baseName + ".tar")) {
                debugLog("Test/fix version detected in quiet check: " + baseName);
                return false;
            }

            // Size only: the hash is checked later, when the shader is actually patched
            boolean result = PatchLibrary.get().matchesBase(path, false);
            debugLog("Byte size verification result for " + path.getFileName() + ": " + result);
            return result;
        } catch (ShaderPatcherException e) {
            debugLog("Cannot validate by byte size: " + e.getMessage());
            return false;
        } catch (Exception e) {
            debugLog("Exception during byte size validation: " + e.getMessage());
            return false;
        }
    }

    /**
     * Callback interface for progress updates during parallel validation
     */
    public interface ProgressCallback {
        void onProgress(int filesScanned, int totalFiles);
    }
}
