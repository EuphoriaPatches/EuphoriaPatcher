package com.euphoriapatches.euphoria_patcher.forge.mixin;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets = EuphoriaMixinPlugin.IRIS_OPTION_MENU_CONTAINER_CLASS, remap = false)
public class IrisModernOptionMenuContainerMixin {

    @Unique
    private Object euphoriaPatcher$profiles2;

    @Unique
    public Object euphoriaPatcher$getProfiles2() {
        return euphoriaPatcher$profiles2;
    }

    @Unique
    public void euphoriaPatcher$setProfiles2(Object profiles2) {
        this.euphoriaPatcher$profiles2 = profiles2;
    }

    @Unique
    private void euphoriaPatcher$debugLog(String message) {
        EuphoriaLogger.debugLog("[IrisModernOptionMenuContainerMixin] " + message);
    }
}
