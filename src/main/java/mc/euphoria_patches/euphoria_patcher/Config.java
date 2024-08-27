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
            try (FileWriter writer = new FileWriter(String.valueOf(CONFIG_PATH), true)) {
                writer.write("# This file stores configuration options for the Euphoria Patcher mod\n");
                writer.write("# Thank you for using Euphoria Patches - SpacEagle17\n");
            }
            EuphoriaPatcher.log(0, "Successfully created config file");
        } catch (IOException e) {
            EuphoriaPatcher.log(3,0, "Error creating config file: " + e.getMessage());
        }
    }

    public static void writeConfig(String option, String value, String description) {
        try {
            if (!Files.exists(CONFIG_PATH)) createConfig();
            loadProperties();
            if(!properties.containsKey(option)) {
                try (FileWriter writer = new FileWriter(String.valueOf(CONFIG_PATH), true)) {
                    writer.write("\n"); // Always write a newline before a new entry
                    if (description != null) {
                        String[] lines = description.split("\n");
                        for (String line : lines) {
                            writer.write("# " + line + "\n");
                        }
                    }
                    writer.write(option + "=" + value + "\n");
                    EuphoriaPatcher.log(0, "Successfully wrote to config file: " + option + "=" + value);
                }
            }
        } catch (IOException e) {
            EuphoriaPatcher.log(3,0, "Error writing to config file: " + e.getMessage());
        }
    }

    public static String readWriteConfig(String optionName, String defaultValue, String description) {
        writeConfig(optionName, defaultValue, description);
        return properties.getProperty(optionName, defaultValue); // Provide defaultValue if the property is missing
    }

    private static void loadProperties() {
        try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
            properties.load(in);
        } catch (IOException e) {
            EuphoriaPatcher.log(3,0, "Error loading properties: " + e.getMessage());
        }
    }
}
