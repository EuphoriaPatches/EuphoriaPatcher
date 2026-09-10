package com.euphoriapatches.euphoria_patcher.fabric.mixin;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

@Pseudo
@Mixin(targets = EuphoriaMixinPlugin.IRIS_SHADER_PROPERTIES_CLASS, remap = false)
public class IrisModernShaderPropertiesMixin {

    @Unique
    private final Map<String, List<String>> euphoriaPatcher$profiles2 = new LinkedHashMap<>();

    @ModifyArg(
        method = {
            "<init>(Ljava/lang/String;Lnet/irisshaders/iris/shaderpack/option/ShaderPackOptions;Ljava/lang/Iterable;)V",
            "<init>"
        },
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/Properties;forEach(Ljava/util/function/BiConsumer;)V",
            ordinal = 1
        ),
        index = 0,
        remap = false,
        require = 0
    )
    private BiConsumer<Object, Object> euphoriaPatcher$registerProfile2(BiConsumer<Object, Object> originalConsumer) {
        return (keyObject, valueObject) -> {
            originalConsumer.accept(keyObject, valueObject);

            if (!(keyObject instanceof String) || !(valueObject instanceof String)) {
                return;
            }

            String key = (String) keyObject;
            String value = (String) valueObject;
            if (!key.startsWith("profile2.")) {
                return;
            }

            String strippedKey = key.substring("profile2.".length());
            euphoriaPatcher$profiles2.put(strippedKey, Arrays.asList(value.split(" +")));
        };
    }

    @Unique
    public Map<String, List<String>> euphoriaPatcher$getProfiles2() {
        return euphoriaPatcher$profiles2;
    }

    @Unique
    private void euphoriaPatcher$debugLog(String message) {
        EuphoriaLogger.debugLog("[IrisModernShaderPropertiesMixin] " + message);
    }
}

