package mc.euphoria_patches.euphoria_patcher.mixin;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import net.minecraft.client.User;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(User.class)
public class EuphoriaPatcherMixin {
    @Inject(at = @At("RETURN"), method = "<init>")
    private void init(CallbackInfo info) {
        new EuphoriaPatcher();
    }
}
