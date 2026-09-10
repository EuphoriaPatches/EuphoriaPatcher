package com.euphoriapatches.euphoria_patcher.forge.mixin;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.integration.iris.ProfileElementTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

@Pseudo
@Mixin(targets = EuphoriaMixinPlugin.IRIS_OPTION_MENU_ELEMENT_CLASS, remap = false)
public class IrisModernOptionMenuElementMixin {

    @Unique
    private static final String euphoriaPatcher$ELEMENT_PROFILE2 = "<profile2>";

    @Inject(
        method = {
                    "create(Ljava/lang/String;Lnet/irisshaders/iris/shaderpack/option/menu/OptionMenuContainer;Lnet/irisshaders/iris/shaderpack/properties/ShaderProperties;Lnet/irisshaders/iris/shaderpack/option/ShaderPackOptions;)Lnet/irisshaders/iris/shaderpack/option/menu/OptionMenuElement;",
                    "create"
            },
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void euphoriaPatcher$onCreate(
        String elementString,
        @Coerce Object container,
        @Coerce Object shaderProperties,
        @Coerce Object shaderPackOptions,
        CallbackInfoReturnable<Object> cir
    ) {
        if ("<profile>".equals(elementString) || euphoriaPatcher$ELEMENT_PROFILE2.equals(elementString)) {
            euphoriaPatcher$debugLog("create intercepted for elementString=" + elementString);
        }

        if (!euphoriaPatcher$ELEMENT_PROFILE2.equals(elementString)) {
            return;
        }

        try {
            Object profiles2 = container.getClass().getMethod("euphoriaPatcher$getProfiles2").invoke(container);
            if (profiles2 == null) {
                euphoriaPatcher$debugLog("<profile2> -> container cache is null, building ProfileSet from ShaderProperties");
                Object profiles2Tree = shaderProperties.getClass().getMethod("euphoriaPatcher$getProfiles2").invoke(shaderProperties);
                int treeSize = (int) profiles2Tree.getClass().getMethod("size").invoke(profiles2Tree);
                euphoriaPatcher$debugLog("<profile2> -> ShaderProperties tree size=" + treeSize);

                Object optionSet = shaderPackOptions.getClass().getMethod("getOptionSet").invoke(shaderPackOptions);
                Class<?> profileSetClass = Class.forName("net.irisshaders.iris.shaderpack.option.ProfileSet");
                Method fromTreeMethod = null;
                for (Method method : profileSetClass.getMethods()) {
                    if (method.getName().equals("fromTree") && method.getParameterCount() == 2) {
                        fromTreeMethod = method;
                        break;
                    }
                }

                if (fromTreeMethod == null) {
                    euphoriaPatcher$debugLog("<profile2> -> could not find ProfileSet.fromTree method");
                    cir.setReturnValue(null);
                    return;
                }

                profiles2 = fromTreeMethod.invoke(null, profiles2Tree, optionSet);

                try {
                    container.getClass().getMethod("euphoriaPatcher$setProfiles2", Object.class).invoke(container, profiles2);
                } catch (Exception ignored) {
                    euphoriaPatcher$debugLog("<profile2> -> unable to cache profiles2 on container, continuing without cache");
                }
            }

            int profiles2Size = (int) profiles2.getClass().getMethod("size").invoke(profiles2);
            euphoriaPatcher$debugLog("<profile2> -> profiles2 size=" + profiles2Size);
            if (profiles2Size <= 0) {
                cir.setReturnValue(null);
                return;
            }

            Object optionSet = shaderPackOptions.getClass().getMethod("getOptionSet").invoke(shaderPackOptions);
            Object optionValues = shaderPackOptions.getClass().getMethod("getOptionValues").invoke(shaderPackOptions);

            Class<?> optionMenuProfileElementClass = Class.forName("net.irisshaders.iris.shaderpack.option.menu.OptionMenuProfileElement");

            Object profileElement = null;
            for (Constructor<?> ctor : optionMenuProfileElementClass.getConstructors()) {
                if (ctor.getParameterCount() == 3) {
                    profileElement = ctor.newInstance(profiles2, optionSet, optionValues);
                    break;
                }
            }

            if (profileElement == null) {
                euphoriaPatcher$debugLog("Could not find OptionMenuProfileElement constructor");
                cir.setReturnValue(null);
                return;
            }

            euphoriaPatcher$debugLog("<profile2> -> created OptionMenuProfileElement successfully");
            cir.setReturnValue(ProfileElementTracker.markSecondProfileSet(profileElement));
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Error handling <profile2> element: " + e.getMessage());
            cir.setReturnValue(null);
        }
    }

    @Unique
    private static void euphoriaPatcher$debugLog(String message) {
        EuphoriaLogger.debugLog("[IrisModernOptionMenuElementMixin] " + message);
    }
}
