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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public class EuphoriaPatcher implements ModInitializer {

    @Override
    public void onInitialize() {
        final boolean isDev = FabricLoader.getInstance().isDevelopmentEnvironment();
        final File shaderpacks = FabricLoader.getInstance().getGameDir().resolve("shaderpacks").toFile();
        System.out.println("Initializing Euphoria Patcher...");

        final String downloadURL = "https://www.complementary.dev/";
        final String brandName = "ComplementaryShaders";
        final String version = "_r5.0";
        final String patchName = "EuphoriaPatches";
        final String patchVersion = "_1.0";
        final String commonLocation = "shaders/lib/common.glsl";

        // Detect which version(s) of Complementary Shaders the user has installed
        final File[] potentialFiles = shaderpacks.listFiles((dir, name) -> name.matches("Complementary.+?(?=" + version + ")" + version + "(?!\\.[0-9]).*"));
        File baseFile = null;
        boolean styleReimagined = false;
        boolean styleUnbound = false;
        if (potentialFiles != null) {
            ArrayList<File> zipFiles = new ArrayList<>();
            for (File potentialFile : Objects.requireNonNull(potentialFiles)) {
                if (potentialFile.getName().endsWith(".zip")) {
                    zipFiles.add(potentialFile);
                }
            }
            if (zipFiles.size() > 0) {
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
                        //baseFile = null;
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
            System.out.println(patchName + " have already been applied or you need to have a version of " + brandName + " installed. Please download it from " + downloadURL + ", place it into your shaderpacks folder and restart Minecraft.");
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
            throw new RuntimeException(e);
        }

        final String baseTarHash = "13ae1fe7fcdf6fcce98276cd72e433bf";
        final int baseTarSize = 1090048;
        final File baseArchived = new File(temp, baseName + ".tar");
        Utils.archive(baseExtracted, baseArchived);

        final File patchedFile = new File(shaderpacks, patchedName);
        try {
            if (isDev) {
                String hash = DigestUtils.md5Hex(Files.newInputStream(baseArchived.toPath()));
                System.out.println("Hash of " + baseName + ": " + hash);
                System.out.println(baseArchived.length() + " bytes");
            } else {
                // for compatibility with older minecraft version because they use an outdated version of commons-compress
                String hash = DigestUtils.md5Hex(Arrays.copyOf(Files.readAllBytes(baseArchived.toPath()), baseTarSize));
                if (!hash.equals(baseTarHash)) {
                    System.out.println("The version of " + brandName + " that was found in your shaderpacks can't be used as a base for " + patchName + ". Please download it again from " + downloadURL + ", place it into your shaderpacks folder and restart Minecraft.");
                    return;
                }
            }
        } catch (IOException e) {
            System.out.println("The version of " + brandName + " that was found in your shaderpacks can't be used as a base for " + patchName + ". Please download it again from " + downloadURL + ", place it into your shaderpacks folder and restart Minecraft.");
            return;
        }

        final File patchedArchive = new File(temp, patchedName + ".tar");
        final File patchFile = new File(isDev ? shaderpacks : temp, patchedName + ".patch");

        if (isDev) {
            Utils.archive(patchedFile, patchedArchive);
            try {
                FileUI.diff(baseArchived, patchedArchive, patchFile);
            } catch (IOException | CompressorException | InvalidHeaderException e) {
                e.printStackTrace();
            }
            return;
        } else {
            try (InputStream patchStream = getClass().getClassLoader().getResourceAsStream(patchName + patchVersion + ".patch")) {
                FileUtils.copyInputStreamToFile(Objects.requireNonNull(patchStream), patchFile);
            } catch (IOException e) {
                System.err.println("Failed to retrieve patch file." + e.getMessage());
                return;
            }
            try {
                FileUI.patch(baseArchived, patchedArchive, patchFile);
            } catch (IOException | CompressorException | InvalidHeaderException e) {
                System.err.println("Failed to apply patch." + e.getMessage());
            }
            Utils.extract(patchedArchive, patchedFile);
        }

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
                throw new RuntimeException(e);
            }
        }

        System.out.println(patchName + " was successfully installed and is ready to nuke your GPU. Enjoy! -isuewo");

        // Enable installed shaderpack by default
        try {
            if (FabricLoader.getInstance().isModLoaded("iris")) {
                final File configFile = FabricLoader.getInstance().getGameDir().resolve("config/iris.properties").toFile();
                final String config = FileUtils.readFileToString(configFile, "UTF-8").replaceFirst("enableShaders=false", "enableShaders=true").replaceFirst("shaderPack=.*", "shaderPack=" + patchedName);
                FileUtils.writeStringToFile(configFile, config, "UTF-8");
            }
        } catch (IOException ignored) {
        }
    }
}
