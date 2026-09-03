package com.euphoriapatches.euphoria_patcher.logging;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.config.ConfigHandler;
import com.euphoriapatches.euphoria_patcher.integration.ShaderLoader;
import com.euphoriapatches.euphoria_patcher.integration.sodium.SodiumConsole;
import com.euphoriapatches.euphoria_patcher.util.mod.ModLoaderSpecifics;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class EuphoriaLogger {
    private static Logger logger;
    private static boolean log4jAvailable = true;

    static {
        try {
            logger = LogManager.getLogger("euphoria_patcher");
        } catch (NoClassDefFoundError | Exception e) {
            // Log4j not available (e.g., running DevPatchGenerator)
            log4jAvailable = false;
            System.out.println("[EuphoriaLogger] Log4j not available, using System.out fallback");
        }
    }

    public static final String ERROR_LOG_FILE_NAME = "_0EUPHORIA_PATCHES_ERROR_LOGS.txt";
    private final Path errorLogFilePath = EuphoriaPatcher.shaderpacks.resolve(ERROR_LOG_FILE_NAME);
    private boolean isSodiumInstalled;
    private boolean shouldCreateErrorLog = true;
    private boolean sodiumHeaderShown = false;

    // For error shader generation
    private final List<String> errorMessages = new ArrayList<>();
    private int lastProcessedErrorCount = 0;
    private boolean hasErrors = false;
    private Timer errorShaderTimer = null;
    private boolean errorShaderScheduled = false;
    private static final int ERROR_COLLECTION_DELAY_MS = 4000; // 4 seconds
    private final Object errorCollectionLock = new Object();

    // URL extraction for clipboard functionality
    private String lastErrorURL = null;
    private final boolean doErrorClipboardCopy;
    private boolean errorURLAlreadyCopied = false;

    private static final int SPAM_PROTECTION_QUEUE_SIZE = 100; // Maximum number of messages to track for spam protection
    private final LogMessageQueue logMessageQueue = new LogMessageQueue(SPAM_PROTECTION_QUEUE_SIZE);

    public EuphoriaLogger() {
        this.isSodiumInstalled = false;

        this.doErrorClipboardCopy = ModLoaderSpecifics.isInstance(ModLoaderSpecifics.FABRIC) ||
                                    ModLoaderSpecifics.isInstance(ModLoaderSpecifics.NEOFORGE) ||
                                    ModLoaderSpecifics.isInstance(ModLoaderSpecifics.FORGE);

    }

    /**
     * Checks if Sodium is available and sets up Sodium logging if it is
     */
    public void checkAndSetupSodiumLogging() {
        isSodiumInstalled = SodiumConsole.isSodiumAvailable();
        if (isSodiumInstalled) {
            debugLog("Sodium found, using Sodium logging!");
        }
    }

    /**
     * Main logging method with custom fade timer
     */
    public void log(int messageLevel, int messageFadeTimer, String message) {
        if (shouldSuppressMessage(message)) {
            return;
        }

        String loggingMessage = "EuphoriaPatcher: " + message;
        if (messageLevel == -1) loggingMessage = "\n \n" + loggingMessage + "\n\n ";

        if (isSodiumInstalled && messageFadeTimer > 0) {
            // Only the first message carries the header, looks cleaner
            String sodiumMessage = sodiumHeaderShown ? message : "EuphoriaPatcher: " + message;
            if (messageLevel == -1) sodiumMessage = "\n \n" + sodiumMessage + "\n\n ";
            SodiumConsole.logMessage(messageLevel, messageFadeTimer, sodiumMessage);
            sodiumHeaderShown = true;
        }

        switch (messageLevel) {
            case 0:
            case 1:
                if (log4jAvailable && logger != null) {
                    logger.info(loggingMessage);
                } else {
                    System.out.println("[INFO] " + loggingMessage);
                }
                break;
            case 2:
                if (log4jAvailable && logger != null) {
                    logger.warn(loggingMessage);
                } else {
                    System.out.println("[WARN] " + loggingMessage);
                }
                if (messageFadeTimer > 0) {
                    appendToErrorLogFile("[WARNING] " + loggingMessage);
                    collectErrorMessage(loggingMessage);
                }
                break;
            case 3:
                if (log4jAvailable && logger != null) {
                    logger.error(loggingMessage);
                } else {
                    System.err.println("[ERROR] " + loggingMessage);
                }
                if (messageFadeTimer > 0) {
                    appendToErrorLogFile("[ERROR] " + loggingMessage);
                    collectErrorMessage(loggingMessage);
                    if (doErrorClipboardCopy) {
                        String url = extractURL(message); // Extract URL if present
                        if (url != null) {
                            synchronized (errorCollectionLock) {
                                errorURLAlreadyCopied = false; // Reset to allow new copy
                                lastErrorURL = url;
                                debugLog("Stored error URL for clipboard: " + lastErrorURL);
                            }
                        }
                    }
                }
                break;
            default:
                System.out.println(loggingMessage);
                break;
        }

        if (message.contains("Have fun developing Euphoria Patches!") || message.contains("Thank you for using Euphoria Patches - SpacEagle17")) {
            deleteErrorLogFile();
        }
    }

    /**
     * Simplified logging method that uses default fade timer
     */
    public void log(int messageLevel, String message) {
        // Map message levels to standard fade times
        int[] fadeTimers = {0, 4, 8, 16}; // Default, Info, Warning, Error
        int messageFadeTimer = messageLevel >= 0 && messageLevel < fadeTimers.length ?
                fadeTimers[messageLevel] : 0;
        log(messageLevel, messageFadeTimer, message);
    }

    /**
     * Checks if a message should be suppressed based on recent duplicates within the defined time window
     */
    private boolean shouldSuppressMessage(String message) {
        LogMessage logMessage = new LogMessage(message);
        int count = logMessageQueue.getOccurrenceCount(logMessage);

        if (count == -1) {
            logMessageQueue.add(logMessage);
            return false;
        }

        return count > 3;
    }

    /**
     * Collects error messages for the error shader with intelligent batching
     */
    private void collectErrorMessage(String message) {
        synchronized (errorCollectionLock) {
            errorMessages.add(message);
            hasErrors = true;

            // Debug to check message collection
            debugLog("Collected error #" + errorMessages.size() + ": " + message);
            debugLog("lastProcessedErrorCount = " + lastProcessedErrorCount);

            // Schedule error shader generation if this is the first error
            if (errorMessages.size() == 1) {
                debugLog("First error, scheduling initial generation");
                scheduleErrorShaderGeneration();
            } else if (errorMessages.size() >= 10 && errorMessages.size() - lastProcessedErrorCount >= 5) {
                // If we have 10+ errors with 5+ new ones, don't wait - generate immediately
                debugLog("Many errors collected, generating immediately");
                cancelScheduledErrorShader();
                ErrorShaderGenerator.generateErrorShader(errorMessages);
                lastProcessedErrorCount = errorMessages.size();
                // Reschedule to catch any new errors
                scheduleErrorShaderGeneration();
            } else if (!errorShaderScheduled && errorMessages.size() > lastProcessedErrorCount) {
                // CRITICAL FIX: If we have new errors but no timer is scheduled, schedule one
                debugLog("New errors detected and no timer active, scheduling update");
                scheduleErrorShaderGeneration();
            }
        }
    }

    /**
     * Schedules the error shader generation after a delay to collect multiple errors
     */
    private void scheduleErrorShaderGeneration() {
        synchronized (errorCollectionLock) {
            if (errorShaderScheduled) {
                debugLog("Timer already scheduled, skipping");
                return; // Already scheduled
            }

            // Cancel any existing timer
            cancelScheduledErrorShader();

            // Create new timer
            errorShaderTimer = new Timer("ErrorShaderTimer", true); // true = daemon thread
            errorShaderTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    synchronized (errorCollectionLock) {
                        debugLog("Timer firing: messages=" + errorMessages.size() +
                                 ", lastProcessed=" + lastProcessedErrorCount);

                        // Generate shader if we have new errors
                        if (hasErrors && errorMessages.size() > lastProcessedErrorCount) {
                            debugLog("Generating shader with " + errorMessages.size() + " messages");
                            ErrorShaderGenerator.generateErrorShader(errorMessages);
                            lastProcessedErrorCount = errorMessages.size();
                        }

                        // Only attempt clipboard copy on supported mod loaders
                        if (doErrorClipboardCopy) {
                            // Check if error shader is active and copy URL to clipboard if available
                            if (ErrorShaderGenerator.isErrorShaderActive() &&
                            lastErrorURL != null &&
                            !errorURLAlreadyCopied &&
                            ShaderLoader.isShaderLoaderRunning()) {
                                debugLog("Error shader is active, attempting to copy URL to clipboard: " + lastErrorURL);
                                boolean success = ModLoaderSpecifics.setClipboardStatic(lastErrorURL);
                                if (success) {
                                    debugLog("Successfully copied URL to clipboard");
                                    log(3, 8, "The download link has been copied to your clipboard. Paste it in your browser.");
                                } else {
                                    debugLog("Failed to copy URL to clipboard");
                                    log(3, 8, "Failed to copy error URL to clipboard. " +
                                        "Please copy it manually from " + ERROR_LOG_FILE_NAME + " in your shaderpacks folder.");

                                }
                                ErrorShaderGenerator.generateErrorShader(errorMessages);
                                lastProcessedErrorCount = errorMessages.size();
                                errorURLAlreadyCopied = true;
                            } else {
                                debugLog("Error shader not active or no URL to copy (active=" +
                                        ErrorShaderGenerator.isErrorShaderActive() + ", url=" + lastErrorURL + ", alreadyCopied=" + errorURLAlreadyCopied + ")");
                            }
                        }

                        // Always reschedule to keep checking periodically
                        errorShaderScheduled = false;
                        scheduleErrorShaderGeneration();
                    }
                }
            }, ERROR_COLLECTION_DELAY_MS);

            errorShaderScheduled = true;
            debugLog("Scheduled error shader generation in " + (ERROR_COLLECTION_DELAY_MS / 1000) + " seconds");
        }
    }

    /**
     * Cancels any scheduled error shader generation
     */
    private void cancelScheduledErrorShader() {
        if (errorShaderTimer != null) {
            errorShaderTimer.cancel();
            errorShaderTimer = null;
            errorShaderScheduled = false;
        }
    }

    /**
     * Checks if the error log file exists and adds a restart separator
     */
    public void checkErrorLogFileAndAddSeparator() {
        if (!shouldCreateErrorLog) return;
        try {
            if (Files.exists(errorLogFilePath)) {
                String separator = "\n--------------------------------------\n" +
                                    "Restart happened\n" +
                                    "--------------------------------------\n";
                Files.write(errorLogFilePath, separator.getBytes(),
                            java.nio.file.StandardOpenOption.APPEND);
                log(0, "Added restart separator to error log file");
            }
        } catch (IOException e) {
            log(0,"Failed to add restart separator to error log file: " + e.getMessage());
        }
    }

    /**
     * Appends a message to the error log file with timestamp
     */
    private void appendToErrorLogFile(String message) {
        if (!shouldCreateErrorLog) return;
        try {
            // Create parent directories if they don't exist
            if (!Files.exists(errorLogFilePath.getParent())) {
                Files.createDirectories(errorLogFilePath.getParent());
            }

            // Format with timestamp
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            String logEntry = timestamp + " " + message + System.lineSeparator();

            // Create or append to file
            java.nio.file.StandardOpenOption option = Files.exists(errorLogFilePath)
                ? java.nio.file.StandardOpenOption.APPEND
                : java.nio.file.StandardOpenOption.CREATE;

            Files.write(errorLogFilePath, logEntry.getBytes(), option);
        } catch (IOException e) {
            log(0,"Failed to write to error log file: " + e.getMessage());
        }
    }

    /**
     * Deletes the error log file when shader has been successfully installed
     */
    private void deleteErrorLogFile() {
        synchronized (errorCollectionLock) {
            try {
                if (Files.exists(errorLogFilePath)) {
                    Files.delete(errorLogFilePath);
                    log(0,"Deleted error log file as shader was successfully installed");
                    shouldCreateErrorLog = false;
                }

                // Also delete error shader if it exists
                Path errorShaderPath = EuphoriaPatcher.shaderpacks.resolve(ErrorShaderGenerator.ERROR_SHADER_FOLDER);
                if (Files.exists(errorShaderPath)) {
                    FileUtils.deleteDirectory(errorShaderPath.toFile());
                    log(0, "Deleted error shader as installation was successful");
                }

                // Clear error messages and cancel any pending timer
                errorMessages.clear();
                hasErrors = false;
                lastProcessedErrorCount = 0;
                cancelScheduledErrorShader();
            } catch (IOException e) {
                log(0,"Failed to delete error log file: " + e.getMessage());
            }
        }
    }

    public static void debugLog(String message) {
        if (ConfigHandler.doDebugLogging) {
            EuphoriaPatcher.log(0, message);
        }
    }

    public static String getStackTrace(Exception e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.getClass().getName()).append(": ").append(e.getMessage()).append("\n");
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append("  at ").append(element.toString()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Extracts a URL from an error message if present
     * @param message The message to extract URL from
     * @return The extracted URL or null if no URL found
     */
    @SuppressWarnings("HttpUrlsUsage")
    private String extractURL(String message) {
        // Simple URL pattern matching for http:// and https://
        String[] words = message.split("\\s+");
        for (String word : words) {
            if (word.startsWith("http://") || word.startsWith("https://")) {
                // Clean up any trailing punctuation
                String url = word.replaceAll("[,;:.)!]+$", "");
                debugLog("Extracted URL from error message: " + url);
                return url;
            }
        }
        return null;
    }
}
