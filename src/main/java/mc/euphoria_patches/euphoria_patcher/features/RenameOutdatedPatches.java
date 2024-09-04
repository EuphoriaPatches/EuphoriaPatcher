package mc.euphoria_patches.euphoria_patcher.features;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class RenameOutdatedPatches {
    public static void rename() {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(EuphoriaPatcher.shaderpacks, RenameOutdatedPatches::isOutdatedPatch)) {
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

    private static boolean isOutdatedPatch(Path path) {
        String name = path.getFileName().toString();
        return name.contains(EuphoriaPatcher.PATCH_NAME) && !name.contains(EuphoriaPatcher.PATCH_VERSION);
    }
}
