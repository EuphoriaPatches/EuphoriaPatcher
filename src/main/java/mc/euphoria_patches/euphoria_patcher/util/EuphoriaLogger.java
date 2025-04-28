package mc.euphoria_patches.euphoria_patcher.util;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;



public class EuphoriaLogger {
    public static Logger logger = LogManager.getLogger("euphoriaPatches");
    private static final String ERROR_LOG_FILE_NAME = "1_EUPHORIA_PATCHES_ERROR_LOGS.txt";
    private final Path errorLogFilePath = EuphoriaPatcher.shaderpacks.resolve(ERROR_LOG_FILE_NAME);;
    private boolean isSodiumInstalled;
    private boolean shouldCreateErrorLog = true;


    public EuphoriaLogger() {
        this.isSodiumInstalled = false;
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
        String loggingMessage = "EuphoriaPatcher: " + message;
        if (messageLevel == -1) loggingMessage = "\n\n" + loggingMessage + "\n";
        
        if (isSodiumInstalled && messageFadeTimer > 0) {
            SodiumConsole.logMessage(messageLevel, messageFadeTimer, loggingMessage);
        }
        
        switch (messageLevel) {
            case -1:
            case 0:
            case 1:
                logger.info(loggingMessage);
                break;
            case 2:
                logger.warn(loggingMessage);
                if(messageFadeTimer > 0) appendToErrorLogFile("[WARNING] " + loggingMessage);
                break;
            case 3:
                logger.error(loggingMessage);
                if(messageFadeTimer > 0) appendToErrorLogFile("[ERROR] " + loggingMessage);
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
        try {
            if (Files.exists(errorLogFilePath)) {
                Files.delete(errorLogFilePath);
                log(0,"Deleted error log file as shader was successfully installed");
                shouldCreateErrorLog = false;
            }
        } catch (IOException e) {
            log(0,"Failed to delete error log file: " + e.getMessage());
        }
    }

    public static void debugLog(String message) {
        if (EuphoriaPatcher.doDebugLogging) {
            EuphoriaPatcher.log(0, message);
        }
    }
}