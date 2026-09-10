package com.euphoriapatches.euphoria_patcher.forge.mixin;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.integration.ShaderLoader;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.services.ShaderDetector;
import com.euphoriapatches.euphoria_patcher.util.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

import static com.euphoriapatches.euphoria_patcher.forge.mixin.EuphoriaMixinPlugin.IRIS_HEADER_ENTRY_CLASS;

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
    private int euphoriaPatcher$capturedX;

    @Unique
    private int euphoriaPatcher$capturedY;

    @Unique
    private int euphoriaPatcher$capturedEntryWidth;

    @Unique
    private static final int euphoriaPatcher$MIN_SIDE_BUTTON_WIDTH = 42;

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

    @Inject(method = "<init>", at = @At("RETURN"), remap = false, require = 0)
    private void onConstructor(CallbackInfo ci) {
        try {
            EuphoriaPatcher instance = EuphoriaPatcher.getInstance();
            ShaderDetector shaderDetector = instance.getShaderDetector();
            Path currentShaderPackPath = ShaderLoader.getCurrentShaderpackPath();

            String buttonTextKey = euphoriaPatcher$BUTTON_SUPPORT_KEY;
            int buttonColor = 0; // 1=Red, 2=Green, 3=Blue, 0=Purple

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

    @Inject(method = "m_6311_", at = @At("HEAD"), remap = false, require = 0)
    private void captureRenderParams(@Coerce Object guiGraphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta, CallbackInfo ci) {
        this.euphoriaPatcher$capturedX = x;
        this.euphoriaPatcher$capturedY = y;
        this.euphoriaPatcher$capturedEntryWidth = entryWidth;
    }

    @Inject(method = "m_6311_", at = @At("TAIL"), remap = false, require = 0)
    private void onRenderContent(@Coerce Object guiGraphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta, CallbackInfo ci) {
        euphoriaPatcher$renderTooltipImpl(guiGraphics);
        euphoriaPatcher$recolorEPButtonWhileShift();
    }

    @Redirect(method = "m_6311_", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;m_280653_(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"), remap = false, require = 0)
    private void redirectDrawCenteredString(@Coerce Object guiGraphics, @Coerce Object font, @Coerce Object text, int x, int y, int color) {
        try {
            Object utilityButtons = ReflectionUtils.getFieldValue(this, "utilityButtons");
            if (utilityButtons != null) {
                int utilityButtonsWidth = (int) utilityButtons.getClass().getMethod("euphoriaPatcher$getWidth").invoke(utilityButtons);
                int minX = this.euphoriaPatcher$capturedX + 5;
                int minY = this.euphoriaPatcher$capturedY + 5;
                int maxX = ((this.euphoriaPatcher$capturedX + this.euphoriaPatcher$capturedEntryWidth) - 10) - utilityButtonsWidth;
                int maxY = this.euphoriaPatcher$capturedY + 15;
                euphoriaPatcher$renderScrollingString(guiGraphics, font, text, x, minX, minY, maxX, maxY, 0xFFFFFF);
            } else {
                // Fallback to original if utilityButtons is null
                Class<?> guiGraphicsClass = Class.forName("net.minecraft.client.gui.GuiGraphics");
                Class<?> fontClass = Class.forName("net.minecraft.client.gui.Font");
                Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
                guiGraphicsClass.getMethod("m_280653_", fontClass, componentClass, int.class, int.class, int.class).invoke(guiGraphics, font, text, x, y, color);
            }
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Error in redirectDrawCenteredString: " + e.getMessage());
            euphoriaPatcher$debugLog(EuphoriaLogger.getStackTrace(e));
            // Fallback to original on error
            try {
                Class<?> guiGraphicsClass = Class.forName("net.minecraft.client.gui.GuiGraphics");
                Class<?> fontClass = Class.forName("net.minecraft.client.gui.Font");
                Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
                guiGraphicsClass.getMethod("m_280653_", fontClass, componentClass, int.class, int.class, int.class).invoke(guiGraphics, font, text, x, y, color);
            } catch (Exception fallbackEx) {
                euphoriaPatcher$debugLog("Fallback also failed: " + fallbackEx.getMessage());
            }
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
            // Screen.hasShiftDown() -> m_96638_
            Class<?> screenClass = Class.forName("net.minecraft.client.gui.screens.Screen");
            java.lang.reflect.Method method = screenClass.getMethod("m_96638_");
            return (boolean) method.invoke(null);
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Error detecting shift key: " + e.getMessage());
        }
        return false;
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

            // Create button text with appropriate color
            Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
            Class<?> mutableComponentClass = Class.forName("net.minecraft.network.chat.MutableComponent");
            Class<?> chatFormattingClass = Class.forName("net.minecraft.ChatFormatting");

            String buttonTextKey;
            String colorFormatting;

            if (isUpdateAvailable) {
                // Update available: always green, ignore shift
                buttonTextKey = euphoriaPatcher$BUTTON_UPDATE_KEY;
                colorFormatting = "GREEN";
            } else if (shiftDown) {
                // No update, shift held: red
                buttonTextKey = euphoriaPatcher$BUTTON_SUPPORT_KEY;
                colorFormatting = "RED";
            } else {
                // No update, normal: purple
                buttonTextKey = euphoriaPatcher$BUTTON_SUPPORT_KEY;
                colorFormatting = "LIGHT_PURPLE";
            }

            Object colorFormattingEnum = chatFormattingClass.getField(colorFormatting).get(null);
            Object buttonText = componentClass.getMethod("m_237115_", String.class).invoke(null, buttonTextKey);
            buttonText = mutableComponentClass.getMethod("m_130940_", chatFormattingClass).invoke(buttonText, colorFormattingEnum);

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
    private void euphoriaPatcher$renderTooltipImpl(Object guiGraphics) {
        try {
            Object utilityButtons = ReflectionUtils.getFieldValue(this, "utilityButtons");
            Object resetButton = ReflectionUtils.getFieldValue(this, "resetButton");

            if (utilityButtons == null) {
                return;
            }

            euphoriaPatcher$renderTooltip(guiGraphics, utilityButtons, resetButton);
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Error rendering tooltip: " + e.getMessage());
            euphoriaPatcher$debugLog(EuphoriaLogger.getStackTrace(e));
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

            // Try to get Minecraft class - try both SRG and MCP names
            Class<?> minecraftClass = null;
            try {
                minecraftClass = Class.forName("C_3391_");
            } catch (ClassNotFoundException e) {
                minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            }
            Object minecraft = minecraftClass.getMethod("m_91087_").invoke(null);

            // Get Component and ChatFormatting classes
            Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
            Class<?> mutableComponentClass = Class.forName("net.minecraft.network.chat.MutableComponent");
            Class<?> chatFormattingClass = Class.forName("net.minecraft.ChatFormatting");
            Object buttonColorFormattingEnum = chatFormattingClass.getField(buttonColorFormatting).get(null);

            // Create button text: Component.translatable(key).withStyle(formatting)
            Object buttonText = componentClass.getMethod("m_237115_", String.class).invoke(null, buttonTextKey);
            buttonText = mutableComponentClass.getMethod("m_130940_", chatFormattingClass).invoke(buttonText, buttonColorFormattingEnum);

            Object supportEPButton = euphoriaPatcher$createIrisButton(buttonText, () -> euphoriaPatcher$handleSupportEPButtonClick(minecraft, screen));
            int width = euphoriaPatcher$measureButtonWidth(minecraft, buttonText);
            euphoriaPatcher$addButtonToRow(utilityButtons, supportEPButton, width);
            euphoriaPatcher$debugLog("Successfully added Iris EP button");
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Error in addEPIrisButton: " + e.getMessage());
            euphoriaPatcher$debugLog(EuphoriaLogger.getStackTrace(e));
        }
    }

    @Unique
    private int euphoriaPatcher$measureButtonWidth(Object minecraft, Object component) {
        try {
            Object font = minecraft.getClass().getField("f_91062_").get(minecraft);
            Class<?> formattedTextClass = Class.forName("net.minecraft.network.chat.FormattedText");
            int textWidth = (int) font.getClass().getMethod("m_92852_", formattedTextClass).invoke(font, component);
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

            // Get ConfirmLinkScreen class
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
            euphoriaPatcher$debugLog("Error handling button click: " + e.getMessage());
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
            // Get Util class
            Class<?> utilClass = Class.forName("net.minecraft.Util");
            // Call Util.getPlatform() -> m_137581_
            Object platform = utilClass.getMethod("m_137581_").invoke(null);

            // Try openUri method first (might be readable name even in SRG)
            try {
                platform.getClass().getMethod("openUri", String.class).invoke(platform, euphoriaPatcher$EuphoriaURL);
            } catch (NoSuchMethodException e) {
                // If that fails, try with m_137648_ and URI parameter
                Class<?> uriClass = Class.forName("java.net.URI");
                Object uri = uriClass.getConstructor(String.class).newInstance(euphoriaPatcher$EuphoriaURL);
                platform.getClass().getMethod("m_137648_", uriClass).invoke(platform, uri);
            }
            euphoriaPatcher$debugLog("Successfully opened URL");
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Failed to open URL: " + e.getMessage());
        }
    }

    @Unique
    private void euphoriaPatcher$setScreen(Object minecraft, Object screen) throws Exception {
        // Get Screen class
        Class<?> screenClass = Class.forName("net.minecraft.client.gui.screens.Screen");
        // Call Minecraft.setScreen(Screen) -> m_91152_
        minecraft.getClass().getMethod("m_91152_", screenClass).invoke(minecraft, screen);
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
        Object minecraft = euphoriaPatcher$getMinecraftInstance();
        Object font = euphoriaPatcher$getFont(minecraft);

        Object children = utilityButtons.getClass().getMethod("children").invoke(utilityButtons);
        Iterable<?> childrenIterable = (Iterable<?>) children;

        Class<?> textButtonElementClass = Class.forName("net.irisshaders.iris.gui.element.IrisElementRow$TextButtonElement");

        for (Object child : childrenIterable) {
            if (textButtonElementClass.isInstance(child) && child != resetButton) {
                // Check if hovered: isHovered() -> isHovered
                boolean isHovered = (boolean) child.getClass().getMethod("isHovered").invoke(child);

                // Check if focused: isFocused() -> m_93696_
                boolean isFocused = false;
                try {
                    isFocused = (boolean) child.getClass().getMethod("m_93696_").invoke(child);
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
                    Class<?> mutableComponentClass = Class.forName("net.minecraft.network.chat.MutableComponent");
                    Class<?> chatFormattingClass = Class.forName("net.minecraft.ChatFormatting");
                    Object colorFormattingEnum = chatFormattingClass.getField(colorFormatting).get(null);
                    Object tooltipText = componentClass.getMethod("m_237115_", String.class).invoke(null, tooltipKey);
                    tooltipText = mutableComponentClass.getMethod("m_130940_", chatFormattingClass).invoke(tooltipText, colorFormattingEnum);

                    if (appendRemoveHint) {
                        Object hintText = componentClass.getMethod("m_237115_", String.class).invoke(null, euphoriaPatcher$TOOLTIP_REMOVE_HINT_KEY);
                        hintText = mutableComponentClass.getMethod("m_130940_", chatFormattingClass).invoke(hintText, colorFormattingEnum);
                        Object space = componentClass.getMethod("m_237113_", String.class).invoke(null, " ");
                        mutableComponentClass.getMethod("m_7220_", componentClass).invoke(tooltipText, space);
                        mutableComponentClass.getMethod("m_7220_", componentClass).invoke(tooltipText, hintText);
                    }

                    // Get ScreenRectangle: getRectangle() -> m_264198_
                    Object rect = child.getClass().getMethod("m_264198_").invoke(child);

                    // Get ScreenDirection.RIGHT: RIGHT
                    Class<?> screenDirectionClass = Class.forName("net.minecraft.client.gui.navigation.ScreenDirection");
                    Object rightDirection = screenDirectionClass.getField("RIGHT").get(null);

                    // Get right bound: getBoundInDirection(ScreenDirection) -> m_264095_
                    int rightBound = (int) rect.getClass().getMethod("m_264095_", screenDirectionClass).invoke(rect, rightDirection);

                    // Get y position: position() -> then y()
                    Object position = rect.getClass().getMethod("f_263846_").invoke(rect);
                    int yPos = (int) position.getClass().getMethod("f_263694_").invoke(position);

                    // Font.width(FormattedText) -> m_92852_
                    Class<?> formattedTextClass = Class.forName("net.minecraft.network.chat.FormattedText");
                    int textWidth = (int) font.getClass().getMethod("m_92852_", formattedTextClass).invoke(font, tooltipText);
                    int tooltipX = rightBound - (textWidth + 10);
                    int tooltipY = yPos - 16;

                    final int finalTooltipX = tooltipX;
                    final int finalTooltipY = tooltipY;
                    final Object finalFont = font;
                    final Object finalGuiGraphics = guiGraphics;
                    final Object finalTooltipText = tooltipText;

                    Class<?> shaderPackScreenClass = Class.forName("net.irisshaders.iris.gui.screen.ShaderPackScreen");
                    Object renderQueue = shaderPackScreenClass.getField("TOP_LAYER_RENDER_QUEUE").get(null);
                    Class<?> guiUtilClass = Class.forName("net.irisshaders.iris.gui.GuiUtil");
                    Class<?> guiGraphicsClass = Class.forName("net.minecraft.client.gui.GuiGraphics");

                    Runnable renderTask = () -> {
                        try {
                            guiUtilClass.getMethod("drawTextPanel", finalFont.getClass(), guiGraphicsClass, componentClass, int.class, int.class)
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
    private Object euphoriaPatcher$getMinecraftInstance() throws Exception {
        Class<?> minecraftClass;
        try {
            minecraftClass = Class.forName("C_3391_");
        } catch (ClassNotFoundException e) {
            minecraftClass = Class.forName("net.minecraft.client.Minecraft");
        }
        return minecraftClass.getMethod("m_91087_").invoke(null);
    }

    @Unique
    private Object euphoriaPatcher$getFont(Object minecraft) throws Exception {
        // Minecraft.font -> f_91062_
        return minecraft.getClass().getField("f_91062_").get(minecraft);
    }

    @Unique
    private boolean euphoriaPatcher$isUpdateAvailable(ShaderDetector shaderDetector, Path currentShaderPackPath) {
        return UpdateChecker.shouldUserUpdate() &&
                VersionComparator.isNewerVersion(UpdateChecker.getNewModVersion(), shaderDetector.readVersionFromPackJson(currentShaderPackPath));
    }

    @Unique
    private static void euphoriaPatcher$renderScrollingString(Object guiGraphics, Object font, Object text, int centerX, int minX, int minY, int maxX, int maxY, int color) {
        try {
            // Get text width: font.width(text)
            int textWidth = (int) font.getClass().getMethod("m_92852_", Class.forName("net.minecraft.network.chat.FormattedText")).invoke(font, text);
            int yPos = (minY + maxY - 9) / 2 + 1;
            int availableWidth = maxX - minX;

            Class<?> guiGraphicsClass = Class.forName("net.minecraft.client.gui.GuiGraphics");
            Class<?> fontClass = Class.forName("net.minecraft.client.gui.Font");
            Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");

            if (textWidth > availableWidth) {
                // Text is too wide, scroll it with smooth sine wave animation
                int scrollRange = textWidth - availableWidth;

                // Get current time: Util.getMillis()
                Class<?> utilClass = Class.forName("net.minecraft.Util");
                long currentTimeMillis = (long) utilClass.getMethod("m_137550_").invoke(null);
                double currentTime = (double) currentTimeMillis / 1000.0;

                double scrollDuration = Math.max((double) scrollRange * 0.5, 3.0);
                double scrollProgress = Math.sin(Math.PI / 2 * Math.cos(Math.PI * 2 * currentTime / scrollDuration)) / 2.0 + 0.5;

                // Mth.lerp()
                Class<?> mthClass = Class.forName("net.minecraft.util.Mth");
                double scrollOffset = (double) mthClass.getMethod("m_14139_", double.class, double.class, double.class).invoke(null, scrollProgress, 0.0, (double) scrollRange);

                // guiGraphics.enableScissor()
                guiGraphicsClass.getMethod("m_280588_", int.class, int.class, int.class, int.class).invoke(guiGraphics, minX, minY, maxX, maxY);

                // guiGraphics.drawString(font, text, x, y, color)
                guiGraphicsClass.getMethod("m_280430_", fontClass, componentClass, int.class, int.class, int.class)
                    .invoke(guiGraphics, font, text, minX - (int) scrollOffset, yPos, color);

                // guiGraphics.disableScissor()
                guiGraphicsClass.getMethod("m_280618_").invoke(guiGraphics);
            } else {
                // Text fits, center it at the specified centerX position using drawCenteredString: m_280653_(Font, Component, int, int, int) -> void
                guiGraphicsClass.getMethod("m_280653_", fontClass, componentClass, int.class, int.class, int.class)
                    .invoke(guiGraphics, font, text, centerX, yPos, color);
            }
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Error in renderScrollingString: " + e.getMessage());
            euphoriaPatcher$debugLog(EuphoriaLogger.getStackTrace(e));
        }
    }

    @Unique
    private static void euphoriaPatcher$debugLog(String message) {
        EuphoriaLogger.debugLog("[IrisHeaderEntryMixin] " + message);
    }
}
