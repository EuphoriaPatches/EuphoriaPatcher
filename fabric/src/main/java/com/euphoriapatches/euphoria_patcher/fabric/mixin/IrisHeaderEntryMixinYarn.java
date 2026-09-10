package com.euphoriapatches.euphoria_patcher.fabric.mixin;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.integration.ShaderLoader;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.services.ShaderDetector;
import com.euphoriapatches.euphoria_patcher.util.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.navigation.NavigationDirection;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

import static com.euphoriapatches.euphoria_patcher.fabric.mixin.EuphoriaMixinPlugin.IRIS_HEADER_ENTRY_CLASS;

@Pseudo
@Mixin(targets = IRIS_HEADER_ENTRY_CLASS, remap = false)
public class IrisHeaderEntryMixinYarn {

    @Unique
    private static String euphoriaPatcher$EuphoriaURL = "https://euphoriapatches.com/support";

    @Unique
    private long euphoriaPatcher$buttonHoverStartTime = 0;

    @Unique
    private boolean euphoriaPatcher$isCurrentlyHovering = false;

    @Unique
    private static boolean euphoriaPatcher$hasShownExtendedTooltip = false;

    @Unique private int euphoriaPatcher$capturedX;
    @Unique private int euphoriaPatcher$capturedY;
    @Unique private int euphoriaPatcher$capturedEntryWidth;

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

    @Inject(method = "method_25343(Lnet/minecraft/class_332;IIIIIIIZF)V", at = @At("TAIL"), remap = false, require = 0)
    private void onRenderContent10Params(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta, CallbackInfo ci) {
        euphoriaPatcher$renderTooltipImpl(context);
        euphoriaPatcher$recolorEPButtonWhileShift();
    }

