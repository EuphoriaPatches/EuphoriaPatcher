package mc.euphoria_patches.euphoria_patcher.features;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import mc.euphoria_patches.euphoria_patcher.util.UpdateChecker;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ModifyUpdateInfo {
    public static void modifyShaderPackAndLangFiles(Path patchedFile, boolean styleUnbound, boolean styleReimagined) throws IOException {
        List<Path> shaderPacks = new ArrayList<>();

        if (styleUnbound && styleReimagined) {
            // Both styles are present, so we need to handle both
            shaderPacks.add(patchedFile);

            // Check for the other style and add if it exists
            Path otherStylePath;
            if (patchedFile.getFileName().toString().contains("Reimagined")) {
                otherStylePath = patchedFile.resolveSibling(patchedFile.getFileName().toString().replace("Reimagined", "Unbound"));
            } else {
                otherStylePath = patchedFile.resolveSibling(patchedFile.getFileName().toString().replace("Unbound", "Reimagined"));
            }

            if (Files.exists(otherStylePath)) {
                shaderPacks.add(otherStylePath);
            }
        } else {
            // Only one style is present, so we just handle the patchedFile
            shaderPacks.add(patchedFile);
        }

        for (Path shaderPack : shaderPacks) {
            if (Files.exists(shaderPack)) {
                // Modify shaders.properties file
                Path shadersPropertiesPath = shaderPack.resolve(EuphoriaPatcher.SHADERS_PROPERTIES_LOCATION);
                String shadersPropertiesContent = new String(Files.readAllBytes(shadersPropertiesPath));
                String modifiedShadersPropertiesContent = shadersPropertiesContent.replaceFirst("screen=<empty> <empty>", "screen=info19 info20");
                Files.write(shadersPropertiesPath, modifiedShadersPropertiesContent.getBytes());

                // Modify language files
                Path langDirectory = shaderPack.resolve(EuphoriaPatcher.LANG_LOCATION);
                try (DirectoryStream<Path> langFiles = Files.newDirectoryStream(langDirectory, "*.lang")) {
                    for (Path langFile : langFiles) {
                        String langContent = new String(Files.readAllBytes(langFile));

                        // Replace NEW_MOD_VERSION with NEW_MOD_VERSION
                        String modifiedLangContent = langContent.replaceAll("value\\.info19\\.0=.*", "value.info19.0=" + UpdateChecker.NEW_MOD_VERSION);
                        modifiedLangContent = modifiedLangContent.replaceAll("value\\.info20\\.0=.*", "value.info20.0=" + EuphoriaPatcher.MOD_VERSION);

                        Files.write(langFile, modifiedLangContent.getBytes());
                    }
                }
            }
        }
    }
}
