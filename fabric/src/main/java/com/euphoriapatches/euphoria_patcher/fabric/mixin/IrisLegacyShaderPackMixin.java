package com.euphoriapatches.euphoria_patcher.fabric.mixin;

import com.euphoriapatches.euphoria_patcher.integration.iris.DimensionWildcardMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Properties;
import java.util.function.BiConsumer;

/**
 * Mixin adding partial wildcard ('*') support to Iris's {@code dimension.properties} parsing.
 * <p>
 * Replaces the map created in {@code parseDimensionMap} with a {@code DimensionWildcardMap}.
 * Re-parses {@code Properties#forEach()} to populate the map directly, allowing lookups
 * in {@code getProgramSet()} and {@code getCurrentDimension()} to support wildcards transparently.
 */
@Pseudo
@Mixin(targets = EuphoriaMixinPlugin.LEGACY_SHADER_PACK_CLASS, remap = false)
public class IrisLegacyShaderPackMixin {

    @Inject(method = "parseDimensionMap", at = @At("HEAD"), remap = false, require = 0)
    private static void euphoriaPatcher$beginDimensionParsing(Properties properties, String keyPrefix, String fileName, CallbackInfoReturnable<Map<Object, Object>> cir) {
        DimensionWildcardMap.beginBuilding();
    }

    @ModifyArg(
        method = "parseDimensionMap",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/Properties;forEach(Ljava/util/function/BiConsumer;)V"
        ),
        index = 0,
        remap = false,
        require = 0
    )
    private static BiConsumer<Object, Object> euphoriaPatcher$captureDimensionEntries(BiConsumer<Object, Object> originalConsumer) {
        return (keyObject, valueObject) -> {
            originalConsumer.accept(keyObject, valueObject);

            DimensionWildcardMap builder = DimensionWildcardMap.currentBuilder();
            if (builder == null || !(keyObject instanceof String) || !(valueObject instanceof String)) {
                return;
            }

            String key = (String) keyObject;
            String value = (String) valueObject;

            if (!key.startsWith("dimension.")) {
                return;
            }

            key = key.substring("dimension.".length());

            for (String part : value.split("\\s+")) {
                if (part.equals("*")) {
                    builder.registerEntry("*", "*", key);
                    continue;
                }
                builder.registerEntry(part, key);
            }
        };
    }

    @Inject(method = "parseDimensionMap", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void euphoriaPatcher$finishDimensionParsing(Properties properties, String keyPrefix, String fileName, CallbackInfoReturnable<Map<Object, Object>> cir) {
        DimensionWildcardMap builder = DimensionWildcardMap.currentBuilder();
        if (builder != null) {
            cir.setReturnValue(builder);
        }

        DimensionWildcardMap.finishBuilding();
    }
}
