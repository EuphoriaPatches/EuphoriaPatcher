package mc.euphoria_patches.euphoria_patcher.features;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ModifyPatchedShaderpacks {

    public static void modifyShadersProperties(Path patchedFile, boolean styleUnbound, boolean styleReimagined, String... regexAndReplacements) throws IOException {
        if (regexAndReplacements.length % 2 != 0) {
            throw new IllegalArgumentException("Regex and replacement pairs must be provided");
        }

        processShaderPacks(patchedFile, styleUnbound, styleReimagined, shaderPack -> {
            try {
                Path shadersPropertiesPath = shaderPack.resolve(EuphoriaPatcher.SHADERS_PROPERTIES_LOCATION);
                String content = new String(Files.readAllBytes(shadersPropertiesPath));
                String modifiedContent = applyReplacements(content, regexAndReplacements);
                Files.write(shadersPropertiesPath, modifiedContent.getBytes());
            } catch (IOException e) {
                throw new RuntimeException("Error processing shader properties", e);
            }
        });
    }

    public static void modifyLangFiles(Path patchedFile, boolean styleUnbound, boolean styleReimagined, String... regexAndReplacements) throws IOException {
        if (regexAndReplacements.length % 2 != 0) {
            throw new IllegalArgumentException("Regex and replacement pairs must be provided");
        }

        processShaderPacks(patchedFile, styleUnbound, styleReimagined, shaderPack -> {
            try {
                Path langDirectory = shaderPack.resolve(EuphoriaPatcher.LANG_LOCATION);
                try (DirectoryStream<Path> langFiles = Files.newDirectoryStream(langDirectory, "*.lang")) {
                    for (Path langFile : langFiles) {
                        String content = new String(Files.readAllBytes(langFile));
                        String modifiedContent = applyReplacements(content, regexAndReplacements);
                        Files.write(langFile, modifiedContent.getBytes());
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Error processing lang files", e);
            }
        });
    }

    private static void processShaderPacks(Path patchedFile, boolean styleUnbound, boolean styleReimagined, Consumer<Path> processor) {
        List<Path> shaderPacks = getShaderPacks(patchedFile, styleUnbound, styleReimagined);
        for (Path shaderPack : shaderPacks) {
            if (Files.exists(shaderPack)) {
                processor.accept(shaderPack);
            }
        }
    }

    private static String applyReplacements(String content, String... regexAndReplacements) {
        String modifiedContent = content;
        for (int i = 0; i < regexAndReplacements.length; i += 2) {
            String regex = regexAndReplacements[i];
            String replacement = regexAndReplacements[i + 1];
            modifiedContent = modifiedContent.replaceAll(regex, replacement);
        }
        return modifiedContent;
    }

    private static List<Path> getShaderPacks(Path patchedFile, boolean styleUnbound, boolean styleReimagined) {
        List<Path> shaderPacks = new ArrayList<>();
        shaderPacks.add(patchedFile);

        if (styleUnbound && styleReimagined) {
            Path otherStylePath = patchedFile.getFileName().toString().contains("Reimagined")
                    ? patchedFile.resolveSibling(patchedFile.getFileName().toString().replace("Reimagined", "Unbound"))
                    : patchedFile.resolveSibling(patchedFile.getFileName().toString().replace("Unbound", "Reimagined"));

            if (Files.exists(otherStylePath)) {
                shaderPacks.add(otherStylePath);
            }
        }
        return shaderPacks;
    }
}