package mc.euphoria_patches.euphoria_patcher.mixin;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import mc.euphoria_patches.euphoria_patcher.ServerCheck;
import net.minecraft.Bootstrap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Bootstrap.class)
public class EuphoriaPatcherMixin {
    @Inject(method = "initialize", at = @At("HEAD"))
    private static void onBootstrap(CallbackInfo ci) {
        if(ServerCheck.check()) return;
        new EuphoriaPatcher();
    }
}
