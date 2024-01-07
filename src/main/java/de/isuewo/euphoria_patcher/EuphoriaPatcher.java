package de.isuewo.euphoria_patcher;

import com.mojang.logging.LogUtils;
import io.sigpipe.jbsdiff.InvalidHeaderException;
import io.sigpipe.jbsdiff.ui.FileUI;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

@Mod(EuphoriaPatcher.MODID)
public class EuphoriaPatcher {
    public static final String MODID = "euphoria_patcher";
    public static final Logger LOGGER = LogUtils.getLogger();

    public EuphoriaPatcher() {
        final File shaderpacks = FMLPaths.GAMEDIR.get().resolve("shaderpacks").toFile();

        final String downloadURL = "https://www.complementary.dev/";
        final String brandName = "ComplementaryShaders";
        final String version = "_r5.1.1";
        final String patchName = "EuphoriaPatches";
        final String patchVersion = "_1.2";
        final String commonLocation = "shaders/lib/common.glsl";

        // Detect which version(s) of Complementary Shaders the user has installed
        final File[] potentialFiles = shaderpacks.listFiles((dir, name) -> name.matches("Complementary.+?(?=" + version + ")" + version + "(?!\\.[0-9]).*"));
        File baseFile = null;
        boolean styleReimagined = false;
        boolean styleUnbound = false;
        boolean isAlreadyInstalled = false;
        if (potentialFiles != null) {
            ArrayList<File> zipFiles = new ArrayList<>();
            for (File potentialFile : potentialFiles) {
                if (potentialFile.getName().endsWith(".zip")) {
                    zipFiles.add(potentialFile);
                }
            }
            if (!zipFiles.isEmpty()) {
                for (File zipFile : zipFiles) {
                    if (zipFile.getName().contains("Reimagined")) {
                        styleReimagined = true;
                        if (baseFile == null) {
                            baseFile = zipFile;
                        }
                    } else if (zipFile.getName().contains("Unbound")) {
                        styleUnbound = true;
                        if (baseFile == null) {
                            baseFile = zipFile;
                        }
                    }
                    if (baseFile != null && new File(shaderpacks, baseFile.getName().replace(".zip", "") + " + " + patchName + patchVersion).exists()) {
                        baseFile = null;
                        isAlreadyInstalled = true;
                    }
                    if (styleReimagined && styleUnbound) {
                        break;
                    }
                }
            }
            if (!styleReimagined && !styleUnbound) {
                for (File f : potentialFiles) {
                    if (f.isDirectory() && !f.getName().contains(patchName)) {
                        if (f.getName().contains("Reimagined")) {
                            styleReimagined = true;
                            if (baseFile == null) {
                                baseFile = f;
                            }
                        } else if (f.getName().contains("Unbound")) {
                            styleUnbound = true;
                            if (baseFile == null) {
                                baseFile = f;
                            }
                        }
                    }
                    if (styleReimagined && styleUnbound) {
                        break;
                    }
                }
            }
        }
        if (baseFile == null) {
            if (!isAlreadyInstalled) {
                LOGGER.info("You need to have " + brandName + version + " installed. Please download it from " + downloadURL + ", place it into your shaderpacks folder and restart Minecraft.");
            }
            return;
        }

        final File temp = Utils.createTempDir();
        final String baseName = baseFile.getName().replace(".zip", "");
        final String patchedName = baseName + " + " + patchName + patchVersion;

        File baseExtracted = new File(temp, baseName);
        if (!baseFile.isDirectory()) {
            Utils.extract(baseFile, baseExtracted);
        } else {
            baseExtracted = baseFile;
        }

        try {
            final File commons = new File(baseExtracted, commonLocation);
            final String config = FileUtils.readFileToString(commons, "UTF-8").replaceFirst("SHADER_STYLE [14]", "SHADER_STYLE 1");
            FileUtils.writeStringToFile(commons, config, "UTF-8");
        } catch (IOException e) {
            LOGGER.error("Failed to extract style information from " + baseName + e.getMessage());
        }

        final String baseTarHash = "b5493f3d688e26814a04c6b1708adeb0";
        final int baseTarSize = 1134592;
        final File baseArchived = new File(temp, baseName + ".tar");
        Utils.archive(baseExtracted, baseArchived);

        final File patchedFile = new File(shaderpacks, patchedName);
        try {
            // for compatibility with older minecraft version because they use an outdated version of commons-compress
            String hash = DigestUtils.md5Hex(Arrays.copyOf(Files.readAllBytes(baseArchived.toPath()), baseTarSize));
            if (!hash.equals(baseTarHash)) {
                LOGGER.info("The version of " + brandName + " that was found in your shaderpacks can't be used as a base for " + patchName + ". Please download " + brandName + version + " from " + downloadURL + ", place it into your shaderpacks folder and restart Minecraft.");
                return;
            }
        } catch (IOException e) {
            LOGGER.info("The version of " + brandName + " that was found in your shaderpacks can't be used as a base for " + patchName + ". Please download " + brandName + version + " from " + downloadURL + ", place it into your shaderpacks folder and restart Minecraft." + e.getMessage());
            return;
        }

        final File patchedArchive = new File(temp, patchedName + ".tar");
        final File patchFile = new File(temp, patchedName + ".patch");

        try (InputStream patchStream = getClass().getClassLoader().getResourceAsStream(patchName + patchVersion + ".patch")) {
            FileUtils.copyInputStreamToFile(Objects.requireNonNull(patchStream), patchFile);
        } catch (IOException e) {
            LOGGER.error("Failed to retrieve patch file." + e.getMessage());
            return;
        }
        try {
            FileUI.patch(baseArchived, patchedArchive, patchFile);
        } catch (IOException | CompressorException | InvalidHeaderException e) {
            LOGGER.error("Failed to apply patch." + e.getMessage());
        }
        Utils.extract(patchedArchive, patchedFile);

        if (styleUnbound) {
            try {
                final File commons = new File(patchedFile, commonLocation);
                final String UnboundConfig = FileUtils.readFileToString(commons, "UTF-8").replaceFirst("SHADER_STYLE 1", "SHADER_STYLE 4");
                if (!styleReimagined) {
                    FileUtils.writeStringToFile(commons, UnboundConfig, "UTF-8");
                } else if (baseName.contains("Reimagined")) {
                    final File unbound = new File(shaderpacks, patchedName.replace("Reimagined", "Unbound"));
                    FileUtils.copyDirectory(patchedFile, unbound);
                    FileUtils.writeStringToFile(new File(unbound, commonLocation), UnboundConfig, "UTF-8");
                } else {
                    final File reimagined = new File(shaderpacks, patchedName.replace("Unbound", "Reimagined"));
                    FileUtils.copyDirectory(patchedFile, reimagined);
                    FileUtils.writeStringToFile(commons, UnboundConfig, "UTF-8");
                }
            } catch (IOException e) {
                LOGGER.error("Failed to apply style settings." + e.getMessage());
            }
        }

        LOGGER.info(patchName + " was successfully installed. Enjoy! -SpacEagle17 & isuewo");
    }
}
