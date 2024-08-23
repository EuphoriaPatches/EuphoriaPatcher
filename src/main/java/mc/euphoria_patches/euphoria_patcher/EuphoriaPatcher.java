package mc.euphoria_patches.euphoria_patcher;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;

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
import java.util.Arrays;
import java.util.Objects;

@Mod("euphoria_patcher")
public class EuphoriaPatcher {
    
    private static final boolean IS_DEV = false; // Manual Boolean. DON'T FORGET TO SET TO FALSE BEFORE COMPILING

    // Config Options
    public static boolean doSodiumLogging;

    // Get necessary paths
    public static Path shaderpacks = FMLPaths.GAMEDIR.get().resolve("shaderpacks");
    public static Path configDirectory = FMLPaths.CONFIGDIR.get();
    public static Path resourcesBuildDir = shaderpacks.getParent().getParent().resolve("build");

    private static final String DOWNLOAD_URL = "https://www.complementary.dev/";
    private static final String COMMON_LOCATION = "shaders/lib/common.glsl";

    private static final String BRAND_NAME = "ComplementaryShaders";
    private static final String PATCH_NAME = "EuphoriaPatches";
    private static final String VERSION = "_r5.2.2";
    private static final String PATCH_VERSION = "_1.3.2";

    private static final String BASE_TAR_HASH = "46a2fb63646e22cea56b2f8fa5815ac2";
    private static final int BASE_TAR_SIZE = 1274880;

    public static Logger LOGGER = LogManager.getLogger("euphoriaPatches");

    public EuphoriaPatcher() {
        if(FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            log(3,"The Euphoria Patcher Mod should not be loaded on a server! Disabling...");
            return;
        }
        configStuff();

        // Detect installed Complementary Shaders versions
        ShaderInfo shaderInfo = detectInstalledShaders();

        if(!shaderInfo.isAlreadyInstalled) {
            if (shaderInfo.baseFile == null){
                log(2, "You need to have " + BRAND_NAME + VERSION + " installed. Please download it from " + DOWNLOAD_URL + ", place it into your shaderpacks folder and restart Minecraft.");
                return;
            }
        } else {
            thankYouMessage();
            return;
        }

        // Create temporary directory
        Path temp = createTempDirectory();
        if (temp == null) return;

        // Process and patch shaders
        if (!processAndPatchShaders(shaderInfo, temp)) return;

        // Update .txt shader config file
        updateShaderTxtConfigFile(shaderInfo.styleUnbound, shaderInfo.styleReimagined);

        // Update shader loader (iris) config
        updateShaderLoaderConfig(shaderInfo.styleUnbound, shaderInfo.styleReimagined);

        thankYouMessage();
    }

    private Path getShaderLoaderPath(Path configDirectory){
        Path shaderLoaderConfig = configDirectory.resolve("iris.properties");
        if(!Files.exists(shaderLoaderConfig)) shaderLoaderConfig = configDirectory.resolve("oculus.properties");
        if (!Files.exists(shaderLoaderConfig)) shaderLoaderConfig = null;
        return shaderLoaderConfig;
    }

    private void configStuff(){
        doSodiumLogging = Boolean.parseBoolean(Config.readWriteConfig("doSodiumLogging", "true"));
    }

    private static boolean isSodiumInstalled() {
        String sodiumVersion = "me.jellysquid.mods.sodium.client.gui.console.Console"; // "net.caffeinemc.mods.sodium.client.console.Console" // Newer Sodium versions // Crashes the game if used - import classes are different in SodiumConsole.java
        try {
            Class.forName(sodiumVersion);
            log(0, "Sodium found, using Sodium logging!");
            return true;
        } catch (ClassNotFoundException e) {
            log(0, "Sodium not found, using default logging: " + e.getMessage());
            return false;
        }
    }

    // Logging method
    public static void log(int messageLevel, String message) {
        String loggingMessage = "EuphoriaPatcher: " + message;
        if (doSodiumLogging) {
            SodiumConsole.logMessage(messageLevel, loggingMessage);
        }
        switch (messageLevel) {
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
        return name.matches("Complementary.+?(?=" + VERSION + ")" + VERSION + "(?!\\.[0-9]).*") && name.endsWith(".zip") && !name.contains(PATCH_NAME);
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
            info.baseFile = null;
            info.isAlreadyInstalled = true;
            log(0, PATCH_NAME + PATCH_VERSION + " is already installed.");
        }
    }

