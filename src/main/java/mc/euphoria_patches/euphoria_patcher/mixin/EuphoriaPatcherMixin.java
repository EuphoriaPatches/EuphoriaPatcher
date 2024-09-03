package mc.euphoria_patches.euphoria_patcher.mixin;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import mc.euphoria_patches.euphoria_patcher.util.ServerCheck;
import net.minecraft.server.Bootstrap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Bootstrap.class)
public class EuphoriaPatcherMixin {
    @Inject(method = "bootStrap", at = @At("HEAD"))
    private static void onBootstrap(CallbackInfo ci) {
        if(ServerCheck.check()) return;
        new EuphoriaPatcher();
    }
}
