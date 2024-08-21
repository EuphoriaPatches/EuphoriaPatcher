package mc.euphoria_patches.euphoria_patcher;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class Config {
    private static final Path CONFIG_PATH = EuphoriaPatcher.configDirectory.resolve("euphoria_patcher.properties");
    private static final Properties properties = new Properties();

    public static void createConfig() {
        try {
            Files.createFile(CONFIG_PATH);
            EuphoriaPatcher.log(0, "Successfully created config file");
        } catch (IOException e) {
            EuphoriaPatcher.log(3, "Error creating config file: " + e.getMessage());
        }
    }

    public static void writeConfig(String option, String value) {
        try {
            if (!Files.exists(CONFIG_PATH)) createConfig();
            loadProperties();

            if(!properties.containsKey(option)) { // If the option doesn't exist, add it
                properties.setProperty(option, value);
                EuphoriaPatcher.log(0, "Successfully wrote to config file: " + option + "=" + value);
            }
            try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
                String initialConfig = "This file stores configuration options for the Euphoria Patcher mod\n"
                        + "Thank you for using Euphoria Patches - SpacEagle17";
                properties.store(out, initialConfig);
            }
        } catch (IOException e) {
            EuphoriaPatcher.log(3, "Error writing to config file: " + e.getMessage());
        }
    }

    public static String readWriteConfig(String optionName, String defaultValue) {
        writeConfig(optionName, defaultValue);
        return properties.getProperty(optionName);
    }

    private static void loadProperties() {
        try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
            properties.load(in);
        } catch (IOException e) {
            EuphoriaPatcher.log(3, "Error loading properties: " + e.getMessage());
        }
    }
}
