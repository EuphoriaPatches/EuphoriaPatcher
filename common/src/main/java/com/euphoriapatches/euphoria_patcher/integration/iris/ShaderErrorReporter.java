package com.euphoriapatches.euphoria_patcher.integration.iris;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.integration.ShaderLoader;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.services.ShaderDetector;
import com.euphoriapatches.euphoria_patcher.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

/**
 * Adds a clickable "report it on discord" line to the chat when the shader loader fails
 */
public final class ShaderErrorReporter {

    private ShaderErrorReporter() {}

    private static final String DISCORD_URL = "https://euphoriapatches.com/discord";

    private static final String FAILED_KEY = "euphoria_patcher.shader_error.failed";
    private static final String REPORT_KEY = "euphoria_patcher.shader_error.report";
    private static final String LINK_KEY = "euphoria_patcher.shader_error.report.link";
    private static final String COPY_KEY = "euphoria_patcher.shader_error.copy";
    // Both translated by Mojang
    private static final String LINK_HOVER_KEY = "chat.link.open";
    private static final String COPY_HOVER_KEY = "chat.copy.click";

    private static final String[] MINECRAFT_CLASSES = {"net.minecraft.client.Minecraft", "net.minecraft.class_310"};
    private static final String[] COMPONENT_CLASSES = {"net.minecraft.network.chat.Component", "net.minecraft.class_2561"};
    // Component only got its factory methods in 1.19, before that text is a TranslatableComponent
    private static final String[] TRANSLATABLE_CLASSES = {"net.minecraft.network.chat.TranslatableComponent", "net.minecraft.class_2588"};
    private static final String[] STYLE_CLASSES = {"net.minecraft.network.chat.Style", "net.minecraft.class_2583"};
    private static final String[] CLICK_EVENT_CLASSES = {"net.minecraft.network.chat.ClickEvent", "net.minecraft.class_2558"};
    private static final String[] HOVER_EVENT_CLASSES = {"net.minecraft.network.chat.HoverEvent", "net.minecraft.class_2568"};
    private static final String[] CHAT_FORMATTING_CLASSES = {"net.minecraft.ChatFormatting", "net.minecraft.class_124"};

    private static final String[] GET_INSTANCE = {"getInstance", "m_91087_", "method_1551"};
    private static final String[] PLAYER_FIELD = {"player", "f_91074_", "field_1724"};
    private static final String[] TRANSLATABLE = {"translatable", "m_237115_", "method_43471"};
    private static final String[] TRANSLATABLE_ARGS = {"translatable", "m_237110_", "method_43469"};
    private static final String[] DISPLAY_CLIENT_MESSAGE = {"displayClientMessage", "m_5661_", "method_7353"};
    // Replaced displayClientMessage on 26.x
    private static final String[] SEND_SYSTEM_MESSAGE = {"sendSystemMessage", "m_213846_", "method_43496"};

    private static final String[] GET_STYLE = {"getStyle", "m_7383_", "method_10866"};
    private static final String[] SET_STYLE = {"setStyle", "m_6270_", "method_10862"};
    private static final String[] WITH_STYLE = {"withStyle", "m_130940_", "method_27692"};
    private static final String[] WITH_CLICK_EVENT = {"withClickEvent", "m_131142_", "method_10958"};
    private static final String[] WITH_HOVER_EVENT = {"withHoverEvent", "m_131144_", "method_10949"};

    private static final Class<?>[] NO_PARAMS = {};

    private static final List<String> FALLBACK_PIPELINES = Arrays.asList("VanillaRenderingPipeline", "FixedFunctionWorldRenderingPipeline");

    // In case the report is triggered before the player exists, queued here to be called in the next tick
    private static volatile boolean pending;
    private static volatile boolean pendingHeader;
    private static volatile String pendingError;

    // Latest failure seen by captureError, for old iris versions that don't put the error in chat
    private static volatile String lastError;

