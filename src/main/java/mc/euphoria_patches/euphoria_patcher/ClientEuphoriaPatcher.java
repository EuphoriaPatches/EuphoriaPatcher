package mc.euphoria_patches.euphoria_patcher;

import net.fabricmc.api.ModInitializer;

public class ClientEuphoriaPatcher implements ModInitializer {
    @Override
    public void onInitialize() {
        if(ServerCheck.check()) return;
        new EuphoriaPatcher();
    }
}