    @Inject(method = "method_25343(Lnet/minecraft/class_332;IIZF)V", at = @At("TAIL"), remap = false, require = 0)
    private void onRenderContent5Params(DrawContext context, int mouseX, int mouseY, boolean hovered, float tickDelta, CallbackInfo ci) {
        euphoriaPatcher$renderTooltipImpl(context);
        euphoriaPatcher$recolorEPButtonWhileShift();
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
    private void euphoriaPatcher$renderTooltipImpl(DrawContext guiGraphics) {
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
    private boolean euphoriaPatcher$isShiftDown() {
        try {
            // Try MinecraftClient.isShiftPressed() first (class_310.method_74187)
            MinecraftClient minecraft = MinecraftClient.getInstance();
            try {
                Class<?> minecraftClass = Class.forName("net.minecraft.class_310");
                java.lang.reflect.Method method = minecraftClass.getMethod("method_74187");
                return (boolean) method.invoke(minecraft);
            } catch (Exception e) {
                // Fallback to Screen.hasShiftDown() (class_437.method_25442)
                Object screen = ReflectionUtils.getFieldValue(this, "screen");
                if (screen != null) {
                    Class<?> screenClass = Class.forName("net.minecraft.class_437");
                    java.lang.reflect.Method method = screenClass.getMethod("method_25442");
                    return (boolean) method.invoke(screen);
                }
            }
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

            MutableText buttonText;
            if (isUpdateAvailable) {
                // Update available: always green, ignore shift
                buttonText = Text.translatable(euphoriaPatcher$BUTTON_UPDATE_KEY).formatted(Formatting.GREEN);
            } else if (shiftDown) {
                // No update, shift held: red
                buttonText = Text.translatable(euphoriaPatcher$BUTTON_SUPPORT_KEY).formatted(Formatting.RED);
            } else {
                // No update, normal: purple
                buttonText = Text.translatable(euphoriaPatcher$BUTTON_SUPPORT_KEY).formatted(Formatting.LIGHT_PURPLE);
            }

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

            Formatting buttonColorFormatting;
            switch (buttonColor) {
                case 1:
                    buttonColorFormatting = Formatting.RED;
                    break;
                case 2:
                    buttonColorFormatting = Formatting.GREEN;
                    break;
                case 3:
                    buttonColorFormatting = Formatting.BLUE;
                    break;
                default:
                    buttonColorFormatting = Formatting.LIGHT_PURPLE;
                    break;
            }
            MutableText buttonText = Text.translatable(buttonTextKey).formatted(buttonColorFormatting);
            MinecraftClient minecraft = MinecraftClient.getInstance();
            Object supportEPButton = euphoriaPatcher$createIrisButton(buttonText, () -> euphoriaPatcher$handleSupportEPButtonClick(minecraft, screen));
            int width = Math.max(euphoriaPatcher$MIN_SIDE_BUTTON_WIDTH, minecraft.textRenderer.getWidth(buttonText) + 8);
            euphoriaPatcher$addButtonToRow(utilityButtons, supportEPButton, width);
            euphoriaPatcher$debugLog("Successfully added Iris EP button (Yarn)");
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Error in addEPIrisButton: " + e.getMessage());
            euphoriaPatcher$debugLog(EuphoriaLogger.getStackTrace(e));
        }
    }

    @Unique
    private void euphoriaPatcher$renderTooltip(DrawContext guiGraphics, Object utilityButtons, Object resetButton) throws Exception {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;

        Object children = utilityButtons.getClass().getMethod("children").invoke(utilityButtons);
        Iterable<?> childrenIterable = (Iterable<?>) children;

        Class<?> textButtonElementClass = Class.forName("net.irisshaders.iris.gui.element.IrisElementRow$TextButtonElement");

        for (Object child : childrenIterable) {
            if (textButtonElementClass.isInstance(child) && child != resetButton) {
                Element elem = (Element) child;
                boolean isHovered = (boolean) child.getClass().getMethod("isHovered").invoke(child);
                boolean isFocused = elem.isFocused();

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
                    Formatting colorFormatting;
                    boolean appendRemoveHint = false;

                    if (isUpdateAvailable) { // Has higher priority over shift key
                        tooltipKey = euphoriaPatcher$TOOLTIP_UPDATE_KEY;
                        colorFormatting = Formatting.GREEN;
                    } else if (shiftDown) {
                        tooltipKey = euphoriaPatcher$TOOLTIP_REMOVE_KEY;
                        colorFormatting = Formatting.RED;
                    } else {
                        tooltipKey = euphoriaPatcher$TOOLTIP_SUPPORT_KEY;
                        appendRemoveHint = euphoriaPatcher$hasShownExtendedTooltip;
                        colorFormatting = Formatting.LIGHT_PURPLE;
                    }

                    MutableText tooltipText = Text.translatable(tooltipKey).formatted(colorFormatting);
                    if (appendRemoveHint) {
                        tooltipText.append(Text.literal(" "));
                        tooltipText.append(Text.translatable(euphoriaPatcher$TOOLTIP_REMOVE_HINT_KEY).formatted(colorFormatting));
                    }
                    // Get ScreenRect from Iris button
                    Object rect = child.getClass().getMethod("method_48202").invoke(child);
                    int rightBound = (int) rect.getClass().getMethod("method_48255", NavigationDirection.class).invoke(rect, NavigationDirection.RIGHT);

                    // Get y position
                    Object position = rect.getClass().getMethod("comp_1195").invoke(rect);
                    int yPos = (int) position.getClass().getMethod("comp_1194").invoke(position);
                    int textWidth = font.getWidth(tooltipText);
                    int tooltipX = rightBound - (textWidth + 10);
                    int tooltipY = yPos - 16;

                    final int finalTooltipX = tooltipX;
                    final int finalTooltipY = tooltipY;

                    Class<?> shaderPackScreenClass = Class.forName("net.irisshaders.iris.gui.screen.ShaderPackScreen");
                    Object renderQueue = shaderPackScreenClass.getField("TOP_LAYER_RENDER_QUEUE").get(null);
                    Class<?> guiUtilClass = Class.forName("net.irisshaders.iris.gui.GuiUtil");

                    Runnable renderTask = () -> {
                        try {
                            guiUtilClass.getMethod("drawTextPanel", TextRenderer.class, DrawContext.class, Text.class, int.class, int.class)
                                .invoke(null, font, guiGraphics, tooltipText, finalTooltipX, finalTooltipY);
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
    private void euphoriaPatcher$handleSupportEPButtonClick(MinecraftClient minecraft, Object screen) {
        try {
            euphoriaPatcher$playButtonClickSound();

            // Check if shift is held
            if (euphoriaPatcher$hasShownExtendedTooltip && euphoriaPatcher$isShiftDown()) {
                euphoriaPatcher$debugLog("Pressed Shift while clicking EP button - removing button");
                UserPersistentData.save(UserPersistentData.SaveData.of(UserPersistentData.DataField.SUPPORT_EP_BUTTON, false));
                euphoriaPatcher$refreshOptionListAfterDismiss();
                return;
            }

            // Normal click: Open support link
            ConfirmLinkScreen confirmScreen = new ConfirmLinkScreen(
                confirmed -> {
                    if (confirmed) {
                        euphoriaPatcher$openUrl();
                    }
                    minecraft.setScreen((Screen) screen);
                },
                euphoriaPatcher$EuphoriaURL,
                true
            );
            minecraft.setScreen(confirmScreen);
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
            Util.getOperatingSystem().open(euphoriaPatcher$EuphoriaURL);
            euphoriaPatcher$debugLog("Successfully opened URL");
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Failed to open URL: " + e.getMessage());
        }
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
    private boolean euphoriaPatcher$isUpdateAvailable(ShaderDetector shaderDetector, Path currentShaderPackPath) {
        return UpdateChecker.shouldUserUpdate() &&
                VersionComparator.isNewerVersion(UpdateChecker.getNewModVersion(), shaderDetector.readVersionFromPackJson(currentShaderPackPath));
    }

    @Unique
    private static void euphoriaPatcher$debugLog(String message) {
        EuphoriaLogger.debugLog("[IrisHeaderEntryMixinYarn] " + message);
    }

    @Dynamic
    @Inject(method = "method_25343(Lnet/minecraft/class_332;IIIIIIIZF)V", at = @At("HEAD"), remap = false, require = 0)
    private void euphoriaPatcher$captureRenderParams(@Coerce Object guiGraphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta, CallbackInfo ci) {
        this.euphoriaPatcher$capturedX = x;
        this.euphoriaPatcher$capturedY = y;
        this.euphoriaPatcher$capturedEntryWidth = entryWidth;
    }

    @Dynamic
    @Redirect(method = "method_25343", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_332;method_27534(Lnet/minecraft/class_327;Lnet/minecraft/class_2561;III)V"), remap = false, require = 0)
    private void euphoriaPatcher$redirectDrawCenteredString(@Coerce Object guiGraphics, @Coerce Object font, @Coerce Object text, int x, int y, int color) {
        try {
            Object utilityButtons = ReflectionUtils.getFieldValue(this, "utilityButtons");
            if (utilityButtons != null) {
                // Try to find the getter width from either our mixin or Euphoria Patcher's mixin wrapper
                int utilityButtonsWidth = (int) utilityButtons.getClass().getMethod("euphoriaPatcher$getWidth").invoke(utilityButtons);

                int minX = this.euphoriaPatcher$capturedX + 5;
                int minY = this.euphoriaPatcher$capturedY + 5;
                int maxX = ((this.euphoriaPatcher$capturedX + this.euphoriaPatcher$capturedEntryWidth) - 10) - utilityButtonsWidth;
                int maxY = this.euphoriaPatcher$capturedY + 15;

                // Call our freshly converted 1:1 local scrolling calculation
                euphoriaPatcher$renderScrollingString(guiGraphics, font, text, x, minX, minY, maxX, maxY, 0xFFFFFF);
                return;
            }
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Error in euphoriaPatcher$redirectDrawCenteredString redirection layer: " + e.getMessage());
        }

        // Vanilla fallback loop execution if utilities are absent or reflection properties error out
        try {
            Class<?> guiGraphicsClass = Class.forName("net.minecraft.class_332");
            Class<?> fontClass = Class.forName("net.minecraft.class_327");
            Class<?> componentClass = Class.forName("net.minecraft.class_2561");
            guiGraphicsClass.getMethod("method_27534", fontClass, componentClass, int.class, int.class, int.class).invoke(guiGraphics, font, text, x, y, color);
        } catch (Exception fallbackEx) {
            euphoriaPatcher$debugLog("Critical rendering fallback failed: " + fallbackEx.getMessage());
        }
    }

    @Unique
    private static void euphoriaPatcher$renderScrollingString(Object guiGraphics, Object font, Object text, int centerX, int minX, int minY, int maxX, int maxY, int color) {
        try {
            // Get text width: font.width(text)
            int textWidth = (int) font.getClass().getMethod("method_27525", Class.forName("net.minecraft.class_5348")).invoke(font, text);
            int yPos = (minY + maxY - 9) / 2 + 1;
            int availableWidth = maxX - minX;

            Class<?> guiGraphicsClass = Class.forName("net.minecraft.class_332");
            Class<?> fontClass = Class.forName("net.minecraft.class_327");
            Class<?> componentClass = Class.forName("net.minecraft.class_2561");


            if (textWidth > availableWidth) {
                // Text is too wide, scroll it with smooth sine wave animation
                int scrollRange = textWidth - availableWidth;

                // Get current time: Util.getMillis()
                Class<?> utilClass = Class.forName("net.minecraft.class_156");
                long currentTimeMillis = (long) utilClass.getMethod("method_658").invoke(null);
                double currentTime = (double) currentTimeMillis / 1000.0;

                double scrollDuration = Math.max((double) scrollRange * 0.5, 3.0);
                double scrollProgress = Math.sin(Math.PI / 2 * Math.cos(Math.PI * 2 * currentTime / scrollDuration)) / 2.0 + 0.5;

                // Mth.lerp()
                Class<?> mthClass = Class.forName("net.minecraft.class_3532");
                double scrollOffset = (double) mthClass.getMethod("method_16436", double.class, double.class, double.class).invoke(null, scrollProgress, 0.0, (double) scrollRange);

                // guiGraphics.enableScissor()
                guiGraphicsClass.getMethod("method_44379", int.class, int.class, int.class, int.class).invoke(guiGraphics, minX, minY, maxX, maxY);

                // guiGraphics.drawString(font, text, x, y, color)
                guiGraphicsClass.getMethod("method_27535", fontClass, componentClass, int.class, int.class, int.class)
                        .invoke(guiGraphics, font, text, minX - (int) scrollOffset, yPos, color);

                // guiGraphics.disableScissor()
                guiGraphicsClass.getMethod("method_44380").invoke(guiGraphics);
                euphoriaPatcher$debugLog("Finished rendering scrolling text.");
            } else {
                // Text fits, center it at the specified centerX position using drawCenteredString
                guiGraphicsClass.getMethod("method_27534", fontClass, componentClass, int.class, int.class, int.class)
                        .invoke(guiGraphics, font, text, centerX, yPos, color);
            }
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Error inside converted renderScrollingString: " + e.getMessage());
        }
    }


}
