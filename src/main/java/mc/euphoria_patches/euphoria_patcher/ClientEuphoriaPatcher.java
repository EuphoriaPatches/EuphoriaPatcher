package mc.euphoria_patches.euphoria_patcher;

import mc.euphoria_patches.euphoria_patcher.util.ServerCheck;
import net.neoforged.fml.common.Mod;

@Mod("euphoria_patcher")
public class ClientEuphoriaPatcher {

    public ClientEuphoriaPatcher() {
        if(ServerCheck.check()) return;
        new EuphoriaPatcher();
    }
}