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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

public class EuphoriaPatcher implements ModInitializer {
    private static boolean isSodiumLoaded;

    private static void log(int messageLevel, String message) {
        if (isSodiumLoaded) {
            SodiumConsole.logMessage(messageLevel, message);
        }
        if (messageLevel == 2) {
            System.err.println(message);
        } else {
            System.out.println(message);
        }
    }

    @Override
    public void onInitialize() {
        try {
            Class.forName("me.jellysquid.mods.sodium.client.gui.console.Console");
            isSodiumLoaded = true;
        } catch (ClassNotFoundException e) {
            isSodiumLoaded = false;
        }

        final boolean isDev = false;

        final Path shaderpacks = FabricLoader.getInstance().getGameDir().resolve("shaderpacks");

        final String downloadURL = "https://www.complementary.dev/";
        final String brandName = "ComplementaryShaders";
        final String version = "_r5.2";
        final String patchName = "EuphoriaPatches";
        final String patchVersion = "_1.3";
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
                    if (baseFile != null && Files.exists(shaderpacks.resolve(baseFile.getFileName().toString().replace(".zip", "") + " + " + patchName + patchVersion)) && !isDev) {
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
            log(2, "Error reading shaderpacks directory" + e.getMessage());
            return;
        }
        if (baseFile == null) {
            if (!isAlreadyInstalled) {
                log(1, "You need to have " + brandName + version + " installed. Please download it from " + downloadURL + ", place it into your shaderpacks folder and restart Minecraft.");
            }
            return;
        }

        final Path temp;
        try {
            temp = Files.createTempDirectory("euphoria-patcher-");
        } catch (IOException e) {
            log(2, "Error creating temporary directory" + e.getMessage());
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
            log(2, "Error extracting style information from " + baseName + e.getMessage());
            return;
        }

        final String baseTarHash = "a15f22c5d917b0af3fdbc695217afb92";
        final int baseTarSize = 1270272;
        final Path baseArchived = temp.resolve(baseName + ".tar");
        ArchiveUtils.archive(baseExtracted, baseArchived);

        final Path patchedFile = shaderpacks.resolve(patchedName);
        try {
            if (isDev) {
                String hash = DigestUtils.md5Hex(Files.newInputStream(baseArchived));

                log(0, "Hash of " + baseName + ": " + hash);
                log(0,FileUtils.sizeOf(baseArchived.toFile()) + " bytes");
            } else {
                // for compatibility with older minecraft version because they use an outdated version of commons-compress
                String hash = DigestUtils.md5Hex(Arrays.copyOf(Files.readAllBytes(baseArchived), baseTarSize));
                if (!hash.equals(baseTarHash)) {
                    log(1, "The version of " + brandName + " that was found in your shaderpacks can't be used as a base for " + patchName + ". Please download " + brandName + version + " from " + downloadURL + ", place it into your shaderpacks folder and restart Minecraft.");
                    return;
                }
            }
        } catch (IOException e) {
            log(1,"The version of " + brandName + " that was found in your shaderpacks can't be used as a base for " + patchName + ". Please download " + brandName + version + " from " + downloadURL + ", place it into your shaderpacks folder and restart Minecraft." + e.getMessage());
            return;
        }

        final Path patchedArchive = temp.resolve(patchedName + ".tar");
        final Path patchFile = (isDev ? shaderpacks : temp).resolve(patchedName + ".patch");

        if (isDev) {
            try {
                ArchiveUtils.archive(patchedFile, patchedArchive);
                FileUI.diff(baseArchived.toFile(), patchedArchive.toFile(), patchFile.toFile());
            } catch (CompressorException | IOException | InvalidHeaderException e) {
                throw new RuntimeException(e);
            }
        } else {
            try (InputStream patchStream = getClass().getClassLoader().getResourceAsStream(patchName + patchVersion + ".patch")) {
                FileUtils.copyInputStreamToFile(Objects.requireNonNull(patchStream), patchFile.toFile());
                FileUI.patch(baseArchived.toFile(), patchedArchive.toFile(), patchFile.toFile());
                ArchiveUtils.extract(patchedArchive, patchedFile);
            } catch (IOException | CompressorException | InvalidHeaderException e) {
                log(2,"Error applying patch file." + e.getMessage());
                return;
            }
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
                log(2,"Error applying style settings." + e.getMessage());
                return;
            }
        }

        log(0,patchName + " was successfully installed. Enjoy! -SpacEagle17 & isuewo");
    }
}