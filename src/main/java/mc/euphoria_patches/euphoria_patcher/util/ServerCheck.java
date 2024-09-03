package mc.euphoria_patches.euphoria_patcher.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

public class ServerCheck {
    public static boolean check() {
        if(FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER){
            System.err.println("[EuphoriaPatcher] The Euphoria Patcher Mod should not be loaded on a server! Disabling...");
            return true;
        }
        return false;
    }

}
