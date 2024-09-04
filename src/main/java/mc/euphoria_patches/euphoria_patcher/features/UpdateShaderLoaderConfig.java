package mc.euphoria_patches.euphoria_patcher.features;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class UpdateShaderLoaderConfig {
    private static Path getShaderLoaderPath(){
        Path shaderLoaderConfig = EuphoriaPatcher.configDirectory.resolve("iris.properties");
        if(!Files.exists(shaderLoaderConfig)) shaderLoaderConfig = EuphoriaPatcher.configDirectory.resolve("oculus.properties");
        if(!Files.exists(shaderLoaderConfig)) shaderLoaderConfig = EuphoriaPatcher.shaderpacks.getParent().resolve("optionsshaders.txt");
        if (!Files.exists(shaderLoaderConfig)) shaderLoaderConfig = null;
        return shaderLoaderConfig;
    }

    public static void updateShaderLoaderConfig(boolean styleUnbound, boolean styleReimagined) {
        Path shaderLoaderConfig = getShaderLoaderPath();
        if (shaderLoaderConfig == null) {
            EuphoriaPatcher.log(0, "No shader loader config found");
            return;
        }

        String shaderLoaderName = shaderLoaderConfig.toString().contains("iris") ? "iris.properties" : shaderLoaderConfig.toString().contains("oculus") ? "oculus.properties" : "OptiFine's optionsshaders.txt";

        File fileToBeModified = shaderLoaderConfig.toFile();
        StringBuilder oldContent = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new FileReader(fileToBeModified))) {
            String line;
            while ((line = reader.readLine()) != null) {
                oldContent.append(line).append(System.lineSeparator());
            }

            if (oldContent.toString().contains(EuphoriaPatcher.PATCH_NAME) && !oldContent.toString().contains(EuphoriaPatcher.PATCH_VERSION)) {
                String newContent = setNewShaderLoaderSelectedPackName(oldContent, styleUnbound, styleReimagined);

                try (FileWriter writer = new FileWriter(fileToBeModified)) {
                    writer.write(newContent);
                } catch (IOException e) {
                    EuphoriaPatcher.log(3,0, "Error writing to " + shaderLoaderName + " config file: " + e.getMessage());
                }
                EuphoriaPatcher.log(0, "Successfully applied new version in " + shaderLoaderName + " config file!");
            }
        } catch (IOException e) {
            EuphoriaPatcher.log(3,0, "Error reading or writing to " + shaderLoaderName + " config file: " + e.getMessage());
        }
    }

    private static String setNewShaderLoaderSelectedPackName(StringBuilder oldContent, boolean styleUnbound, boolean styleReimagined) {
        String style = styleUnbound ? "Unbound" : "Reimagined";
        if (styleUnbound && styleReimagined) { // Both styles installed
            style = oldContent.toString().contains(EuphoriaPatcher.PATCH_NAME) && !oldContent.toString().contains(EuphoriaPatcher.PATCH_VERSION) && oldContent.toString().contains("Unbound") ? "Unbound" : "Reimagined";
        }
        String newName = EuphoriaPatcher.BRAND_NAME + style + EuphoriaPatcher.VERSION + " + " + EuphoriaPatcher.PATCH_NAME + EuphoriaPatcher.PATCH_VERSION;
        return oldContent.toString().replaceAll("shaderPack=.*", "shaderPack=" + newName);
    }
}
