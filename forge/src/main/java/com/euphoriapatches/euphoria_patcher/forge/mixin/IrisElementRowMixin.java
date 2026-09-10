package com.euphoriapatches.euphoria_patcher.forge.mixin;

import org.spongepowered.asm.mixin.*;

import static com.euphoriapatches.euphoria_patcher.forge.mixin.EuphoriaMixinPlugin.IRIS_ELEMENT_ROW_CLASS;

@Pseudo
@Mixin(targets = IRIS_ELEMENT_ROW_CLASS, remap = false)
public class IrisElementRowMixin {

    @Shadow
    private int width;

    @Unique
    public int euphoriaPatcher$getWidth() {
        return this.width;
    }
}
