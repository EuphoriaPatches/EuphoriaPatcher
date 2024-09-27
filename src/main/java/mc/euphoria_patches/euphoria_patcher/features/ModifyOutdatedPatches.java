package mc.euphoria_patches.euphoria_patcher.features;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class ModifyOutdatedPatches {
    public static void rename() {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(EuphoriaPatcher.shaderpacks, path -> isOutdatedPatch(path, true))) {
            for (Path potentialFile : stream) {
                String name = potentialFile.getFileName().toString();
                String newName = name
                        .replaceFirst("(.*) \\+", "§cOutdated§r $1 +")
                        .replace("EuphoriaPatches_", "EP_");
                Files.move(potentialFile, potentialFile.resolveSibling(newName));
                EuphoriaPatcher.log(0, "Successfully renamed outdated " + name + " shaderpack file!");
            }
        } catch (IOException e) {
            EuphoriaPatcher.log(3, 0, "Error reading shaderpacks directory: " + e.getMessage());
        }
    }

    public static void delete() {
        try (Stream<Path> stream = Files.list(EuphoriaPatcher.shaderpacks)) {
            stream.forEach(path -> {
                try {
                    if (isOutdatedPatch(path, false)) {
                        deleteRecursively(path);
                        EuphoriaPatcher.log(0, "Successfully deleted outdated " + path.getFileName() + " shaderpack file!");
                    }
                } catch (IOException e) {
                    EuphoriaPatcher.log(2, 0, "Error processing path: " + path + " - " + e.getMessage());
                }
            });
        } catch (IOException e) {
            EuphoriaPatcher.log(3, 0, "Error reading shaderpacks directory: " + e.getMessage());
        }
    }

    private static void deleteRecursively(Path path) throws IOException { // needed to delete folders
        if (Files.isDirectory(path)) {
            try (Stream<Path> entries = Files.list(path)) {
                entries.forEach(entry -> {
                    try {
                        deleteRecursively(entry);
                    } catch (IOException e) {
                        EuphoriaPatcher.log(2, 0, "Error deleting entry: " + entry + " - " + e.getMessage());
                    }
                });
            }
        }
        Files.delete(path);
    }

    private static boolean isOutdatedPatch(Path path, boolean renameFile) {
        String name = path.getFileName().toString();
        if (renameFile) {
            return name.contains(EuphoriaPatcher.PATCH_NAME) && !name.contains(EuphoriaPatcher.PATCH_VERSION);
        } else {
            return (name.contains(EuphoriaPatcher.PATCH_NAME) || name.matches(".*Outdated.*Complementary.* \\+ EP.*")) && !name.contains(EuphoriaPatcher.PATCH_VERSION);
        }
    }
}
