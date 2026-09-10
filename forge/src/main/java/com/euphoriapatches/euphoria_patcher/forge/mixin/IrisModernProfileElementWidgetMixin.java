package com.euphoriapatches.euphoria_patcher.forge.mixin;

import com.euphoriapatches.euphoria_patcher.integration.iris.ProfileElementTracker;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

@Pseudo
@Mixin(targets = EuphoriaMixinPlugin.IRIS_PROFILE_ELEMENT_WIDGET_CLASS, remap = false)
public class IrisModernProfileElementWidgetMixin {

    @Inject(method = "<init>", at = @At("RETURN"), remap = false, require = 0)
    private void euphoriaPatcher$onConstructed(CallbackInfo ci) {
        euphoriaPatcher$debugLog("Injected constructor: ProfileElementWidget");
    }

    @Unique
    private static final ThreadLocal<Boolean> euphoriaPatcher$useSecondProfileSet = ThreadLocal.withInitial(() -> false);

    @Inject(
            method = "init",
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private void euphoriaPatcher$setInitContext(@Coerce Object screen, @Coerce Object navigation, CallbackInfo ci) {
        Object element = euphoriaPatcher$getElement();
        euphoriaPatcher$useSecondProfileSet.set(element != null && ProfileElementTracker.isSecondProfileSet(element));
    }

    @Inject(
            method = "init",
            at = @At("TAIL"),
            remap = false,
            require = 0
    )
    private void euphoriaPatcher$clearInitContext(@Coerce Object screen, @Coerce Object navigation, CallbackInfo ci) {
        euphoriaPatcher$useSecondProfileSet.remove();
    }

    @ModifyArg(
            method = "lambda$init$1(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;",
            remap = false,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/irisshaders/iris/gui/GuiUtil;translateOrDefault(Lnet/minecraft/network/chat/MutableComponent;Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;"
            ),
            index = 1,
            require = 0
    )
    private static String euphoriaPatcher$modifyProfileTranslationKeyNew(String translationKey) {
        return euphoriaPatcher$modifyProfileTranslationKeyInternal(translationKey);
    }

    @Unique
    private static String euphoriaPatcher$modifyProfileTranslationKeyInternal(String translationKey) {
        String name = translationKey.substring("profile.".length());
        return (euphoriaPatcher$useSecondProfileSet.get() ? "profile2." : "profile.") + name;
    }

    @Inject(method = "getCommentKey", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void euphoriaPatcher$modifyCommentKey(CallbackInfoReturnable<String> cir) {
        Object element = euphoriaPatcher$getElement();
        if (element != null && ProfileElementTracker.isSecondProfileSet(element)) {
            euphoriaPatcher$debugLog("Using profile2.comment for second profile element");
            cir.setReturnValue("profile2.comment");
        }
    }

    @Unique
    private Object euphoriaPatcher$getElement() {
        Class<?> current = getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField("element");
                field.setAccessible(true);
                return field.get(this);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            } catch (Exception e) {
                euphoriaPatcher$debugLog("Error reading element field: " + e.getMessage());
                return null;
            }
        }
        return null;
    }

    @Unique
    private static void euphoriaPatcher$debugLog(String message) {
        EuphoriaLogger.debugLog("[IrisModernProfileElementWidgetMixin] " + message);
    }
}
