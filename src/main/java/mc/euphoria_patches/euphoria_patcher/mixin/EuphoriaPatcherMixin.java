package mc.euphoria_patches.euphoria_patcher.mixin;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import net.minecraft.server.Bootstrap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Bootstrap.class)
public class EuphoriaPatcherMixin {
    @Inject(method = "bootStrap", at = @At("HEAD"))
    private static void onBootstrap(CallbackInfo ci) {
        if (FMLLoader.getDist() == Dist.DEDICATED_SERVER) {
            System.err.println("[EuphoriaPatcher] The Euphoria Patcher Mod should not be loaded on a server! Disabling...");
            return;
        }
        new EuphoriaPatcher();
    }
}
