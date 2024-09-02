package mc.euphoria_patches.euphoria_patcher.mixin;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.Bootstrap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Bootstrap.class)
public class EuphoriaPatcherMixin {
    @Inject(method = "initialize", at = @At("HEAD"))
    private static void onBootstrap(CallbackInfo ci) {
        if(FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER){
            System.err.println("[EuphoriaPatcher] The Euphoria Patcher Mod should not be loaded on a server! Disabling...");
            return;
        }
        new EuphoriaPatcher();
    }
}