    private void thankYouMessage(){
        log(0,"Thank you for using Euphoria Patches - SpacEagle17");
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
        return path.getFileName().toString().matches("Complementary.+?(?=" + VERSION + ")" + VERSION + "(?!\\.[0-9]).*") && Files.isDirectory(path);
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
                    log(2, "The version of " + BRAND_NAME + " that was found in your shaderpacks folder can't be used as a base for " + PATCH_NAME +
                            ". Please download " + BRAND_NAME + VERSION + " from " + DOWNLOAD_URL + ", place it into your shaderpacks folder and restart Minecraft.");
                    return false;
                }
            }
        } catch (IOException e) {
            log(2,"The version of " + BRAND_NAME + " that was found in your shaderpacks folder can't be used as a base for " + PATCH_NAME +
                    ". Please download " + BRAND_NAME + VERSION + " from " + DOWNLOAD_URL + ", place it into your shaderpacks folder and restart Minecraft. And something more went wrong" + e.getMessage());
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
            log(0, ".patch file successfully created!");
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

    // Update config file
    private void updateShaderTxtConfigFile(boolean styleUnbound, boolean styleReimagined) {
        try (DirectoryStream<Path> oldConfigTextStream = Files.newDirectoryStream(shaderpacks, this::isConfigFile)) {
            Path oldShaderConfigFilePath = findShaderConfigFile(oldConfigTextStream, true);
            if (oldShaderConfigFilePath != null) {
                String style = styleUnbound ? "Unbound" : "Reimagined";
                String newName = "Complementary" + style + VERSION + " + " + PATCH_NAME + PATCH_VERSION + ".txt";
                try {
                    Files.copy(oldShaderConfigFilePath, oldShaderConfigFilePath.resolveSibling(newName)); // Copy old config and rename it to current PATCH_VERSION
                    log(0, "Successfully updated shader config file to the latest version!");
                } catch (IOException e) {
                    log(3, "Could not rename the config file: " + e.getMessage());
                }
                if (styleUnbound && styleReimagined) { // Yeah, this makes things unnecessarily complex lol
                    log(0, "Both shader styles detected!");
                    try (DirectoryStream<Path> latestConfigTextStream = Files.newDirectoryStream(shaderpacks, this::isConfigFile)) { // Create a new DirectoryStream - The iterator of Files.newDirectoryStream can only be used once
                        Path latestShaderConfigFilePath = findShaderConfigFile(latestConfigTextStream, false);
                        if (latestShaderConfigFilePath != null) {
                            style = latestShaderConfigFilePath.toString().contains("Unbound") ? "Reimagined" : "Unbound"; // Detect what the previously renamed (oldShaderConfigFilePath) .txt contains
                            newName = "Complementary" + style + VERSION + " + " + PATCH_NAME + PATCH_VERSION + ".txt";
                            try { // Now copy and past the renamed .txt file with a new name - 2 identical.txt files with different style names are now in the shaderpacks folder
                                Files.copy(latestShaderConfigFilePath, latestShaderConfigFilePath.resolveSibling(newName));
                                log(0, "Successfully copied shader config file and renamed it!");
                            } catch (IOException e) {
                                log(3, "Could not copy and rename the config file: " + e.getMessage());
                            }
                        }
                    } catch (IOException e) {
                        log(3, "Error reading shaderpacks directory: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            log(3, "Error reading shaderpacks directory: " + e.getMessage());
        }
    }

    // Helper method to check if a file is a config file
    private boolean isConfigFile(Path path) {
        String nameText = path.getFileName().toString();
        return nameText.matches("Complementary.*") && nameText.endsWith(".txt") && nameText.contains(PATCH_NAME);
    }

    // Find the latest config file
    private Path findShaderConfigFile(DirectoryStream<Path> textStream, boolean searchOldConfigs) {
        Path latestRequestedConfig = null;
        for (Path potentialTextFile : textStream) {
            String name = potentialTextFile.getFileName().toString();
            if (name.endsWith(".txt")) {
                latestRequestedConfig = potentialTextFile;
                if (name.contains(PATCH_VERSION)) {
                    return searchOldConfigs ? null : latestRequestedConfig; // by default return null if contains PATCH_VERSION only return with PATCH_VERSION if Reimagined AND Unbound both installed
                }
            }
        }
        return latestRequestedConfig;
    }

    // Update shader loader (iris) config
    private void updateShaderLoaderConfig(boolean styleUnbound, boolean styleReimagined) {
        Path shaderLoaderConfig = getShaderLoaderPath(configDirectory);
        if (shaderLoaderConfig == null) {
            log(0, "No shader loader config found");
            return;
        }

        String shaderLoaderName = shaderLoaderConfig.toString().contains("iris") ? "iris" : "oculus";

        File fileToBeModified = shaderLoaderConfig.toFile();
        StringBuilder oldContent = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new FileReader(fileToBeModified))) {
            String line;
            while ((line = reader.readLine()) != null) {
                oldContent.append(line).append(System.lineSeparator());
            }

            if (oldContent.toString().contains(PATCH_NAME) && !oldContent.toString().contains(PATCH_VERSION)) {
                String newContent = setNewShaderLoaderSelectedPackName(oldContent, styleUnbound, styleReimagined);

                try (FileWriter writer = new FileWriter(fileToBeModified)) {
                    writer.write(newContent);
                } catch (IOException e) {
                    log(3, "Error writing to " + shaderLoaderName + " config file: " + e.getMessage());
                }
                log(0, "Successfully applied new version in " + shaderLoaderName + ".properties config file!");
            }
        } catch (IOException e) {
            log(3, "Error reading or writing to " + shaderLoaderName + " config file: " + e.getMessage());
        }
    }

    private static String setNewShaderLoaderSelectedPackName(StringBuilder oldContent, boolean styleUnbound, boolean styleReimagined) {
        String style = styleUnbound ? "Unbound" : "Reimagined";
        if (styleUnbound && styleReimagined) { // Both styles installed
            style = oldContent.toString().contains(PATCH_NAME) && !oldContent.toString().contains(PATCH_VERSION) && oldContent.toString().contains("Unbound") ? "Unbound" : "Reimagined";
        }
        String newName = "Complementary" + style + VERSION + " + " + PATCH_NAME + PATCH_VERSION;
        return oldContent.toString().replaceAll("shaderPack=.*", "shaderPack=" + newName);
    }

    // Helper class to store shader information
    private static class ShaderInfo {
        Path baseFile = null;
        boolean styleReimagined = false;
        boolean styleUnbound = false;
        boolean isAlreadyInstalled = false;
    }
}
