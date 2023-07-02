package de.isuewo.euphoria_patcher;

import io.sigpipe.jbsdiff.InvalidHeaderException;
import io.sigpipe.jbsdiff.ui.FileUI;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Objects;

public class EuphoriaPatcher implements ModInitializer {

    @Override
    public void onInitialize() {
        boolean isDev = FabricLoader.getInstance().isDevelopmentEnvironment();

        System.out.println("Initializing Euphoria Patcher...");

        String baseName = "ComplementaryReimagined_r2.2.1";
        String baseDownloadUrl = "https://www.complementary.dev/reimagined/";
        String baseTarHash = "9ddea5a6425b687e6871aecdfbc0b3af";
        int baseTarSize = 858112;

        String patchName = "ComplementaryReimagined r2.2 + Euphoria Patches B7";

        File shaderpacks = FabricLoader.getInstance().getGameDir().resolve("shaderpacks").toFile();

        File patchedFile = new File(shaderpacks, patchName);

        if (!isDev && patchedFile.exists()) {
            System.out.println(patchName + " is already installed.");
            return;
        }

        File temp = Utils.createTempDir();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                FileUtils.deleteDirectory(temp);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));

        File baseFile = new File(shaderpacks, baseName + ".zip");
        File baseExtracted = new File(temp, baseName);

        if (baseFile.exists()) {
            Utils.extract(baseFile, baseExtracted);
        } else {
            File[] baseFiles = shaderpacks.listFiles((dir, name) -> name.startsWith(baseName) && name.endsWith(".zip"));
            if (baseFiles != null && baseFiles.length > 0) {
                baseFile = baseFiles[0];
                Utils.extract(baseFile, baseExtracted);
            } else  { // in case the user has the source shaderpack already extracted
                baseFile = new File(shaderpacks, baseName);
                if (baseFile.exists()) {
                    baseExtracted = baseFile;
                } else {
                    System.out.println(baseName + " is needed for " + patchName + ", but couldn't be found in your shaderpacks folder. Please download it from " + baseDownloadUrl + ", place it into your shaderpacks folder and restart Minecraft.");
                    return;
                }
            }
        }

        File baseArchived = new File(temp, baseName + ".tar");
        Utils.archive(baseExtracted, baseArchived);

        try {
            if (isDev) {
                String hash = DigestUtils.md5Hex(Files.newInputStream(baseArchived.toPath()));
                System.out.println("Hash of " + baseName + ": " + hash);
                System.out.println(baseArchived.length() + " bytes");
            } else {
                // needs to be done for compatibility with older minecraft version because they use an outdated version of commons-compress
                byte[] fileBytes = Files.readAllBytes(baseArchived.toPath());
                byte[] firstBytes = new byte[baseTarSize];
                System.arraycopy(fileBytes, 0, firstBytes, 0, baseTarSize);

                String hash = DigestUtils.md5Hex(firstBytes);
                if (!hash.equals(baseTarHash)) {
                    System.out.println("The version of " + baseName + " that was found in your shaderpacks can't be used as a base for " + patchName + ". Please download it again from " + baseDownloadUrl + ", place it into your shaderpacks folder and restart Minecraft.");
                    return;
                }
            }
        } catch (IOException e) {
            System.out.println("The version of " + baseName + " that was found in your shaderpacks can't be used as a base for " + patchName + ". Please download it again from " + baseDownloadUrl + ", place it into your shaderpacks folder and restart Minecraft.");
            return;
        }

        File patchFile = new File(isDev ? shaderpacks : temp, patchName + ".patch");
        File patchedArchive = new File(temp, patchName + ".tar");

        if (isDev) {
            Utils.archive(patchedFile, patchedArchive);
            try {
                FileUI.diff(baseArchived, patchedArchive, patchFile);
            } catch (IOException | CompressorException | InvalidHeaderException e) {
                e.printStackTrace();
            }
        } else {
            try (InputStream patchStream = getClass().getClassLoader().getResourceAsStream(patchName + ".patch")) {
                try {
                    FileUtils.copyInputStreamToFile(Objects.requireNonNull(patchStream), patchFile);
                } catch (IOException e) {
                    System.err.println("Failed to retrieve patch file." + e.getMessage());
                    return;
                }
            } catch (IOException e) {
                System.err.println("Failed to retrieve patch file." + e.getMessage());
                return;
            }

            try {
                FileUI.patch(baseArchived, patchedArchive, patchFile);
            } catch (IOException | CompressorException | InvalidHeaderException e) {
                System.err.println("Failed to apply patch." + e.getMessage());
                return;
            }

            Utils.extract(patchedArchive, patchedFile);
        }

        System.out.println(patchName + " was successfully installed and is ready to nuke your GPU. Enjoy! -isuewo");

        // Enable installed shaderpack by default
        if (FabricLoader.getInstance().isModLoaded("iris")) {
            File irisProperties = FabricLoader.getInstance().getGameDir().resolve("config/iris.properties").toFile();
            try {
                String irisPropertiesContent = FileUtils.readFileToString(irisProperties, "UTF-8");
                irisPropertiesContent = irisPropertiesContent.replaceAll("enableShaders=false", "enableShaders=true").replaceAll("shaderPack=.*", "shaderPack=" + patchName);
                FileUtils.writeStringToFile(irisProperties, irisPropertiesContent, "UTF-8");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (FabricLoader.getInstance().isModLoaded("optifabric")) {
            File optionsshaders = FabricLoader.getInstance().getGameDir().resolve("optionsshaders.txt").toFile();
            try {
                String optionsshadersContent = FileUtils.readFileToString(optionsshaders, "UTF-8");
                optionsshadersContent = optionsshadersContent.replaceAll("shaderPack=.+", "shaderPack=" + patchName);
                FileUtils.writeStringToFile(optionsshaders, optionsshadersContent, "UTF-8");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("No supported shader-loader was found. Please install either Iris (recommended) or OptiFine.");
        }
    }
}
