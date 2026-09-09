package com.euphoriapatches.euphoria_patcher;

import com.euphoriapatches.euphoria_patcher.config.Config;
import com.euphoriapatches.euphoria_patcher.config.ConfigHandler;
import com.euphoriapatches.euphoria_patcher.features.*;
import com.euphoriapatches.euphoria_patcher.features.properties.PropertiesWatcher;
import com.euphoriapatches.euphoria_patcher.features.shader_settings.UpdateShaderConfig;
import com.euphoriapatches.euphoria_patcher.features.shader_settings.UpdateShaderLoaderConfig;
import com.euphoriapatches.euphoria_patcher.integration.ShaderLoader;
import com.euphoriapatches.euphoria_patcher.io.ArchiveOperations;
import com.euphoriapatches.euphoria_patcher.io.JsonUtilReader;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.monitoring.PotatoFileMonitor;
import com.euphoriapatches.euphoria_patcher.monitoring.ShaderpacksWatcherUtils;
import com.euphoriapatches.euphoria_patcher.services.*;
import com.euphoriapatches.euphoria_patcher.services.ShaderDetector.ShaderInfo;
import com.euphoriapatches.euphoria_patcher.util.*;
import com.euphoriapatches.euphoria_patcher.util.mod.ModLoaderSpecifics;
import com.euphoriapatches.euphoria_patcher.util.shader.BootShaderModificator;
import com.euphoriapatches.euphoria_patcher.util.shader.ShaderVersionComparator;

import java.nio.file.Path;

public class EuphoriaPatcher {
    public static final String BRAND_NAME = "Complementary";
    public static final String PATCH_NAME = "EuphoriaPatches";
    public static final String VERSION = PatchInfo.VERSION;
    public static final String PATCH_VERSION = PatchInfo.PATCH_VERSION;

    public static final String COMP_DOWNLOAD_URL = "https://www.complementary.dev/manual-download/";
    public static final String EP_DOWNLOAD_URL = "https://euphoriapatches.com/download";
    public static final String COMMON_LOCATION = "shaders/lib/common.glsl";
    public static final String LANG_LOCATION = "shaders/lang";
    public static final String SHADERS_PROPERTIES_LOCATION = "shaders/shaders.properties";
    public static final String SHADER_MYFILE_LOCATION = "shaders/lib/misc/myFile.glsl";

    // Get necessary paths
    public static Path shaderpacks = ModLoaderSpecifics.shaderpacks();
    public static Path configDirectory = ModLoaderSpecifics.configDirectory();

    // Global Variables and Objects
    private static boolean ALREADY_LAUNCHED = false;
    private static EuphoriaPatcher instance;
    private static EuphoriaLogger loggerInstance;

    // Service classes
    private ShaderDetector shaderDetector;
    private ShaderPatchingService patchingService;
    private ShaderVersionComparator versionComparator;
    private ShaderNamingService namingService;

    public EuphoriaPatcher() {
        if (ALREADY_LAUNCHED) {
            return;
        }
        ALREADY_LAUNCHED = true;
        instance = this;
        System.out.println("\nEuphoria Patcher:");

        // Initialize the logger
        loggerInstance = new EuphoriaLogger();
        loggerInstance.checkErrorLogFileAndAddSeparator();
        JarLauncher.touch(); // Call to prevent minimization from removing the GUI code.

        ConfigHandler.configStuff();

        UserPersistentData.validateShaderDataHash();
        if (UserPersistentData.isIncorrectVersion()) UserPersistentData.resetShaderStyles();

        if (ModFolderVersionChecker.existsNewerModInFolder()) return;

        if (ConfigHandler.doPopUpLogging) loggerInstance.checkAndSetupSodiumLogging();

        UpdateChecker.checkForUpdates();

        ShaderLoader.getShaderLoader();

        log(0, JsonUtilReader.getRandomMessage("startupMessages"));

        UpdateShaderConfig.markAllEPSettingsFiles();

        // Initialize services
        initializeServices();
        new ShaderpacksWatcherUtils();

        // Detect installed Complementary Shaders versions
        ShaderInfo shaderInfo = shaderDetector.detectInstalledShaders(namingService);

        if (!shaderInfo.isAlreadyInstalled) {
            if (shaderInfo.baseFile == null) {
                UserInstallErrorMessages.handleShaderNotFound(versionComparator);
                return;
            }
        } else {
            // Load shader style data from persistent storage if styles weren't detected
            if (!shaderInfo.styleReimagined && !shaderInfo.styleUnbound && UserPersistentData.dataFileExists()) {
                UserPersistentData.PersistentShaderData data = UserPersistentData.load();
                if (data.styleReimagined == null || data.styleUnbound == null) {
                    debugLog("Could not load data.json, cannot check for missing styles");
                } else {
                    shaderInfo.styleReimagined = data.styleReimagined;
                    shaderInfo.styleUnbound = data.styleUnbound;
                }
            }
            thankYouMessage(shaderInfo.baseFile, shaderInfo.styleUnbound, shaderInfo.styleReimagined, shaderInfo.installedDir, true);
            return;
        }

        // Create temporary directory
        Path temp = ArchiveOperations.createTempDirectory();
        if (temp == null || shaderInfo.baseFile == null) return;

        completeShaderPatching(shaderInfo, temp);
    }

