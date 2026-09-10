package com.euphoriapatches.euphoria_patcher.fabric.mixin;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.integration.ShaderLoader;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.services.ShaderDetector;
import com.euphoriapatches.euphoria_patcher.util.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

import static com.euphoriapatches.euphoria_patcher.fabric.mixin.EuphoriaMixinPlugin.IRIS_HEADER_ENTRY_CLASS;

@Pseudo
@Mixin(targets = IRIS_HEADER_ENTRY_CLASS, remap = false)
public class IrisHeaderEntryMixin {

    @Unique
    private static String euphoriaPatcher$EuphoriaURL = "https://euphoriapatches.com/support";

    @Unique
    private long euphoriaPatcher$buttonHoverStartTime = 0;

    @Unique
    private boolean euphoriaPatcher$isCurrentlyHovering = false;

    @Unique
    private static boolean euphoriaPatcher$hasShownExtendedTooltip = false;

    @Unique
    private static final String euphoriaPatcher$BUTTON_SUPPORT_KEY = "euphoria_patcher.button.support";
    @Unique
    private static final String euphoriaPatcher$BUTTON_UPDATE_KEY = "euphoria_patcher.button.update";
    @Unique
    private static final String euphoriaPatcher$TOOLTIP_SUPPORT_KEY = "euphoria_patcher.tooltip.support";
    @Unique
    private static final String euphoriaPatcher$TOOLTIP_UPDATE_KEY = "euphoria_patcher.tooltip.update";
    @Unique
    private static final String euphoriaPatcher$TOOLTIP_REMOVE_KEY = "euphoria_patcher.tooltip.remove";
    @Unique
    private static final String euphoriaPatcher$TOOLTIP_REMOVE_HINT_KEY = "euphoria_patcher.tooltip.remove.hint";

