package mc.euphoria_patches.euphoria_patcher.compat;

import mc.euphoria_patches.euphoria_patcher.util.EuphoriaLogger;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import mc.euphoria_patches.euphoria_patcher.util.Config;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import java.lang.reflect.Field;
import java.util.Map;

/**
 * Builds a Cloth Config screen for our mod settings
 * This class dynamically generates UI from configStuff() metadata
 */
public class ConfigScreenBuilder {
    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[ConfigScreenBuilder] " + message);
    }

    public static Screen buildScreen(Screen parent) {
        // Make sure configs have been loaded at least once
        ensureConfigLoaded();
        
        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Text.of("Euphoria Patches Configuration"))
            .setSavingRunnable(() -> {
                debugLog("Config saved via GUI");
            });
        
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        ConfigCategory general = builder.getOrCreateCategory(Text.of("General"));
        
        // Get metadata collected during configStuff()
        Map<String, Config.ConfigOptionMetadata> metadata = Config.getConfigMetadata();
        
        // Dynamically add all boolean config options
        try {
            Class<?> euphoriaPatcherClass = EuphoriaPatcher.class;
            for (Field field : euphoriaPatcherClass.getDeclaredFields()) {
                if (field.getType() == boolean.class && field.getName().startsWith("do")) {
                    field.setAccessible(true);
                    String fieldName = field.getName();
                    
                    // Get metadata for this field
                    Config.ConfigOptionMetadata meta = metadata.get(fieldName);
                    if (meta == null) continue;
                    
                    // Get current value
                    boolean currentValue = field.getBoolean(null);
                    boolean defaultValue = Boolean.parseBoolean(meta.defaultValue);
                    
                    // Clean description (remove "Default = true/false")
                    String description = meta.description;
                    if (description.contains("\nDefault = ")) {
                        description = description.substring(0, description.lastIndexOf("\nDefault = "));
                    }
                    
                    general.addEntry(entryBuilder.startBooleanToggle(
                            Text.of(meta.displayName), 
                            currentValue)
                        .setDefaultValue(defaultValue)
                        .setTooltip(Text.of(description))
                        .setSaveConsumer(newValue -> {
                            try {
                                // Update the field
                                field.setBoolean(null, newValue);
                                // Update the config file
                                updateConfig(fieldName, newValue);
                            } catch (Exception e) {
                                EuphoriaPatcher.log(3, 0, "Error updating config from GUI: " + e.getMessage());
                            }
                        })
                        .build());
                }
            }
        } catch (Exception e) {
            EuphoriaPatcher.log(3, 0, "Error building config screen: " + e.getMessage());
        }
        
        return builder.build();
    }
    
    /**
     * Ensures config has been loaded at least once
     */
    private static void ensureConfigLoaded() {
        if (Config.getConfigMetadata().isEmpty()) {
            EuphoriaPatcher instance = EuphoriaPatcher.getInstance();
            if (instance != null) {
                instance.configStuff();
            }
        }
    }
    
    /**
     * Updates the config file with the new value
     */
    private static void updateConfig(String key, boolean value) {
        try {
            Config.writeConfig(key, String.valueOf(value), null);
        } catch (Exception e) {
            EuphoriaPatcher.log(3, 0, "Error updating config from GUI: " + e.getMessage());
        }
    }
}