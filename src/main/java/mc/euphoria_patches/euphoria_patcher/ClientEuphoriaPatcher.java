package mc.euphoria_patches.euphoria_patcher;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod("euphoria_patcher")
public class ClientEuphoriaPatcher {

    public ClientEuphoriaPatcher() {
        if(FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            System.err.println("The Euphoria Patcher Mod should not be loaded on a server! Disabling...");
            return;
        }
        new EuphoriaPatcher();
    }
}
