package mc.euphoria_patches.euphoria_patcher.util;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public class ServerCheck {
    public static boolean check() {
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            System.err.println("The Euphoria Patcher Mod should not be loaded on a server! Disabling...");
            return true;
        }
        return false;
    }
}