    /**
     * Initialize all service classes
     */
    private void initializeServices() {
        // Initialize version comparator
        versionComparator = new ShaderVersionComparator(BRAND_NAME, PATCH_NAME, VERSION, shaderpacks);

        // Initialize detector (without naming service initially)
        shaderDetector = new ShaderDetector(BRAND_NAME, PATCH_NAME, VERSION, PATCH_VERSION,
                                           COMMON_LOCATION, SHADER_MYFILE_LOCATION, shaderpacks);

        // Initialize naming service (needs detector for some operations)
        namingService = new ShaderNamingService(BRAND_NAME, PATCH_NAME, VERSION, PATCH_VERSION,
                                               COMMON_LOCATION, SHADER_MYFILE_LOCATION, shaderpacks, shaderDetector);

        // Initialize patching service
        patchingService = new ShaderPatchingService(PATCH_NAME, PATCH_VERSION, COMMON_LOCATION,
                                                   shaderpacks, namingService);
    }

    public static EuphoriaPatcher getInstance() {
        return instance;
    }

    public ShaderVersionComparator getVersionComparator() {
        return versionComparator;
    }

    public ShaderDetector getShaderDetector() {
        return shaderDetector;
    }

    public ShaderNamingService getNamingService() {
        return namingService;
    }

    public boolean completeShaderPatching(ShaderInfo shaderInfo, Path temp) {
        // Process and patch shaders
        if (!patchingService.processAndPatchShaders(shaderInfo, temp)) return false;

        // Update .txt shader config file
        UpdateShaderConfig.updateShaderTxtConfigFile(shaderInfo.styleUnbound, shaderInfo.styleReimagined);

        // Update shader loader config
        UpdateShaderLoaderConfig.updateShaderLoaderConfig(shaderInfo.styleUnbound, shaderInfo.styleReimagined);

        if (ConfigHandler.doDeleteOldShaderFiles) ModifyOutdatedPatches.delete();
        if (ConfigHandler.doRenameOldShaderFiles) ModifyOutdatedPatches.rename();

        UserPersistentData.saveShaderStyles(shaderInfo.styleReimagined, shaderInfo.styleUnbound);

        thankYouMessage(shaderInfo.baseFile, shaderInfo.styleUnbound, shaderInfo.styleReimagined, shaderInfo.installedDir, shaderInfo.isAlreadyInstalled);
        return true;
    }

    public static void log(int messageLevel, int messageFadeTimer, String message) {
        if (loggerInstance == null) {
            System.out.println("EuphoriaPatcher (early log): " + message);
            return;
        }
        loggerInstance.log(messageLevel, messageFadeTimer, message);
    }

    public static void log(int messageLevel, String message) {
        if (loggerInstance == null) {
            System.out.println("EuphoriaPatcher (early log): " + message);
            return;
        }
        loggerInstance.log(messageLevel, message);
    }

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[EuphoriaPatcher] " + message);
    }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                UpdateShaderConfig.shutdownFileWriter();
                Config.stopConfigWatcher();
                ShaderpacksWatcherUtils.getInstance().getShaderpacksWatcher().stopWatching();
                PotatoFileMonitor.stopMonitoring();
                PropertiesWatcher.stopMonitoring();
            } catch (Throwable ignored) {
            }
        }));
    }

    public void thankYouMessage(Path baseFile, boolean styleUnbound, boolean styleReimagined, Path installedDir, boolean isAlreadyInstalled) {
        Path shader = shaderDetector.findInstalledShaderPath(baseFile, installedDir);

        if (shader != null) {
            BootShaderModificator.applyShaderModifications(shader, styleUnbound, styleReimagined, isAlreadyInstalled);
            displayFinalMessage();
        } else {
            debugLog("No valid shader path found for thank you message");
            log(-1, "Thank you for using Euphoria Patches - SpacEagle17");
        }
    }

    private void displayFinalMessage() {
        if (SpacEagle17.check()) {
            log(1, "Have fun developing Euphoria Patches!\n\n ");
        } else {
            log(-1, "Thank you for using Euphoria Patches - SpacEagle17");
        }
    }
}