    private static Boolean handlesExceptions;
    private static Boolean reportsErrors;

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[ShaderErrorReporter] " + message);
    }

    public static void captureError(Throwable error) {
        if (error != null) {
            lastError = error.getMessage() != null ? error.getMessage() : error.toString();
        }
    }

    public static void onShaderError(String irisClass) {
        try {
            if (!isEuphoriaShaderActive()) {
                return;
            }
            boolean loaderReports = loaderReportsErrors(irisClass);
            // With debug options on, the loader opens its error screen instead of printing to chat
            if (loaderReports && debugOptionsEnabled(irisClass)) {
                debugLog("Loader debug options are on, skipping chat report");
                return;
            }
            pendingHeader = !loaderReports;
            pendingError = loaderReports ? null : lastError;
            lastError = null;
            if (!trySend()) {
                pending = true;
                debugLog("No player yet, queued shader error report");
            }
        } catch (Throwable t) {
            debugLog("Shader error report failed: " + t);
        }
    }

    /**
     * Hook for loaders that swallow the failure inside {@code createPipeline} and return a fallback
     * pipeline. A no-op where {@code handleException} exists, which {@link #onShaderError} already covers.
     */
    public static void onPipelineCreated(String irisClass, Object pipeline) {
        try {
            if (pipeline == null || handlesExceptions(irisClass)
                    || !FALLBACK_PIPELINES.contains(pipeline.getClass().getSimpleName())) {
                return;
            }
            // With no pack loaded the fallback pipeline is the intended result, not a failure
            if (ReflectionUtils.getFieldValue(irisClass, "currentPack") == null) {
                return;
            }
            onShaderError(irisClass);
        } catch (Throwable t) {
            debugLog("Pipeline check failed: " + t);
        }
    }

    public static void checkPending() {
        if (!pending) {
            return;
        }
        try {
            if (trySend()) {
                pending = false;
            }
        } catch (Throwable t) {
            pending = false;
            debugLog("Queued shader error report failed: " + t);
        }
    }

    private static boolean isEuphoriaShaderActive() {
        ShaderDetector detector = EuphoriaPatcher.getInstance().getShaderDetector();
        return detector != null && detector.isEuphoriaPatchesShader(ShaderLoader.getCurrentShaderpackPath());
    }

    private static boolean handlesExceptions(String irisClass) {
        if (handlesExceptions == null) {
            handlesExceptions = ReflectionUtils.hasDeclaredMethod(irisClass, "handleException");
        }
        return handlesExceptions;
    }

    private static boolean loaderReportsErrors(String irisClass) {
        if (reportsErrors == null) {
            reportsErrors = ReflectionUtils.hasDeclaredMethod(irisClass, "getStoredError");
        }
        return reportsErrors;
    }

    private static boolean debugOptionsEnabled(String irisClass) {
        Object config = ReflectionUtils.invokeMethod(irisClass, "getIrisConfig");
        return Boolean.TRUE.equals(ReflectionUtils.invokeMethod(config, "areDebugOptionsEnabled"));
    }

    private static boolean trySend() {
        Object player = currentPlayer();
        if (player == null) {
            return false;
        }
        Class<?> componentClass = ReflectionUtils.firstClass(COMPONENT_CLASSES);
        Method sender = componentClass == null ? null : findSender(player.getClass(), componentClass);
        if (sender == null) {
            debugLog("No way to send a chat message to " + player.getClass().getName());
            return true;
        }
        if (pendingHeader) {
            Object header = translatable(componentClass, FAILED_KEY, copyLink(componentClass, pendingError));
            send(sender, player, format(header, "RED"));
        }
        send(sender, player, translatable(componentClass, REPORT_KEY, discordLink(componentClass)));
        return true;
    }

    private static Object currentPlayer() {
        Class<?> minecraftClass = ReflectionUtils.firstClass(MINECRAFT_CLASSES);
        Object minecraft = minecraftClass == null ? null : ReflectionUtils.invokeMethod(minecraftClass, GET_INSTANCE, NO_PARAMS);
        return ReflectionUtils.getFieldValue(minecraft, PLAYER_FIELD);
    }

    /** {@code displayClientMessage(Component, boolean)}, or {@code sendSystemMessage(Component)} where that is gone. */
    private static Method findSender(Class<?> playerClass, Class<?> componentClass) {
        try {
            return ReflectionUtils.tryMethods(playerClass, new Class<?>[]{componentClass, boolean.class}, DISPLAY_CLIENT_MESSAGE);
        } catch (NoSuchMethodException ignored) {
        }
        try {
            return ReflectionUtils.tryMethods(playerClass, new Class<?>[]{componentClass}, SEND_SYSTEM_MESSAGE);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static void send(Method sender, Object player, Object message) {
        if (message == null) {
            return;
        }
        try {
            if (sender.getParameterCount() == 2) {
                sender.invoke(player, message, false);
            } else {
                sender.invoke(player, message);
            }
        } catch (Throwable t) {
            debugLog("Failed to send the shader error report: " + t);
        }
    }

    /** Link opening discord. Falls back to the bare URL if it can't be made clickable. */
    private static Object discordLink(Class<?> componentClass) {
        return clickableLink(componentClass, LINK_KEY, clickEvent("OPEN_URL", DISCORD_URL), LINK_HOVER_KEY, DISCORD_URL);
    }

    /** Link copying the loader's error text, mirroring Iris' own "Copy Info". Empty when there is nothing to copy. */
    private static Object copyLink(Class<?> componentClass, String error) {
        if (error == null) {
            return "";
        }
        return clickableLink(componentClass, COPY_KEY, clickEvent("COPY_TO_CLIPBOARD", error), COPY_HOVER_KEY, "");
    }

    /** Blue underlined translatable text that runs {@code clickEvent} and shows a tooltip. */
    private static Object clickableLink(Class<?> componentClass, String textKey, Object clickEvent, String hoverKey, Object fallback) {
        Class<?> styleClass = ReflectionUtils.firstClass(STYLE_CLASSES);
        Class<?> clickEventClass = ReflectionUtils.firstClass(CLICK_EVENT_CLASSES);
        Object link = format(translatable(componentClass, textKey), "BLUE", "UNDERLINE");
        if (link == null || clickEvent == null || styleClass == null || clickEventClass == null) {
            return fallback;
        }
        Object style = ReflectionUtils.invokePublicMethod(link, GET_STYLE, NO_PARAMS);
        style = ReflectionUtils.invokePublicMethod(style, WITH_CLICK_EVENT, new Class<?>[]{clickEventClass}, clickEvent);
        style = withHover(style, translatable(componentClass, hoverKey), componentClass);
        Object clickable = style == null ? null : ReflectionUtils.invokePublicMethod(link, SET_STYLE, new Class<?>[]{styleClass}, style);
        if (clickable == null) {
            debugLog("Could not make the " + textKey + " text clickable");
            return fallback;
        }
        return clickable;
    }

    /** Adds a show-text tooltip to the style. The link works without one, so a failure keeps the style as is. */
    private static Object withHover(Object style, Object text, Class<?> componentClass) {
        Class<?> hoverEventClass = ReflectionUtils.firstClass(HOVER_EVENT_CLASSES);
        if (style == null || text == null || hoverEventClass == null) {
            return style;
        }
        try {
            Object hoverEvent = showTextEvent(hoverEventClass, text, componentClass);
            Object hovered = hoverEvent == null ? null : ReflectionUtils.invokePublicMethod(style, WITH_HOVER_EVENT, new Class<?>[]{hoverEventClass}, hoverEvent);
            return hovered != null ? hovered : style;
        } catch (ReflectiveOperationException e) {
            debugLog("Could not add the link tooltip: " + e);
            return style;
        }
    }

    /** {@code new HoverEvent(SHOW_TEXT, text)} up to 1.21.4, {@code new HoverEvent.ShowText(text)} from 1.21.5. */
    private static Object showTextEvent(Class<?> hoverEventClass, Object text, Class<?> componentClass) throws ReflectiveOperationException {
        for (Class<?> nested : hoverEventClass.getDeclaredClasses()) {
            try {
                return nested.getConstructor(componentClass).newInstance(text);
            } catch (NoSuchMethodException ignored) {
            }
        }
        for (Class<?> nested : hoverEventClass.getDeclaredClasses()) {
            for (Field field : nested.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || field.getType() != nested) {
                    continue;
                }
                // Action isn't an enum and its fields are renamed, but its toString() is "<action show_text>"
                Object action = field.get(null);
                if (action != null && action.toString().contains("show_text")) {
                    return hoverEventClass.getConstructor(nested, Object.class).newInstance(action, text);
                }
            }
        }
        return null;
    }

    /**
     * {@code new ClickEvent(action, value)} up to 1.21.4. From 1.21.5 the events are one record per action
     */
    private static Object clickEvent(String actionName, String value) {
        Class<?> clickEventClass = ReflectionUtils.firstClass(CLICK_EVENT_CLASSES);
        if (clickEventClass == null) {
            return null;
        }
        for (Class<?> nested : clickEventClass.getDeclaredClasses()) {
            Object action = ReflectionUtils.enumConstant(nested, actionName);
            if (action == null) {
                continue;
            }
            try {
                return clickEventClass.getConstructor(nested, String.class).newInstance(action, value);
            } catch (ReflectiveOperationException ignored) {
                // 1.21.5+ keeps the Action enum but no longer builds events from it
            }
        }
        if ("OPEN_URL".equals(actionName)) {
            for (Class<?> nested : clickEventClass.getDeclaredClasses()) {
                try {
                    return nested.getConstructor(URI.class).newInstance(URI.create(value));
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
        debugLog("No way to build a " + actionName + " click event on this version");
        return null;
    }

    /** {@code component.withStyle(ChatFormatting.X)} for each name. */
    private static Object format(Object component, String... formattings) {
        Class<?> formattingClass = ReflectionUtils.firstClass(CHAT_FORMATTING_CLASSES);
        if (component == null || formattingClass == null) {
            return component;
        }
        for (String name : formattings) {
            Object constant = ReflectionUtils.enumConstant(formattingClass, name);
            Object formatted = constant == null ? null : ReflectionUtils.invokePublicMethod(component, WITH_STYLE, new Class<?>[]{formattingClass}, constant);
            if (formatted == null) {
                debugLog("Could not apply " + name);
            } else {
                component = formatted;
            }
        }
        return component;
    }

    /** {@code Component.translatable(key, args)}, or {@code new TranslatableComponent(key, args)} before 1.19. */
    private static Object translatable(Class<?> componentClass, String key, Object... args) {
        try {
            try {
                return args.length == 0
                        ? ReflectionUtils.tryMethods(componentClass, new Class<?>[]{String.class}, TRANSLATABLE).invoke(null, key)
                        : ReflectionUtils.tryMethods(componentClass, new Class<?>[]{String.class, Object[].class}, TRANSLATABLE_ARGS).invoke(null, key, args);
            } catch (NoSuchMethodException e) {
                Class<?> legacy = ReflectionUtils.firstClass(TRANSLATABLE_CLASSES);
                return legacy == null ? null : legacy.getConstructor(String.class, Object[].class).newInstance(key, args);
            }
        } catch (ReflectiveOperationException e) {
            debugLog("Could not build translatable text for " + key + ": " + e);
            return null;
        }
    }
}