    @Unique
    private static final int euphoriaPatcher$MIN_SIDE_BUTTON_WIDTH = 42;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false, require = 0)
    private void onConstructor(CallbackInfo ci) {
        try {
            EuphoriaPatcher instance = EuphoriaPatcher.getInstance();
            ShaderDetector shaderDetector = instance.getShaderDetector();
            Path currentShaderPackPath = ShaderLoader.getCurrentShaderpackPath();

            String buttonTextKey = euphoriaPatcher$BUTTON_SUPPORT_KEY;
            int buttonColor = 0; // 1=Red, 2=Green, 3=Blue , 0=Purple

            boolean isUpdateAvailable = euphoriaPatcher$isUpdateAvailable(shaderDetector, currentShaderPackPath);
            if (isUpdateAvailable) {
                buttonTextKey = euphoriaPatcher$BUTTON_UPDATE_KEY;
                buttonColor = 2; // Green
                euphoriaPatcher$EuphoriaURL = EuphoriaPatcher.EP_DOWNLOAD_URL;
            } else {
                euphoriaPatcher$EuphoriaURL = "https://euphoriapatches.com/support";
            }

            if (euphoriaPatcher$isShaderOptionsScreenOpen() && shaderDetector.isEuphoriaPatchesShader(currentShaderPackPath)) {
                UserPersistentData.recordFirstEPOptionsTimestampIfAbsent();
            }

            if (euphoriaPatcher$shouldShowEPButton())
                euphoriaPatcher$addEPIrisButton(buttonTextKey, buttonColor);
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Failed to add Iris EP button: " + e.getMessage());
            euphoriaPatcher$debugLog(EuphoriaLogger.getStackTrace(e));
        }
    }

    @Inject(method = {"renderContent", "extractContent"}, at = @At("TAIL"), remap = false, require = 0)
    private void onRenderContent(@Coerce Object guiGraphics, int mouseX, int mouseY, boolean bl, float tickDelta, CallbackInfo ci) {
        try {
            Object utilityButtons = ReflectionUtils.getFieldValue(this, "utilityButtons");
            Object resetButton = ReflectionUtils.getFieldValue(this, "resetButton");

            if (utilityButtons == null) {
                return;
            }

            euphoriaPatcher$renderTooltip(guiGraphics, utilityButtons, resetButton);
            euphoriaPatcher$recolorEPButtonWhileShift();
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Error rendering tooltip: " + e.getMessage());
            euphoriaPatcher$debugLog(EuphoriaLogger.getStackTrace(e));
        }
    }

    @Unique
    private boolean euphoriaPatcher$shouldShowEPButton() {
        EuphoriaPatcher instance = EuphoriaPatcher.getInstance();
        ShaderDetector shaderDetector = instance.getShaderDetector();
        Path currentShaderPackPath = ShaderLoader.getCurrentShaderpackPath();

        boolean isUpdateAvailable = euphoriaPatcher$isUpdateAvailable(shaderDetector, currentShaderPackPath);

        UserPersistentData.PersistentShaderData data = UserPersistentData.load();
        boolean userAllowsSupportButton = false;
        if (data.supportEPButtonVisible == null || data.supportEPButtonVisible) {
            euphoriaPatcher$debugLog("User has not dismissed EP support button");
            userAllowsSupportButton = true;
        } else {
            euphoriaPatcher$debugLog("User has dismissed EP support button - not adding button");
        }
        boolean shouldShowSupportButton = shaderDetector.noDevVersionsInstalled() && userAllowsSupportButton;

        boolean secondCondition = shouldShowSupportButton || isUpdateAvailable;

        return shaderDetector.isEuphoriaPatchesShader(currentShaderPackPath) && secondCondition;
    }

    @Unique
    private boolean euphoriaPatcher$isShaderOptionsScreenOpen() {
        try {
            Object screen = ReflectionUtils.getFieldValue(this, "screen");
            if (screen == null) {
                return false;
            }
            Object optionMenuOpen = ReflectionUtils.getFieldValue(screen, "optionMenuOpen");
            if (!(optionMenuOpen instanceof Boolean)) {
                euphoriaPatcher$debugLog("Could not read optionMenuOpen from ShaderPackScreen");
                return false;
            }
            return (Boolean) optionMenuOpen;
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Error checking if shader options screen is open: " + e.getMessage());
            return false;
        }
    }

    @Unique
    private boolean euphoriaPatcher$isShiftDown() {
        try {
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            Object minecraft = minecraftClass.getMethod("getInstance").invoke(null);
            return (boolean) minecraft.getClass().getMethod("hasShiftDown").invoke(minecraft);
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Error detecting shift key: " + e.getMessage());
            return false;
        }
    }

    @Unique
    private void euphoriaPatcher$recolorEPButtonWhileShift() {
        try {
            Object utilityButtons = ReflectionUtils.getFieldValue(this, "utilityButtons");
            Object resetButton = ReflectionUtils.getFieldValue(this, "resetButton");

            if (utilityButtons == null) {
                return;
            }

            // Check if update is available
            EuphoriaPatcher instance = EuphoriaPatcher.getInstance();
            ShaderDetector shaderDetector = instance.getShaderDetector();
            Path currentShaderPackPath = ShaderLoader.getCurrentShaderpackPath();
            boolean isUpdateAvailable = euphoriaPatcher$isUpdateAvailable(shaderDetector, currentShaderPackPath);

            boolean shiftDown = euphoriaPatcher$hasShownExtendedTooltip && euphoriaPatcher$isShiftDown();

            String buttonTextKey;
            String buttonColorFormatting;
            if (isUpdateAvailable) {
                // Update available: always green, ignore shift
                buttonTextKey = euphoriaPatcher$BUTTON_UPDATE_KEY;
                buttonColorFormatting = "GREEN";
            } else if (shiftDown) {
                // No update, shift held: red
                buttonTextKey = euphoriaPatcher$BUTTON_SUPPORT_KEY;
                buttonColorFormatting = "RED";
            } else {
                // No update, normal: purple
                buttonTextKey = euphoriaPatcher$BUTTON_SUPPORT_KEY;
                buttonColorFormatting = "LIGHT_PURPLE";
            }

            Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
            Class<?> chatFormattingClass = Class.forName("net.minecraft.ChatFormatting");
            Object buttonColorFormattingEnum = chatFormattingClass.getField(buttonColorFormatting).get(null);
            Object buttonText = componentClass.getMethod("translatable", String.class).invoke(null, buttonTextKey);
            buttonText = buttonText.getClass().getMethod("withStyle", chatFormattingClass).invoke(buttonText, buttonColorFormattingEnum);

            Object children = utilityButtons.getClass().getMethod("children").invoke(utilityButtons);
            Iterable<?> childrenIterable = (Iterable<?>) children;
            Class<?> textButtonElementClass = Class.forName("net.irisshaders.iris.gui.element.IrisElementRow$TextButtonElement");

            for (Object child : childrenIterable) {
                if (textButtonElementClass.isInstance(child) && child != resetButton) {
                    java.lang.reflect.Field textField = child.getClass().getField("text");
                    textField.setAccessible(true);
                    textField.set(child, buttonText);
                    break;
                }
            }
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Error recoloring EP button: " + e.getMessage());
        }
    }

    @Unique
    private void euphoriaPatcher$addEPIrisButton(String buttonTextKey, int buttonColor) {
        try {
            Object utilityButtons = ReflectionUtils.getFieldValue(this, "utilityButtons");
            Object screen = ReflectionUtils.getFieldValue(this, "screen");

            if (utilityButtons == null) {
                euphoriaPatcher$debugLog("utilityButtons field not found");
                return;
            }

            String buttonColorFormatting;
            switch (buttonColor) {
                case 1:
                    buttonColorFormatting = "RED";
                    break;
                case 2:
                    buttonColorFormatting = "GREEN";
                    break;
                case 3:
                    buttonColorFormatting = "BLUE";
                    break;
                default:
                    buttonColorFormatting = "LIGHT_PURPLE";
                    break;
            }
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            Object minecraft = minecraftClass.getMethod("getInstance").invoke(null);

            Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
            Class<?> chatFormattingClass = Class.forName("net.minecraft.ChatFormatting");
            Object buttonColorFormattingEnum = chatFormattingClass.getField(buttonColorFormatting).get(null);

            Object buttonText = componentClass.getMethod("translatable", String.class).invoke(null, buttonTextKey);
            buttonText = buttonText.getClass().getMethod("withStyle", chatFormattingClass).invoke(buttonText, buttonColorFormattingEnum);

            Object supportEPButton = euphoriaPatcher$createIrisButton(buttonText, () -> euphoriaPatcher$handleSupportEPButtonClick(minecraft, screen));
            int width = euphoriaPatcher$measureButtonWidth(minecraft, buttonText);
            euphoriaPatcher$addButtonToRow(utilityButtons, supportEPButton, width);
            euphoriaPatcher$debugLog("Successfully added Iris EP button (Modern)");
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Error in addEPIrisButton: " + e.getMessage());
            euphoriaPatcher$debugLog(EuphoriaLogger.getStackTrace(e));
        }
    }

    @Unique
    private int euphoriaPatcher$measureButtonWidth(Object minecraft, Object component) {
        try {
            Object font = minecraft.getClass().getField("font").get(minecraft);
            Class<?> formattedTextClass = Class.forName("net.minecraft.network.chat.FormattedText");
            int textWidth = (int) font.getClass().getMethod("width", formattedTextClass).invoke(font, component);
            return Math.max(euphoriaPatcher$MIN_SIDE_BUTTON_WIDTH, textWidth + 8);
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Error measuring button width, falling back to default: " + e.getMessage());
            return 66;
        }
    }

    @Unique
    private Object euphoriaPatcher$createIrisButton(Object buttonText, Runnable action) throws Exception {
        Class<?> textButtonElementClass = Class.forName("net.irisshaders.iris.gui.element.IrisElementRow$TextButtonElement");

        for (java.lang.reflect.Constructor<?> ctor : textButtonElementClass.getConstructors()) {
            Class<?>[] paramTypes = ctor.getParameterTypes();
            if (paramTypes.length == 2) {
                Class<?> actionClass = paramTypes[1];
                Object actionProxy = java.lang.reflect.Proxy.newProxyInstance(
                    actionClass.getClassLoader(),
                    new Class<?>[] { actionClass },
                    (proxy, method, args) -> {
                        action.run();
                        return true;
                    }
                );
                return ctor.newInstance(buttonText, actionProxy);
            }
        }
        throw new Exception("Could not find TextButtonElement constructor");
    }

    @Unique
    private void euphoriaPatcher$addButtonToRow(Object utilityButtons, Object button, int width) throws Exception {
        for (java.lang.reflect.Method method : utilityButtons.getClass().getMethods()) {
            if (method.getName().equals("add") && method.getParameterCount() == 2) {
                Class<?>[] paramTypes = method.getParameterTypes();
                if (paramTypes[1] == int.class || paramTypes[1] == Integer.class) {
                    method.invoke(utilityButtons, button, width);
                    return;
                }
            }
        }
        throw new Exception("Could not find add method on utilityButtons");
    }

    @Unique
    private void euphoriaPatcher$handleSupportEPButtonClick(Object minecraft, Object screen) {
        try {
            euphoriaPatcher$playButtonClickSound();

            // Check if shift is held
            if (euphoriaPatcher$hasShownExtendedTooltip && euphoriaPatcher$isShiftDown()) {
                euphoriaPatcher$debugLog("Pressed Shift while clicking EP button - removing button");
                UserPersistentData.save(UserPersistentData.SaveData.of(UserPersistentData.DataField.SUPPORT_EP_BUTTON, false));
                euphoriaPatcher$refreshOptionListAfterDismiss();
                return;
            }

            Class<?> confirmLinkScreenClass = Class.forName("net.minecraft.client.gui.screens.ConfirmLinkScreen");

            java.lang.reflect.Constructor<?> constructor = null;
            Class<?> consumerClass = null;

            for (java.lang.reflect.Constructor<?> ctor : confirmLinkScreenClass.getConstructors()) {
                Class<?>[] paramTypes = ctor.getParameterTypes();
                if (paramTypes.length == 3 && paramTypes[1] == String.class && paramTypes[2] == boolean.class) {
                    constructor = ctor;
                    consumerClass = paramTypes[0];
                    break;
                }
            }

            if (constructor == null || consumerClass == null) {
                euphoriaPatcher$debugLog("Could not find ConfirmLinkScreen constructor");
                return;
            }

            Object booleanConsumer = java.lang.reflect.Proxy.newProxyInstance(
                consumerClass.getClassLoader(),
                new Class<?>[] { consumerClass },
                (proxy, method, args) -> {
                    if (args != null && args.length > 0 && args[0] instanceof Boolean) {
                        boolean confirmed = (boolean) args[0];
                        if (confirmed) {
                            euphoriaPatcher$openUrl();
                        }
                        euphoriaPatcher$setScreen(minecraft, screen);
                    }
                    return null;
                }
            );

            Object confirmScreen = constructor.newInstance(booleanConsumer, euphoriaPatcher$EuphoriaURL, true);
            euphoriaPatcher$setScreen(minecraft, confirmScreen);
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Error handling button click (Modern): " + e.getMessage());
            euphoriaPatcher$debugLog(EuphoriaLogger.getStackTrace(e));
        }
    }

    @Unique
    private void euphoriaPatcher$playButtonClickSound() {
        try {
            Class<?> guiUtilClass = Class.forName("net.irisshaders.iris.gui.GuiUtil");
            guiUtilClass.getMethod("playButtonClickSound").invoke(null);
        } catch (Exception e) {
            // GuiUtil might not exist in all versions, skip sound
        }
    }

    @Unique
    private void euphoriaPatcher$openUrl() {
        try {
            Class<?> utilClass;
            try {
                utilClass = Class.forName("net.minecraft.Util");
            } catch (ClassNotFoundException e) {
                utilClass = Class.forName("net.minecraft.util.Util");
            }
            Object platform = utilClass.getMethod("getPlatform").invoke(null);
            platform.getClass().getMethod("openUri", String.class).invoke(platform, euphoriaPatcher$EuphoriaURL);
            euphoriaPatcher$debugLog("Successfully opened URL (Modern)");
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Failed to open URL (Modern): " + e.getMessage());
        }
    }

    @Unique
    private void euphoriaPatcher$setScreen(Object minecraft, Object screen) throws Exception {
        Class<?> screenClass;
        try {
            screenClass = Class.forName("net.minecraft.client.gui.screens.Screen");
        } catch (ClassNotFoundException e) {
            screenClass = Class.forName("net.minecraft.client.gui.screen.Screen");
        }

        // Try modern path first: minecraft.gui.setScreen(screen)
        try {
            Object gui = minecraft.getClass().getField("gui").get(minecraft);
            gui.getClass().getMethod("setScreen", screenClass).invoke(gui, screen);
            euphoriaPatcher$debugLog("setScreen succeeded via minecraft.gui.setScreen");
            return;
        } catch (NoSuchFieldException | NoSuchMethodException ignored) {}

        // Fallback: legacy direct minecraft.setScreen(screen)
        try {
            minecraft.getClass().getMethod("setScreen", screenClass).invoke(minecraft, screen);
            euphoriaPatcher$debugLog("setScreen succeeded via minecraft.setScreen");
            return;
        } catch (NoSuchMethodException ignored) {}

        throw new Exception("Could not find a working setScreen method on Minecraft or Minecraft.gui");
    }

    @Unique
    private void euphoriaPatcher$refreshOptionListAfterDismiss() {
        Object optionList = euphoriaPatcher$resolveOptionListFromScreen();
        if (optionList == null) {
            euphoriaPatcher$debugLog("Could not resolve ShaderPackOptionList from header entry");
            return;
        }

        boolean rebuilt = euphoriaPatcher$invokeIfPresent(optionList, "rebuild");
        boolean refreshed = euphoriaPatcher$invokeIfPresent(optionList, "refresh");
        if (!rebuilt && !refreshed) {
            euphoriaPatcher$debugLog("No rebuild/refresh method found on ShaderPackOptionList");
        }
    }

    @Unique
    private Object euphoriaPatcher$resolveOptionListFromScreen() {
        try {
            Object screen = ReflectionUtils.getFieldValue(this, "screen");
            if (screen == null) {
                return null;
            }

            Class<?> optionListClass = Class.forName("net.irisshaders.iris.gui.element.ShaderPackOptionList");
            return euphoriaPatcher$findFieldValueByType(screen, optionListClass);
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Error resolving ShaderPackOptionList from screen: " + e.getMessage());
        }

        return null;
    }

    @Unique
    private Object euphoriaPatcher$findFieldValueByType(Object owner, Class<?> targetType) {
        Class<?> clazz = owner.getClass();
        while (clazz != null) {
            for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(owner);
                    if (value != null && targetType.isInstance(value)) {
                        return value;
                    }
                } catch (Exception ignored) {
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    @Unique
    private boolean euphoriaPatcher$invokeIfPresent(Object target, String methodName) {
        try {
            target.getClass().getMethod(methodName).invoke(target);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Failed to invoke " + methodName + " on ShaderPackOptionList: " + e.getMessage());
            return false;
        }
    }

    @Unique
    private void euphoriaPatcher$renderTooltip(Object guiGraphics, Object utilityButtons, Object resetButton) throws Exception {
        // Get font: Minecraft.getInstance().font
        Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
        Object minecraft = minecraftClass.getMethod("getInstance").invoke(null);
        Object font = minecraft.getClass().getField("font").get(minecraft);

        // Get children from utilityButtons
        Object children = utilityButtons.getClass().getMethod("children").invoke(utilityButtons);
        Iterable<?> childrenIterable = (Iterable<?>) children;

        Class<?> textButtonElementClass = Class.forName("net.irisshaders.iris.gui.element.IrisElementRow$TextButtonElement");

        for (Object child : childrenIterable) {
            if (textButtonElementClass.isInstance(child) && child != resetButton) {
                // Check if hovered
                boolean isHovered = (boolean) child.getClass().getMethod("isHovered").invoke(child);

                // Check if focused
                boolean isFocused = false;
                try {
                    isFocused = (boolean) child.getClass().getMethod("isFocused").invoke(child);
                } catch (Exception e) {
                    // isFocused might not exist, ignore
                }

                if (isHovered || isFocused) {
                    // Track hover time and set permanent flag when threshold is reached
                    if (!euphoriaPatcher$hasShownExtendedTooltip) {
                        long currentTime = System.currentTimeMillis();
                        if (!euphoriaPatcher$isCurrentlyHovering) {
                            euphoriaPatcher$buttonHoverStartTime = currentTime;
                            euphoriaPatcher$isCurrentlyHovering = true;
                        }
                        long hoverDuration = currentTime - euphoriaPatcher$buttonHoverStartTime;
                        if (hoverDuration >= 1715) {
                            euphoriaPatcher$hasShownExtendedTooltip = true;
                        }
                    }

                    // Get tooltip text and color based on shift state and update availability
                    boolean shiftDown = euphoriaPatcher$hasShownExtendedTooltip && euphoriaPatcher$isShiftDown();
                    EuphoriaPatcher instance = EuphoriaPatcher.getInstance();
                    ShaderDetector shaderDetector = instance.getShaderDetector();
                    Path currentShaderPackPath = ShaderLoader.getCurrentShaderpackPath();
                    boolean isUpdateAvailable = euphoriaPatcher$isUpdateAvailable(shaderDetector, currentShaderPackPath);

                    String tooltipKey;
                    String colorFormatting;
                    boolean appendRemoveHint = false;

                    if (isUpdateAvailable) { // Has higher priority over shift key
                        tooltipKey = euphoriaPatcher$TOOLTIP_UPDATE_KEY;
                        colorFormatting = "GREEN";
                    } else if (shiftDown) {
                        tooltipKey = euphoriaPatcher$TOOLTIP_REMOVE_KEY;
                        colorFormatting = "RED";
                    } else {
                        tooltipKey = euphoriaPatcher$TOOLTIP_SUPPORT_KEY;
                        appendRemoveHint = euphoriaPatcher$hasShownExtendedTooltip;
                        colorFormatting = "LIGHT_PURPLE";
                    }

                    // Create tooltip text: Component.translatable(key).withStyle(color)
                    Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
                    Class<?> formattedTextClass = Class.forName("net.minecraft.network.chat.FormattedText");
                    Class<?> chatFormattingClass = Class.forName("net.minecraft.ChatFormatting");
                    Object colorFormattingEnum = chatFormattingClass.getField(colorFormatting).get(null);
                    Object tooltipText = componentClass.getMethod("translatable", String.class).invoke(null, tooltipKey);
                    tooltipText = tooltipText.getClass().getMethod("withStyle", chatFormattingClass).invoke(tooltipText, colorFormattingEnum);

                    if (appendRemoveHint) {
                        Object hintText = componentClass.getMethod("translatable", String.class).invoke(null, euphoriaPatcher$TOOLTIP_REMOVE_HINT_KEY);
                        hintText = hintText.getClass().getMethod("withStyle", chatFormattingClass).invoke(hintText, colorFormattingEnum);
                        Object space = componentClass.getMethod("literal", String.class).invoke(null, " ");
                        tooltipText.getClass().getMethod("append", componentClass).invoke(tooltipText, space);
                        tooltipText.getClass().getMethod("append", componentClass).invoke(tooltipText, hintText);
                    }

                    // Get rectangle and calculate tooltip position
                    Object rect = child.getClass().getMethod("getRectangle").invoke(child);
                    Class<?> screenDirectionClass = Class.forName("net.minecraft.client.gui.navigation.ScreenDirection");
                    Object rightDirection = screenDirectionClass.getField("RIGHT").get(null);
                    int rightBound = (int) rect.getClass().getMethod("getBoundInDirection", screenDirectionClass).invoke(rect, rightDirection);

                    Object position = rect.getClass().getMethod("position").invoke(rect);
                    int yPos = (int) position.getClass().getMethod("y").invoke(position);

                    int textWidth = (int) font.getClass().getMethod("width", formattedTextClass).invoke(font, tooltipText);
                    int tooltipX = rightBound - (textWidth + 10);
                    int tooltipY = yPos - 16;

                    // Make final copies for lambda
                    final Object finalFont = font;
                    final Object finalGuiGraphics = guiGraphics;
                    final Object finalTooltipText = tooltipText;
                    final int finalTooltipX = tooltipX;
                    final int finalTooltipY = tooltipY;

                    // Add to render queue
                    Class<?> shaderPackScreenClass = Class.forName("net.irisshaders.iris.gui.screen.ShaderPackScreen");
                    Object renderQueue = shaderPackScreenClass.getField("TOP_LAYER_RENDER_QUEUE").get(null);
                    Class<?> guiUtilClass = Class.forName("net.irisshaders.iris.gui.GuiUtil");

                    Runnable renderTask = () -> {
                        try {
                            guiUtilClass.getMethod("drawTextPanel", finalFont.getClass(), finalGuiGraphics.getClass(), componentClass, int.class, int.class)
                                .invoke(null, finalFont, finalGuiGraphics, finalTooltipText, finalTooltipX, finalTooltipY);
                        } catch (Exception e) {
                            euphoriaPatcher$debugLog("Error in render task: " + e.getMessage());
                        }
                    };
                    renderQueue.getClass().getMethod("add", Object.class).invoke(renderQueue, renderTask);
                } else {
                    // Reset hover tracking (but keep extended tooltip flag)
                    euphoriaPatcher$isCurrentlyHovering = false;
                }
                break;
            }
        }
    }

    @Unique
    private boolean euphoriaPatcher$isUpdateAvailable(ShaderDetector shaderDetector, Path currentShaderPackPath) {
        return UpdateChecker.shouldUserUpdate() &&
                VersionComparator.isNewerVersion(UpdateChecker.getNewModVersion(), shaderDetector.readVersionFromPackJson(currentShaderPackPath));
    }

    @Unique
    private static void euphoriaPatcher$debugLog(String message) {
        EuphoriaLogger.debugLog("[IrisHeaderEntryMixin] " + message);
    }
}
