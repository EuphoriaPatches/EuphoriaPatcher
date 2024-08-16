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
        final Path irisConfig = FabricLoader.getInstance().getConfigDir().resolve("iris.properties");

        final String downloadURL = "https://www.complementary.dev/";
        final String brandName = "ComplementaryShaders";
        final String version = "_r5.2.2";
        final String patchName = "EuphoriaPatches";
        final String patchVersion = "_1.3.2";
        final String commonLocation = "shaders/lib/common.glsl";
        final String baseTarHash = "46a2fb63646e22cea56b2f8fa5815ac2";
        final int baseTarSize = 1274880;

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

        final Path baseArchived = temp.resolve(baseName + ".tar");
        ArchiveUtils.archive(baseExtracted, baseArchived);

        final Path patchedFile = shaderpacks.resolve(patchedName);
//        final Path patchedFilePath = shaderpacks.resolve(patchedName + ".patch"); // For Testing .txt deletion
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
                if (patchStream != null) {
//                    if (!patchFile.toString().endsWith(".txt")) {
                        FileUtils.copyInputStreamToFile(Objects.requireNonNull(patchStream), patchFile.toFile());
                        FileUI.patch(baseArchived.toFile(), patchedArchive.toFile(), patchFile.toFile());
                        ArchiveUtils.extract(patchedArchive, patchedFile); // I think this line causes the .txt deletion
//                    }
                }
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

        // Code to update the config file to the latest version
        Path textFilePath = null;
        try (DirectoryStream<Path> textStream = Files.newDirectoryStream(shaderpacks, path -> { // shaderpack config txt file finder
            String nameText = path.getFileName().toString();
            return nameText.matches("Complementary.*") && nameText.endsWith(".txt") && nameText.contains(patchName);
        })) {
            for (Path potentialTextFile : textStream) { // First find the latest .txt file the user used
                String name = potentialTextFile.getFileName().toString();
                if (name.endsWith(".txt")) {
                    textFilePath = potentialTextFile; // This is the path to the correct txt file
                    // Files are automatically sorted alphabetically, meaning latest version overwrites the previous ones in the loop - no need to sort version numbers
                    if (name.contains(patchVersion)) { // Prevent it updating already updated config files
                        textFilePath = null;
                    }
                }
            }
            if (textFilePath != null) { // Now with the info of the latest .txt file rename it to the latest version
                String style = styleUnbound ? "Unbound" : "Reimagined";

                String newName = "Complementary" + style + version + " + " + patchName + patchVersion + ".txt";
                try{
                    Files.move(textFilePath, textFilePath.resolveSibling(newName)); // rename a file in the directory of the path
                    log(0, "Successfully updated shader config file to the latest version!");
                } catch (IOException e) {
                    log(2, "Could not rename the config file" + e.getMessage());
                    return;
                }
            }
//            try { // Test to rename the EP folder so .txt does not get deleted - did not work sadly
//                Files.move(patchedFilePath, patchedFilePath.resolveSibling(patchedName)); // rename a folder in the directory of the path
//            } catch (IOException e) {
//                log(2, "Could not rename the EP folder" + e.getMessage());
//                return;
//            }
        } catch (IOException e) {
            log(2, "Error reading shaderpacks directory" + e.getMessage());
            return;
        }

        if (Files.exists(irisConfig)) {
            File fileToBeModified = new File(String.valueOf(irisConfig));
            StringBuilder oldContent = new StringBuilder();
            BufferedReader reader = null;
            FileWriter writer = null;
            boolean writeToFile = false;

            try {
                reader = new BufferedReader(new FileReader(fileToBeModified));
                String line = reader.readLine();

                while (line != null) { // Reading all the lines of input text file into oldContent
                    oldContent.append(line).append(System.lineSeparator());
                    line = reader.readLine();
                }
                writeToFile = oldContent.toString().contains(patchName) && !oldContent.toString().contains(patchVersion); // Failsafe

                if (writeToFile) {
                    String style = styleUnbound ? "Unbound" : "Reimagined";
                    String newName = "Complementary" + style + version + " + " + patchName + patchVersion;
                    String newContent = oldContent.toString().replaceAll("shaderPack=.*", "shaderPack=" + newName); // Replace using regex

                    writer = new FileWriter(fileToBeModified); //Rewriting the input text file with newContent
                    writer.write(newContent);
                    log(0, "Successfully applied new version with iris!");
                }
            } catch (IOException e) {
                log(2, "Error reading or writing to iris config file" + e.getMessage());
            } finally {
                try {
                    assert reader != null;
                    reader.close(); //Closing the resources
                    if (writeToFile) {
                        assert writer != null;
                        writer.close();
                    }
                } catch (IOException e) {
                    log(2, "Error closing iris config reader or writer" + e.getMessage());
                }
            }
        }
    }
}
