package mc.euphoria_patches.euphoria_patcher.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Implements ModMenu's API for configuration screen integration
 * This class is only loaded if ModMenu is present
 */
public class ModMenuApiImpl implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // Check if Cloth Config is installed
        if (!FabricLoader.getInstance().isModLoaded("cloth-config")) {
            EuphoriaPatcher.log(0, "ModMenu integration available, but Cloth Config is not installed");
            return parent -> parent; // Return same screen if Cloth Config isn't present
        }

        // Create the config screen if Cloth Config is present
        return parent -> {
            try {
                return ConfigScreenBuilder.buildScreen(parent);
            } catch (Exception e) {
                EuphoriaPatcher.log(3, 0, "Error creating config screen: " + e.getMessage());
                return parent;
            }
        };
    }
}
