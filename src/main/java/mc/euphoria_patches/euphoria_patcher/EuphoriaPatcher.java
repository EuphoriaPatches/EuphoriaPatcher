package mc.euphoria_patches.euphoria_patcher;

import mc.euphoria_patches.euphoria_patcher.features.*;
import mc.euphoria_patches.euphoria_patcher.util.Config;
import mc.euphoria_patches.euphoria_patcher.util.JsonUtilReader;
import mc.euphoria_patches.euphoria_patcher.util.SodiumConsole;
import mc.euphoria_patches.euphoria_patcher.util.UpdateChecker;

import net.fabricmc.loader.api.FabricLoader;

import io.sigpipe.jbsdiff.InvalidHeaderException;
import io.sigpipe.jbsdiff.ui.FileUI;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class EuphoriaPatcher {
    
    public static final boolean IS_DEV = false; // Manual Boolean. DON'T FORGET TO SET TO FALSE BEFORE COMPILING

    public static final String BRAND_NAME = "Complementary";
    public static final String PATCH_NAME = "EuphoriaPatches";
    public static final String VERSION = "_r5.3";
    public static final String PATCH_VERSION = "_1.4.1";

    private static final String BASE_TAR_HASH = "c85eb822ee37aa3b1764f9490484d47e";
    private static final int BASE_TAR_SIZE = 1300992;

    public static final String DOWNLOAD_URL = "https://www.complementary.dev/";
    public static final String COMMON_LOCATION = "shaders/lib/common.glsl";
    public static final String LANG_LOCATION = "shaders/lang";
    public static final String SHADERS_PROPERTIES_LOCATION = "shaders/shaders.properties";

    // Get necessary paths
    public static Path shaderpacks = FabricLoader.getInstance().getGameDir().resolve("shaderpacks");
    public static Path configDirectory = FabricLoader.getInstance().getConfigDir();
    public static Path resourcesBuildDir = shaderpacks.getParent().getParent().resolve("build");

    // Config Options
    public static boolean doPopUpLogging = true;
    public static boolean doUpdateChecking = true;
    public static boolean doRenameOldShaderFiles = true;

    // Global Variables and Objects
    public static Logger LOGGER = LogManager.getLogger("euphoriaPatches");
    public static boolean isSodiumInstalled = false;
    private static boolean ALREADY_LAUNCHED = false;

    public EuphoriaPatcher() {
        if (ALREADY_LAUNCHED) {
            return;
        }
        ALREADY_LAUNCHED = true;
        configStuff();

        if(doPopUpLogging) isSodiumInstalled();

        if(doUpdateChecking) UpdateChecker.checkForUpdates();

        log(0, JsonUtilReader.getRandomMessage("startupMessages"));

        // Detect installed Complementary Shaders versions
        ShaderInfo shaderInfo = detectInstalledShaders();

        if(!shaderInfo.isAlreadyInstalled) {
            if (shaderInfo.baseFile == null){
                log(3, 8, "You need to have " + BRAND_NAME + "Shaders" + VERSION + " installed!");
                log(3, 8, "Please download it from " + DOWNLOAD_URL + ", place it into your shaderpacks folder and restart Minecraft!");
                if(!IS_DEV) return;
            }
        } else {
            thankYouMessage(shaderInfo.baseFile, shaderInfo.styleUnbound, shaderInfo.styleReimagined);
            return;
        }

        // Create temporary directory
        Path temp = createTempDirectory();
        if (temp == null) return;

        // Process and patch shaders
        if (!processAndPatchShaders(shaderInfo, temp)) return;

        // Update .txt shader config file
        UpdateShaderConfig.updateShaderTxtConfigFile(shaderInfo.styleUnbound, shaderInfo.styleReimagined);

        // Update shader loader (iris) config
        UpdateShaderLoaderConfig.updateShaderLoaderConfig(shaderInfo.styleUnbound, shaderInfo.styleReimagined);

        if(doRenameOldShaderFiles) RenameOutdatedPatches.rename();

        thankYouMessage(shaderInfo.baseFile, shaderInfo.styleUnbound, shaderInfo.styleReimagined);
    }

    private void configStuff(){
        // How to use: Cast to desired data type, then call readWriteConfig, it returns a String.
        // First parameter is the config name, second is the value
        // Third one is the description, it can either be null or a String, supports multi line descriptions with "\n"
        doPopUpLogging = Boolean.parseBoolean(Config.readWriteConfig("doPopUpLogging", "true","Option for the sodium message popup logging." +
                "\nDefault = true"));
        doUpdateChecking = Boolean.parseBoolean(Config.readWriteConfig("doUpdateChecking", "true","Option that enables or disables the update checker, which verifies if a new version of the mod is available." +
                "\nMore info here: https://github.com/EuphoriaPatches/PatcherUpdateChecker" +
                "\nDefault = true"));
        doRenameOldShaderFiles = Boolean.parseBoolean(Config.readWriteConfig("doRenameOldShaderFiles", "true","Option that automatically renames outdated Euphoria Patches folders and config files to a new name." +
                "\nThis makes it easier for users to identify which ones are outdated." +
                "\nDefault = true"));
    }

    private void isSodiumInstalled() {
        String sodiumVersion = "me.jellysquid.mods.sodium.client.gui.console.Console"; // "net.caffeinemc.mods.sodium.client.console.Console" // Newer Sodium versions // Crashes the game if used - import classes are different in SodiumConsole.java
        try {
            Class.forName(sodiumVersion);
            log(0, "Sodium found, using Sodium logging!");
            isSodiumInstalled = true;
        } catch (ClassNotFoundException e) {
            log(0, "Sodium's logging module not found or incompatible version detected. Default logging will be used.: " + e.getMessage());
        }
    }

    // Logging method
    public static void log(int messageLevel, int messageFadeTimer, String message) {
        String loggingMessage = "EuphoriaPatcher: " + message;
        if (messageLevel == -1) loggingMessage = "\n\n" + loggingMessage + "\n";
        if (isSodiumInstalled && messageFadeTimer > 0) {
            SodiumConsole.logMessage(messageLevel, messageFadeTimer, loggingMessage);
        }
        switch (messageLevel) {
            case -1:
            case 0:
            case 1:
                LOGGER.info(loggingMessage);
                break;
            case 2:
                LOGGER.warn(loggingMessage);
                break;
            case 3:
                LOGGER.error(loggingMessage);
                break;
            default:
                System.out.println(loggingMessage);
                break;
        }
    }
    public static void log(int messageLevel, String message) { // Method overloading for optional parameter
        int messageFadeTimer = 0;
        switch (messageLevel) {
            case 1:
                messageFadeTimer = 4;
                break;
            case 2:
                messageFadeTimer = 8;
                break;
            case 3:
                messageFadeTimer = 16;
                break;
        }
        log(messageLevel, messageFadeTimer, message);
    }

    // Detect installed Complementary Shaders versions
    private ShaderInfo detectInstalledShaders() {
        ShaderInfo info = new ShaderInfo();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks, this::isComplementaryShader)) {
            for (Path potentialFile : stream) {
                processShaderFile(potentialFile, info);
                if (info.styleReimagined && info.styleUnbound) break;
            }
            if (!info.styleReimagined && !info.styleUnbound) {
                detectInstalledDirectories(info);
            }
        } catch (IOException e) {
            log(3, "Error reading shaderpacks directory: " + e.getMessage());
        }
        return info;
    }

    // Helper method to check if a file is a Complementary Shader
    private boolean isComplementaryShader(Path path) {
        String name = path.getFileName().toString();
        return name.matches(BRAND_NAME + ".*" + VERSION + ".*") && name.endsWith(".zip") && !name.contains(PATCH_NAME);
    }

    // Process each shader file
    private void processShaderFile(Path file, ShaderInfo info) {
        String name = file.getFileName().toString();
        if (name.contains("Reimagined")) {
            info.styleReimagined = true;
            if (info.baseFile == null) {
                info.baseFile = file;
            }
        } else if (name.contains("Unbound")) {
            info.styleUnbound = true;
            if (info.baseFile == null) {
                info.baseFile = file;
            }
        }
        checkIfAlreadyInstalled(file, info);
    }

    // Check if the patch is already installed
    private void checkIfAlreadyInstalled(Path file, ShaderInfo info) {
        if (info.baseFile != null && Files.exists(file.resolveSibling(file.getFileName().toString().replace(".zip", "") + " + " + PATCH_NAME + PATCH_VERSION)) && !IS_DEV && !info.isAlreadyInstalled) {
            info.isAlreadyInstalled = true;
            log(0, PATCH_NAME + PATCH_VERSION + " is already installed.");
        }
    }

    private void thankYouMessage(Path baseFile, boolean styleUnbound, boolean styleReimagined) {
        if (UpdateChecker.NEW_VERSION_AVAILABLE && doUpdateChecking && baseFile != null) {
            try {
                Path shader = baseFile.resolveSibling(baseFile.getFileName().toString().replace(".zip", "") + " + " + PATCH_NAME + PATCH_VERSION);
                ModifyPatchedShaderpacks.modifyShadersProperties(shader, styleUnbound, styleReimagined,"screen=<empty> <empty>", "screen=info19 info20");
                ModifyPatchedShaderpacks.modifyLangFiles(shader, styleUnbound, styleReimagined,"value\\.info19\\.0=.*", "value.info19.0=§c" + PATCH_VERSION.replace("_", "") + " §r->§a " + UpdateChecker.NEW_MOD_VERSION);
            } catch (IOException e) {
                log(3, 0, "Could not modify the shader to show the user that a new version is available" + e.getMessage());
            }
        }
        if (shaderpacks.toString().contains("SpacEagle")) {
            log(1, "Have fun developing Euphoria Patches!");
        } else {
            log(-1, "Thank you for using Euphoria Patches - SpacEagle17");
        }
    }

    // Detect installed directories
    private void detectInstalledDirectories(ShaderInfo info) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks, this::isComplementaryShaderDirectory)) {
            for (Path potentialFile : stream) {
                processShaderDirectory(potentialFile, info);
                if (info.styleReimagined && info.styleUnbound) break;
            }
        }
    }

    // Helper method to check if a directory is a Complementary Shader
    private boolean isComplementaryShaderDirectory(Path path) {
        return path.getFileName().toString().matches(BRAND_NAME + ".*" + VERSION + ".*") && Files.isDirectory(path);
    }

    // Process each shader directory
    private void processShaderDirectory(Path directory, ShaderInfo info) {
        String name = directory.getFileName().toString();
        if (name.contains(PATCH_NAME)) {
            if(name.contains(PATCH_NAME + PATCH_VERSION) && !info.isAlreadyInstalled) {
                info.isAlreadyInstalled = true;
                log(0, PATCH_NAME +  PATCH_VERSION + " is already installed.");
            }
            return;
        }
        if (name.contains("Reimagined")) {
            info.styleReimagined = true;
            if (info.baseFile == null) {
                info.baseFile = directory;
            }
        } else if (name.contains("Unbound")) {
            info.styleUnbound = true;
            if (info.baseFile == null) {
                info.baseFile = directory;
            }
        }
    }

    // Create temporary directory
    private Path createTempDirectory() {
        try {
            return Files.createTempDirectory("euphoria-patcher-");
        } catch (IOException e) {
            log(3, "Error creating temporary directory: " + e.getMessage());
            return null;
        }
    }

    // Process and patch shaders
    private boolean processAndPatchShaders(ShaderInfo info, Path temp) {
        String baseName = info.baseFile.getFileName().toString().replace(".zip", "");
        String patchedName = baseName + " + " + PATCH_NAME + PATCH_VERSION;

        Path baseExtracted = extractBase(info.baseFile, temp, baseName);
        if (baseExtracted == null) return false;

        if (!updateCommonFile(baseExtracted)) return false;

        Path baseArchived = archiveBase(baseExtracted, temp, baseName);

        if (!verifyBaseArchive(baseArchived)) return false;

        return applyPatch(baseArchived, temp, patchedName, info.styleUnbound, info.styleReimagined);
    }

    // Extract base shader
    private Path extractBase(Path baseFile, Path temp, String baseName) {
        Path baseExtracted = temp.resolve(baseName);
        if (!Files.isDirectory(baseFile)) {
            try {
                ArchiveUtils.extract(baseFile, baseExtracted);
            } catch (IOException | ArchiveException e) {
                log(2, "Error extracting archive: " + e.getMessage());
            }
        } else {
            baseExtracted = baseFile;
        }
        return baseExtracted;
    }

    // Update common file
    private boolean updateCommonFile(Path baseExtracted) {
        try {
            Path commons = baseExtracted.resolve(COMMON_LOCATION);
            String config = FileUtils.readFileToString(commons.toFile(), "UTF-8").replaceFirst("SHADER_STYLE [14]", "SHADER_STYLE 1");
            FileUtils.writeStringToFile(commons.toFile(), config, "UTF-8");
            return true;
        } catch (IOException e) {
            log(3, "Error extracting style information: " + e.getMessage());
            return false;
        }
    }

    // Archive base shader
    private Path archiveBase(Path baseExtracted, Path temp, String baseName) {
        Path baseArchived = temp.resolve(baseName + ".tar");
        try {
            ArchiveUtils.archive(baseExtracted, baseArchived);
        } catch (IOException e) {
            log(2, "Error extracting archive: " + e.getMessage());
            // Handle the error appropriately
        }
        return baseArchived;
    }

    // Verify base archive
    private boolean verifyBaseArchive(Path baseArchived) {
        try {
            if (IS_DEV) {
                String hash = DigestUtils.md5Hex(Files.newInputStream(baseArchived));
                log(0, "Hash of base: " + hash);
                log(0, FileUtils.sizeOf(baseArchived.toFile()) + " bytes");
            } else {
                String hash = DigestUtils.md5Hex(Arrays.copyOf(Files.readAllBytes(baseArchived), BASE_TAR_SIZE));
                if (!hash.equals(BASE_TAR_HASH)) {
                    log(3, 8, "The shader " + BRAND_NAME + "Shaders" + " that was found in your shaderpacks folder can't be used as a base for " + PATCH_NAME);
                    log(3, 8, "Please download " + BRAND_NAME + "Shaders" + VERSION + " from " + DOWNLOAD_URL + ", place it into your shaderpacks folder and restart Minecraft.");
                    if (baseArchived.getFileName().toString().matches(BRAND_NAME + ".*" + VERSION + ".*")) {
                        log(3, 8, "Correct Shader Version Found. BUT it might have been modified. The expected hash does not match.");
                    } else {
                        log(3, 8, "Incorrect Shader Version found or unexpected error. The expected hash does not match.");
                    }
                    return false;
                }
            }
        } catch (IOException e) {
            log(3, "Something went wrong during the hash verification" + e.getMessage());
            return false;
        }
        return true;
    }

    // Apply patch
    private boolean applyPatch(Path baseArchived, Path temp, String patchedName, boolean styleUnbound, boolean styleReimagined) {
        Path patchedArchive = temp.resolve(patchedName + ".tar");
        Path patchedFile = shaderpacks.resolve(patchedName);
        Path patchFile;
        if (IS_DEV){
             // All this code to generate the .patch file in the forge and fabric build dir for less copy-paste action
            Path fabricBuildDir = resourcesBuildDir.resolve("resources/main");
            Path forgeBuildDir = resourcesBuildDir.resolve("sourcesSets/main");
            return devPatchFilePrep(fabricBuildDir, baseArchived, patchedFile, patchedArchive) &&
                    devPatchFilePrep(forgeBuildDir, baseArchived, patchedFile, patchedArchive);
        } else {
            patchFile = temp.resolve(patchedName + ".patch");
            return applyProductionPatch(baseArchived, patchedArchive, patchFile, patchedFile, styleUnbound, styleReimagined);
        }
    }

    private boolean devPatchFilePrep(Path buildDir, Path baseArchived, Path patchedFile, Path patchedArchive){
        checkBuildPath(buildDir);
        Path patchFile = buildDir.resolve(PATCH_NAME + PATCH_VERSION + ".patch");
        return createDevPatch(baseArchived, patchedFile, patchedArchive, patchFile);
    }

    private void checkBuildPath(Path buildDir){
        if (!Files.exists(buildDir)){
            try {
                Files.createDirectories(buildDir);
                log(2,"Build directory created successfully: " + buildDir);
            } catch (IOException e) {
                log(3,"Failed to create directory: " + e.getMessage());
            }
        }
    }

    // Create dev patch
    private boolean createDevPatch(Path baseArchived, Path patchedFile, Path patchedArchive, Path patchFile) {
        try {
            ArchiveUtils.archive(patchedFile, patchedArchive);
            FileUI.diff(baseArchived.toFile(), patchedArchive.toFile(), patchFile.toFile());
            log(0, ".patch file successfully created in " + patchFile + "!");
            return true;
        } catch (CompressorException | IOException | InvalidHeaderException e) {
            log(3, "Error creating dev patch: " + e.getMessage());
            return false;
        }
    }

    // Apply production patch
    private boolean applyProductionPatch(Path baseArchived, Path patchedArchive, Path patchFile, Path patchedFile, boolean styleUnbound, boolean styleReimagined) {
        try (InputStream patchStream = getClass().getClassLoader().getResourceAsStream(PATCH_NAME + PATCH_VERSION + ".patch")) {
            if (patchStream != null) {
                FileUtils.copyInputStreamToFile(Objects.requireNonNull(patchStream), patchFile.toFile());
                FileUI.patch(baseArchived.toFile(), patchedArchive.toFile(), patchFile.toFile());
                try {
                    ArchiveUtils.extract(patchedArchive, patchedFile);
                } catch (IOException | ArchiveException e) {
                    log(2, "Error extracting archive: " + e.getMessage());
                }
                applyStyleSettings(patchedFile, styleUnbound, styleReimagined);
                log(1, PATCH_NAME + " was successfully installed. Enjoy! -SpacEagle17");
                return true;
            }
        } catch (IOException | CompressorException | InvalidHeaderException e) {
            log(3, "Error applying patch file: " + e.getMessage());
        }
        return false;
    }

    // Apply style settings
    private void applyStyleSettings(Path patchedFile, boolean styleUnbound, boolean styleReimagined) throws IOException {
        if (styleUnbound) {
            File commons = new File(patchedFile.toFile(), COMMON_LOCATION);
            String unboundConfig = FileUtils.readFileToString(commons, "UTF-8").replaceFirst("SHADER_STYLE 1", "SHADER_STYLE 4");
            if (!styleReimagined) {
                FileUtils.writeStringToFile(commons, unboundConfig, "UTF-8");
            } else if (patchedFile.getFileName().toString().contains("Reimagined")) {
                File unbound = new File(patchedFile.getParent().toFile(), patchedFile.getFileName().toString().replace("Reimagined", "Unbound"));
                FileUtils.copyDirectory(patchedFile.toFile(), unbound);
                FileUtils.writeStringToFile(new File(unbound, COMMON_LOCATION), unboundConfig, "UTF-8");
            } else {
                File reimagined = new File(patchedFile.getParent().toFile(), patchedFile.getFileName().toString().replace("Unbound", "Reimagined"));
                FileUtils.copyDirectory(patchedFile.toFile(), reimagined);
                FileUtils.writeStringToFile(commons, unboundConfig, "UTF-8");
            }
        }
    }

    // Helper class to store shader information
    private static class ShaderInfo {
        Path baseFile = null;
        boolean styleReimagined = false;
        boolean styleUnbound = false;
        boolean isAlreadyInstalled = false;
    }
}
