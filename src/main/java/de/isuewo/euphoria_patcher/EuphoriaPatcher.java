package de.isuewo.euphoria_patcher;

import com.mojang.logging.LogUtils;
import io.sigpipe.jbsdiff.InvalidHeaderException;
import io.sigpipe.jbsdiff.ui.FileUI;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;


@Mod("euphoria_patcher")
public class EuphoriaPatcher
{
    private static final Logger LOGGER = LogUtils.getLogger();

    public EuphoriaPatcher() {
        if(FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            LOGGER.info("The Euphoria Patcher should not be loaded on a server! Disabling...");
            return;
        }
        final Path shaderpacks = FMLPaths.GAMEDIR.get().resolve("shaderpacks");

        final String downloadURL = "https://www.complementary.dev/";
        final String brandName = "ComplementaryShaders";
        final String version = "_r5.2.2";
        final String patchName = "EuphoriaPatches";
        final String patchVersion = "_1.3.2";
        final String commonLocation = "shaders/lib/common.glsl";

        // Detect which version(s) of Complementary Shaders the user has installed
        Path baseFile = null;
        boolean styleReimagined = false;
        boolean styleUnbound = false;
        boolean isAlreadyInstalled = false;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks, path -> {
            String name = path.getFileName().toString();
            return name.matches("Complementary.+?(?=" + version + ")" + version + "(?!\\.[0-9]).*") && name.endsWith(".zip") && !name.contains(patchName);
        })) {
            for (Path potentialFile : stream) {
                String name = potentialFile.getFileName().toString();
                if (name.endsWith(".zip")) {
                    if (name.contains("Reimagined")) {
                        styleReimagined = true;
                        if (baseFile == null) {
                            baseFile = potentialFile;
                        }
                    } else if (name.contains("Unbound")) {
                        styleUnbound = true;
                        if (baseFile == null) {
                            baseFile = potentialFile;
                        }
                    }
                    if (baseFile != null && Files.exists(shaderpacks.resolve(baseFile.getFileName().toString().replace(".zip", "") + " + " + patchName + patchVersion))) {
                        baseFile = null;
                        isAlreadyInstalled = true;
                    }
                    if (styleReimagined && styleUnbound) {
                        break;
                    }
                }
            }
            if (!styleReimagined && !styleUnbound) {
                try (DirectoryStream<Path> stream2 = Files.newDirectoryStream(shaderpacks, path -> {
                    String name = path.getFileName().toString();
                    return name.matches("Complementary.+?(?=" + version + ")" + version + "(?!\\.[0-9]).*") && Files.isDirectory(path);
                })) {
                    for (Path potentialFile : stream2) {
                        String name = potentialFile.getFileName().toString();
                        if (name.contains(patchName)) {
                            if(name.contains(patchName + patchVersion)) {
                                isAlreadyInstalled = true;
                            }
                            continue;
                        }
                        if (name.contains("Reimagined")) {
                            styleReimagined = true;
                            if (baseFile == null) {
                                baseFile = potentialFile;
                            }
                        } else if (name.contains("Unbound")) {
                            styleUnbound = true;
                            if (baseFile == null) {
                                baseFile = potentialFile;
                            }
                        }
                        if (styleReimagined && styleUnbound) {
                            break;
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Error reading shaderpacks directory {}", e.getMessage());
            return;
        }
        if (baseFile == null) {
            if (!isAlreadyInstalled) {
                LOGGER.info("You need to have " + brandName + version + " installed. Please download it from " + downloadURL + ", place it into your shaderpacks folder and restart Minecraft.");
            }
            return;
        }

        final Path temp;
        try {
            temp = Files.createTempDirectory("euphoria-patcher-");
        } catch (IOException e) {
            LOGGER.error("Error creating temporary directory {}", e.getMessage());
            return;
        }
        final String baseName = baseFile.getFileName().toString().replace(".zip", "");
        final String patchedName = baseName + " + " + patchName + patchVersion;

        Path baseExtracted = temp.resolve(baseName);
        if (!Files.isDirectory(baseFile)) {
            ArchiveUtils.extract(baseFile, baseExtracted);
        } else {
            baseExtracted = baseFile;
        }

        try {
            final Path commons = baseExtracted.resolve(commonLocation);
            final String config = FileUtils.readFileToString(commons.toFile(), "UTF-8").replaceFirst("SHADER_STYLE [14]", "SHADER_STYLE 1");
            FileUtils.writeStringToFile(commons.toFile(), config, "UTF-8");
        } catch (IOException e) {
            LOGGER.error("Error extracting style information from {} {}", baseName, e.getMessage());
            return;
        }

        final String baseTarHash = "46a2fb63646e22cea56b2f8fa5815ac2";
        final int baseTarSize = 1274880;
        final Path baseArchived = temp.resolve(baseName + ".tar");
        ArchiveUtils.archive(baseExtracted, baseArchived);

        final Path patchedFile = shaderpacks.resolve(patchedName);
        try {
            // for compatibility with older minecraft version because they use an outdated version of commons-compress
            String hash = DigestUtils.md5Hex(Arrays.copyOf(Files.readAllBytes(baseArchived), baseTarSize));
            if (!hash.equals(baseTarHash)) {
                LOGGER.info("The version of " + brandName + " that was found in your shaderpacks can't be used as a base for " + patchName + ". Please download " + brandName + version + " from " + downloadURL + ", place it into your shaderpacks folder and restart Minecraft.");
                return;
            }
        } catch (IOException e) {
            LOGGER.info("The version of " + brandName + " that was found in your shaderpacks can't be used as a base for " + patchName + ". Please download " + brandName + version + " from " + downloadURL + ", place it into your shaderpacks folder and restart Minecraft. {}", e.getMessage());
            return;
        }

        final Path patchedArchive = temp.resolve(patchedName + ".tar");
        final Path patchFile = (temp).resolve(patchedName + ".patch");

        try (InputStream patchStream = getClass().getClassLoader().getResourceAsStream(patchName + patchVersion + ".patch")) {
            FileUtils.copyInputStreamToFile(Objects.requireNonNull(patchStream), patchFile.toFile());
            FileUI.patch(baseArchived.toFile(), patchedArchive.toFile(), patchFile.toFile());
            ArchiveUtils.extract(patchedArchive, patchedFile);
        } catch (IOException | CompressorException | InvalidHeaderException e) {
            LOGGER.error("Error applying patch file. {}", e.getMessage());
            return;
        }

        if (styleUnbound) {
            try {
                final File commons = new File(patchedFile.toFile(), commonLocation);
                final String UnboundConfig = FileUtils.readFileToString(commons, "UTF-8").replaceFirst("SHADER_STYLE 1", "SHADER_STYLE 4");
                if (!styleReimagined) {
                    FileUtils.writeStringToFile(commons, UnboundConfig, "UTF-8");
                } else if (baseName.contains("Reimagined")) {
                    final File unbound = new File(shaderpacks.toFile(), patchedName.replace("Reimagined", "Unbound"));
                    FileUtils.copyDirectory(patchedFile.toFile(), unbound);
                    FileUtils.writeStringToFile(new File(unbound, commonLocation), UnboundConfig, "UTF-8");
                } else {
                    final File reimagined = new File(shaderpacks.toFile(), patchedName.replace("Unbound", "Reimagined"));
                    FileUtils.copyDirectory(patchedFile.toFile(), reimagined);
                    FileUtils.writeStringToFile(commons, UnboundConfig, "UTF-8");
                }
            } catch (IOException e) {
                LOGGER.error("Error applying style settings. {}", e.getMessage());
                return;
            }
        }

        LOGGER.info(patchName + " was successfully installed. Enjoy! -SpacEagle17 & isuewo");
    }
}