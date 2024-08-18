package de.isuewo.euphoria_patcher;

import io.sigpipe.jbsdiff.InvalidHeaderException;
import io.sigpipe.jbsdiff.ui.FileUI;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

public class EuphoriaPatcher implements ModInitializer {
    private static boolean isSodiumLoaded;

    // Constants
    private static final boolean IS_DEV = false; // Manual Boolean. DON'T FORGET TO SET TO FALSE BEFORE COMPILING

    private static final String DOWNLOAD_URL = "https://www.complementary.dev/";
    private static final String COMMON_LOCATION = "shaders/lib/common.glsl";

    private static final String BRAND_NAME = "ComplementaryShaders";
    private static final String PATCH_NAME = "EuphoriaPatches";
    private static final String VERSION = "_r5.2.2";
    private static final String PATCH_VERSION = "_1.3.2";

    private static final String BASE_TAR_HASH = "46a2fb63646e22cea56b2f8fa5815ac2";
    private static final int BASE_TAR_SIZE = 1274880;

    // Logging method (unchanged)
    private static void log(int messageLevel, String message) {
        if (isSodiumLoaded) {
            SodiumConsole.logMessage(messageLevel, message);
        } else {
            if (messageLevel == 3) {
                System.err.println(message);
            } else {
                System.out.println(message);
            }
        }
    }

    @Override
    public void onInitialize() {
        // Check if Sodium is loaded
        try {
            Class.forName("me.jellysquid.mods.sodium.client.gui.console.Console");
            isSodiumLoaded = true;
        } catch (ClassNotFoundException e) {
            isSodiumLoaded = false;
        }

        // Get necessary paths
        Path shaderpacks = FabricLoader.getInstance().getGameDir().resolve("shaderpacks");
        Path irisConfig = FabricLoader.getInstance().getConfigDir().resolve("iris.properties");

        // Detect installed Complementary Shaders versions
        ShaderInfo shaderInfo = detectInstalledShaders(shaderpacks);

        if (shaderInfo.baseFile == null) {
            if (!shaderInfo.isAlreadyInstalled) {
                log(2, "You need to have " + BRAND_NAME + VERSION + " installed. Please download it from " + DOWNLOAD_URL + ", place it into your shaderpacks folder and restart Minecraft.");
            }
            return;
        }

        // Create temporary directory
        Path temp = createTempDirectory();
        if (temp == null) return;

        // Process and patch shaders
        if (!processAndPatchShaders(shaderInfo, temp, shaderpacks)) return;
        
        // Update .txt shader config file
//        updateShaderTxtConfigFile(shaderpacks, shaderInfo.styleUnbound); // Commented out for now as it causes .txt file overwriting for some reason

        // Update Iris config
        updateIrisConfig(irisConfig, shaderInfo.styleUnbound);
    }

    // Detect installed Complementary Shaders versions
    private ShaderInfo detectInstalledShaders(Path shaderpacks) {
        ShaderInfo info = new ShaderInfo();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks, this::isComplementaryShader)) {
            for (Path potentialFile : stream) {
                processShaderFile(potentialFile, info);
                if (info.styleReimagined && info.styleUnbound) break;
            }
            if (!info.styleReimagined && !info.styleUnbound) {
                detectInstalledDirectories(shaderpacks, info);
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

    // Detect installed directories
    private void detectInstalledDirectories(Path shaderpacks, ShaderInfo info) throws IOException {
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
    private boolean processAndPatchShaders(ShaderInfo info, Path temp, Path shaderpacks) {
        String baseName = info.baseFile.getFileName().toString().replace(".zip", "");
        String patchedName = baseName + " + " + PATCH_NAME + PATCH_VERSION;

        Path baseExtracted = extractBase(info.baseFile, temp, baseName);
        if (baseExtracted == null) return false;

        if (!updateCommonFile(baseExtracted)) return false;

        Path baseArchived = archiveBase(baseExtracted, temp, baseName);
        if (baseArchived == null) return false;

        if (!verifyBaseArchive(baseArchived)) return false;

        return applyPatch(baseArchived, temp, patchedName, shaderpacks, info.styleUnbound, info.styleReimagined);
    }

    // Extract base shader
    private Path extractBase(Path baseFile, Path temp, String baseName) {
        Path baseExtracted = temp.resolve(baseName);
        if (!Files.isDirectory(baseFile)) {
            ArchiveUtils.extract(baseFile, baseExtracted);
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
        ArchiveUtils.archive(baseExtracted, baseArchived);
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
    private boolean applyPatch(Path baseArchived, Path temp, String patchedName, Path shaderpacks, boolean styleUnbound, boolean styleReimagined) {
        Path patchedArchive = temp.resolve(patchedName + ".tar");
        Path patchFile = (IS_DEV ? shaderpacks : temp).resolve(patchedName + ".patch");
        Path patchedFile = shaderpacks.resolve(patchedName);

        if (IS_DEV) {
            return createDevPatch(baseArchived, patchedFile, patchedArchive, patchFile);
        } else {
            return applyProductionPatch(baseArchived, patchedArchive, patchFile, patchedFile, styleUnbound, styleReimagined);
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
                ArchiveUtils.extract(patchedArchive, patchedFile);
                applyStyleSettings(patchedFile, styleUnbound, styleReimagined);
                log(1, PATCH_NAME + " was successfully installed. Enjoy! -SpacEagle17 & isuewo");
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
    private void updateShaderTxtConfigFile(Path shaderpacks, boolean styleUnbound) {
        try (DirectoryStream<Path> textStream = Files.newDirectoryStream(shaderpacks, this::isConfigFile)) {
            Path textFilePath = findLatestConfigFile(textStream);
            if (textFilePath != null) {
                String style = styleUnbound ? "Unbound" : "Reimagined";
                String newName = "Complementary" + style + VERSION + " + " + PATCH_NAME + PATCH_VERSION + ".txt";
                try {
                    Files.move(textFilePath, textFilePath.resolveSibling(newName));
                    log(0, "Successfully updated shader config file to the latest version!");
                } catch (IOException e) {
                    log(3, "Could not rename the config file: " + e.getMessage());
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
    private Path findLatestConfigFile(DirectoryStream<Path> textStream) {
        Path latestFile = null;
        for (Path potentialTextFile : textStream) {
            String name = potentialTextFile.getFileName().toString();
            if (name.endsWith(".txt")) {
                latestFile = potentialTextFile;
                if (name.contains(PATCH_VERSION)) {
                    return null; // Prevent updating already updated config files
                }
            }
        }
        return latestFile;
    }

    // Update Iris config
    private void updateIrisConfig(Path irisConfig, boolean styleUnbound) {
        if (!Files.exists(irisConfig)) return;

        File fileToBeModified = irisConfig.toFile();
        StringBuilder oldContent = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new FileReader(fileToBeModified))) {
            String line;
            while ((line = reader.readLine()) != null) {
                oldContent.append(line).append(System.lineSeparator());
            }
            boolean writeToFile = oldContent.toString().contains(PATCH_NAME) && !oldContent.toString().contains(PATCH_VERSION);

            if (writeToFile) {
                String style = styleUnbound ? "Unbound" : "Reimagined";
                String newName = "Complementary" + style + VERSION + " + " + PATCH_NAME + PATCH_VERSION;
                String newContent = oldContent.toString().replaceAll("shaderPack=.*", "shaderPack=" + newName);

                try (FileWriter writer = new FileWriter(fileToBeModified)) {
                    writer.write(newContent);
                }
                log(0, "Successfully applied new version with iris!");
            }
        } catch (IOException e) {
            log(3, "Error reading or writing to iris config file: " + e.getMessage());
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
