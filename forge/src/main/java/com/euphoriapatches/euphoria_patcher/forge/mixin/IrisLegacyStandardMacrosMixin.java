package com.euphoriapatches.euphoria_patcher.forge.mixin;

import com.euphoriapatches.euphoria_patcher.integration.DefineHelper;
import com.euphoriapatches.euphoria_patcher.integration.Target;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.ArrayList;
import java.util.List;

@Pseudo
@Mixin(targets = EuphoriaMixinPlugin.LEGACY_IRIS_CLASS, remap = false)
public class IrisLegacyStandardMacrosMixin {

    @Shadow(remap = false)
    private static void define(List<?> defines, String key) {}

    @Shadow(remap = false)
    private static void define(List<?> defines, String key, String value) {}

    @Inject(
            method = "createStandardEnvironmentDefines",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/coderbot/iris/gl/shader/StandardMacros;define(Ljava/util/List;Ljava/lang/String;)V",
                    ordinal = 1,
                    remap = false
            ),
            locals = LocalCapture.CAPTURE_FAILHARD,
            require = 0,
            remap = false
    )
    private static void addEuphoriaDefine(CallbackInfoReturnable<?> cir, ArrayList<?> standardDefines) {
        DefineHelper.addEuphoriaDefines(
            standardDefines,
            Target.IRIS_LEGACY,
            IrisLegacyStandardMacrosMixin::define,
            (defines, keyValue) -> define(defines, keyValue[0], keyValue[1])
        );
    }
}
