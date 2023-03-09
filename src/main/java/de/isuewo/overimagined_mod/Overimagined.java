package de.isuewo.overimagined_mod;

import com.mojang.logging.LogUtils;
import io.sigpipe.jbsdiff.InvalidHeaderException;
import io.sigpipe.jbsdiff.ui.FileUI;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Objects;

@Mod(Overimagined.MODID)
public class Overimagined {
    public static final String MODID = "overimagined_mod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Overimagined() {
        LOGGER.info("Initializing Overimagined...");

        String baseName = "ComplementaryReimagined_r2.0.3";
        String baseDownloadUrl = "https://www.complementary.dev/reimagined/";
        String baseTarHash = "38a65d1d495f7ee1e7dfa2649aa26b7f";
        int baseTarSize = 786432;

        String patchName = "OverimaginedShaders-2.0-Beta3";

        File shaderpacks = FMLPaths.GAMEDIR.get().resolve("shaderpacks").toFile();

        File patchedFile = new File(shaderpacks, patchName);

        if (patchedFile.exists()) {
            LOGGER.info(patchName + " is already installed.");
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
            } else { // in case the user has the source shaderpack already extracted
                baseFile = new File(shaderpacks, baseName);
                if (baseFile.exists()) {
                    baseExtracted = baseFile;
                } else {
                    LOGGER.info(baseName + " is needed for " + patchName + ", but couldn't be found in your shaderpacks folder. Please download it from " + baseDownloadUrl + ", place it into your shaderpacks folder and restart Minecraft.");
                    return;
                }
            }
        }

        File baseArchived = new File(temp, baseName + ".tar");
        Utils.archive(baseExtracted, baseArchived);

        try {
            // needs to be done for compatibility with older minecraft version because they use an outdated version of commons-compress
            byte[] fileBytes = Files.readAllBytes(baseArchived.toPath());
            byte[] firstBytes = new byte[baseTarSize];
            System.arraycopy(fileBytes, 0, firstBytes, 0, baseTarSize);

            String hash = DigestUtils.md5Hex(firstBytes);
            if (!hash.equals(baseTarHash)) {
                LOGGER.info("The version of " + baseName + " that was found in your shaderpacks can't be used as a base for " + patchName + ". Please download it again from " + baseDownloadUrl + ", place it into your shaderpacks folder and restart Minecraft.");
                return;
            }
        } catch (IOException e) {
            LOGGER.info("The version of " + baseName + " that was found in your shaderpacks can't be used as a base for " + patchName + ". Please download it again from " + baseDownloadUrl + ", place it into your shaderpacks folder and restart Minecraft.");
            return;
        }

        File patchFile = new File(temp, patchName + ".patch");
        File patchedArchive = new File(temp, patchName + ".tar");


        try (InputStream patchStream = getClass().getClassLoader().getResourceAsStream(patchName + ".patch")) {
            try {
                FileUtils.copyInputStreamToFile(Objects.requireNonNull(patchStream), patchFile);
            } catch (IOException e) {
                LOGGER.error("Failed to retrieve patch file." + e.getMessage());
                return;
            }
        } catch (IOException e) {
            LOGGER.error("Failed to retrieve patch file." + e.getMessage());
            return;
        }

        try {
            FileUI.patch(baseArchived, patchedArchive, patchFile);
        } catch (IOException | CompressorException | InvalidHeaderException e) {
            LOGGER.error("Failed to apply patch." + e.getMessage());
            return;
        }

        Utils.extract(patchedArchive, patchedFile);

        LOGGER.info(patchName + " was successfully installed and is ready to nuke your GPU. Enjoy! -isuewo");

        if (FMLLoader.getLoadingModList().getModFileById("oculus") == null) {
            try {
                Class.forName("net.optifine.Config");
            } catch (ClassNotFoundException e) {
                System.out.println("No supported shader-loader was found. Please install either OptiFine (recommended) or Oculus.");
            }
        }
    }
}