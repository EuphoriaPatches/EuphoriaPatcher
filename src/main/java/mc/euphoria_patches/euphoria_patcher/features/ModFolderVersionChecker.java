package mc.euphoria_patches.euphoria_patcher.features;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModFolderVersionChecker {
    private static final String MOD_PREFIX = "EuphoriaPatcher-";
    private static final Pattern VERSION_PATTERN = Pattern.compile("EuphoriaPatcher-(\\d+\\.\\d+\\.\\d+)(?:-r.*)?-\\w+\\.jar");

    public static boolean existsNewerModInFolder() {
        String currentVersion = EuphoriaPatcher.PATCH_VERSION.substring(1); // Remove leading underscore
        File folder = EuphoriaPatcher.modDirectory.toFile();
        File[] modFiles = folder.listFiles((dir, name) -> name.startsWith(MOD_PREFIX) && name.endsWith(".jar"));

        if (modFiles == null || modFiles.length == 0) {
            return false;
        }

        Arrays.sort(modFiles, Comparator.comparing(File::getName).reversed());

        for (File modFile : modFiles) {
            Matcher matcher = VERSION_PATTERN.matcher(modFile.getName());
            if (matcher.find()) {
                String fileMainVersion = matcher.group(1);

                int mainComparison = compareVersions(fileMainVersion, currentVersion);

                if (mainComparison > 0) {
                    EuphoriaPatcher.log(0, "Found newer version: " + modFile.getName());
                    return true;
                } else if (mainComparison < 0) {
                    try {
                        Files.delete(modFile.toPath());
                        EuphoriaPatcher.log(0, "Successfully deleted older version: " + modFile.getName());
                    } catch (IOException e) {
                        EuphoriaPatcher.log(2, 0, "Failed to delete older version: " + modFile.getName() + " - " + e.getMessage());
                    }
                }
            }
        }
        return false;
    }

    private static int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int part1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int part2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            if (part1 < part2) return -1;
            if (part1 > part2) return 1;
        }
        return 0;
    }
}