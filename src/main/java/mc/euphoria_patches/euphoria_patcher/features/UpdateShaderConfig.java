package mc.euphoria_patches.euphoria_patcher.features;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateShaderConfig {
    public static void updateShaderTxtConfigFile(boolean styleUnbound, boolean styleReimagined) {
        try (DirectoryStream<Path> oldConfigTextStream = Files.newDirectoryStream(EuphoriaPatcher.shaderpacks,
                path -> isConfigFile(path, true))) {
            Path oldShaderConfigFilePath = findShaderConfigFile(oldConfigTextStream, true);
            if (oldShaderConfigFilePath != null) {
                doConfigFileCopy(oldShaderConfigFilePath, true, styleUnbound, styleReimagined);
            } else { // No Euphoria settings .txt file
                try (DirectoryStream<Path> baseShaderConfigTextStream = Files.newDirectoryStream(EuphoriaPatcher.shaderpacks,
                        path -> isConfigFile(path, false))) {
                    Path baseShaderConfigFilePath = findShaderConfigFile(baseShaderConfigTextStream, true);
                    if (baseShaderConfigFilePath != null) {
                        doConfigFileCopy(baseShaderConfigFilePath, false, styleUnbound, styleReimagined);
                    }
                } catch (IOException e) {
                    EuphoriaPatcher.log(3,0, "Error reading shaderpacks directory: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            EuphoriaPatcher.log(3,0, "Error reading shaderpacks directory: " + e.getMessage());
        }
    }

    private static void doConfigFileCopy(Path configFilePath, boolean containsPatchName, boolean styleUnbound, boolean styleReimagined){
        String style = styleUnbound ? "Unbound" : "Reimagined";
        String newName = EuphoriaPatcher.BRAND_NAME + style + EuphoriaPatcher.VERSION + " + " + EuphoriaPatcher.PATCH_NAME + EuphoriaPatcher.PATCH_VERSION + ".txt";
        try {
            Files.copy(configFilePath, configFilePath.resolveSibling(newName)); // Copy old config and rename it to current PATCH_VERSION
            EuphoriaPatcher.log(0, "Successfully updated shader config file to the latest version!");
        } catch (IOException e) {
            EuphoriaPatcher.log(3,0, "Could not rename the config file: " + e.getMessage());
        }
        if (styleUnbound && styleReimagined) { // Yeah, this makes things unnecessarily complex lol
            EuphoriaPatcher.log(0, "Both shader styles detected!");
            try (DirectoryStream<Path> latestConfigTextStream = Files.newDirectoryStream(EuphoriaPatcher.shaderpacks,
                    path -> isConfigFile(path, containsPatchName))) { // Create a new DirectoryStream - The iterator of Files.newDirectoryStream can only be used once
                Path latestShaderConfigFilePath = findShaderConfigFile(latestConfigTextStream, false);
                if (latestShaderConfigFilePath != null) {
                    style = latestShaderConfigFilePath.toString().contains("Unbound") ? "Reimagined" : "Unbound"; // Detect what the previously renamed (oldShaderConfigFilePath) .txt contains
                    newName = EuphoriaPatcher.BRAND_NAME + style + EuphoriaPatcher.VERSION + " + " + EuphoriaPatcher.PATCH_NAME + EuphoriaPatcher.PATCH_VERSION + ".txt";
                    try { // Now copy and past the renamed .txt file with a new name - 2 identical.txt files with different style names are now in the shaderpacks folder
                        Files.copy(latestShaderConfigFilePath, latestShaderConfigFilePath.resolveSibling(newName));
                        EuphoriaPatcher.log(0, "Successfully copied shader config file and renamed it!");
                    } catch (IOException e) {
                        EuphoriaPatcher.log(3,0, "Could not copy and rename the config file: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                EuphoriaPatcher.log(3,0, "Error reading shaderpacks directory: " + e.getMessage());
            }
        }
    }

    // Helper method to check if a file is a config file
    private static boolean isConfigFile(Path path, boolean containsPatchName) {
        String nameText = path.getFileName().toString();
        return containsPatchName ? nameText.matches(".*?Comp(?:lementary)?.*?(?:Reimagined|Unbound)?.*") && nameText.endsWith(".txt") && (nameText.contains(EuphoriaPatcher.PATCH_NAME) || nameText.contains(" + EP_")):
                nameText.matches(".*" + EuphoriaPatcher.BRAND_NAME + ".*(Reimagined|Unbound).*") && nameText.endsWith(".txt");
    }

    private static Path findShaderConfigFile(DirectoryStream<Path> textStream, boolean searchOldEuphoriaConfigs) {
        List<Path> euphoriaFiles = new ArrayList<>();
        List<Path> baseFiles = new ArrayList<>();

        for (Path potentialTextFile : textStream) {
            String name = potentialTextFile.getFileName().toString();
            if (name.endsWith(".txt")) {
                if (name.contains("EuphoriaPatches") || name.contains("EP_")) {
                    euphoriaFiles.add(potentialTextFile);
                } else {
                    baseFiles.add(potentialTextFile);
                }
            }
        }

        // First, try to find the latest Euphoria version (including dev versions)
        if (!euphoriaFiles.isEmpty()) {
            euphoriaFiles.sort((p1, p2) -> compareConfigFileVersions(getConfigFileVersion(p1), getConfigFileVersion(p2)));
            Path latestEuphoriaConfig = euphoriaFiles.get(euphoriaFiles.size() - 1);
            String latestName = latestEuphoriaConfig.getFileName().toString();

            if (searchOldEuphoriaConfigs) {
                if (!latestName.contains(EuphoriaPatcher.PATCH_VERSION) || latestName.contains("dev")) {
                    return latestEuphoriaConfig;
                }
            } else {
                return latestEuphoriaConfig;
            }
        }

        // If no suitable Euphoria version is found, fall back to base versions
        if (!baseFiles.isEmpty()) {
            baseFiles.sort((p1, p2) -> compareConfigFileVersions(getConfigFileVersion(p1), getConfigFileVersion(p2)));
            return baseFiles.get(baseFiles.size() - 1);
        }

        return null;
    }

    private static String getConfigFileVersion(Path path) {
        String name = path.getFileName().toString();
        Pattern pattern = Pattern.compile("(?:[a-zA-Z_]+)?[rdp]?(\\d+(?:\\.\\d+)*)(?:[rdp]\\d+)?(?: \\+ )?(?:EuphoriaPatches_|EP_)(\\d+(?:\\.\\d+)*(?:-dev\\d+)?)");
        Matcher matcher = pattern.matcher(name);
        if (matcher.find()) {
            String mainVersion = matcher.group(1);
            String patchVersion = matcher.group(2);
            if (patchVersion != null) {
                return patchVersion + "|" + mainVersion; // Euphoria Patches version first
            }
            return "0|" + mainVersion; // If no Euphoria Patches version, use 0
        }
        return "0|0"; // Default version if pattern doesn't match
    }

    private static int compareConfigFileVersions(String v1, String v2) {
        String[] fullVersion1 = v1.split("\\|");
        String[] fullVersion2 = v2.split("\\|");

        // Compare Euphoria Patches versions first
        int epCompare = compareVersionParts(fullVersion1[0], fullVersion2[0]);
        if (epCompare != 0) {
            return epCompare;
        }

        // If Euphoria Patches versions are the same, compare main versions
        return compareVersionParts(fullVersion1[1], fullVersion2[1]);
    }

    private static int compareVersionParts(String p1, String p2) {
        String[] parts1 = p1.split("[.\\-]");
        String[] parts2 = p2.split("[.\\-]");
        int length = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < length; i++) {
            String part1 = i < parts1.length ? parts1[i] : "0";
            String part2 = i < parts2.length ? parts2[i] : "0";

            boolean isP1Dev = part1.contains("dev");
            boolean isP2Dev = part2.contains("dev");

            if (isP1Dev && isP2Dev) {
                // Both are dev versions, compare them
                String[] devParts1 = part1.split("dev");
                String[] devParts2 = part2.split("dev");
                EuphoriaPatcher.log(0,devParts1[1] + "  " + devParts2[1]);
                int mainCompare = Integer.compare(Integer.parseInt(devParts1[1]), Integer.parseInt(devParts2[1]));
                if (mainCompare != 0) {
                    return mainCompare;
                }
                return Integer.parseInt(devParts1[1]);
            } else if (isP1Dev) {
                // p1 is a dev version, p2 is not. p2 is considered newer.
                return -1;
            } else if (isP2Dev) {
                // p2 is a dev version, p1 is not. p1 is considered newer.
                return 1;
            } else {
                // Neither is a dev version, compare as integers
                int compare = Integer.compare(Integer.parseInt(part1), Integer.parseInt(part2));
                if (compare != 0) {
                    return compare;
                }
            }
        }
        return 0;
    }
}
