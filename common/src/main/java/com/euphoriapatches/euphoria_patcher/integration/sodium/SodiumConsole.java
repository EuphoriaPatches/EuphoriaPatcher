package com.euphoriapatches.euphoria_patcher.integration.sodium;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.ReflectionUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
public final class SodiumConsole {

    private SodiumConsole() {
    }

    private static final String[] CONSOLE_CLASSES = {
            "net.caffeinemc.mods.sodium.client.console.Console",       // Sodium 0.6.7+ ("26.x")
            "net.caffeinemc.mods.sodium.client.gui.console.Console",   // Sodium 0.6.0 - 0.6.6
            "me.jellysquid.mods.sodium.client.gui.console.Console"     // Sodium 0.5.x (1.20.1)
    };

    private static final String[] TEXT_FACTORY_NAMES = {
            "literal", "m_237113_", "method_43470",   // Component.literal / Text.literal
            "of", "method_30163",                     // Text.of
            "nullToEmpty", "m_130674_"                // Component.nullToEmpty
    };

    private static boolean initialized;
    private static boolean available;

    private static Object sink;
    private static Method logMessage;
    private static Object infoLevel;
    private static Object warnLevel;
    private static Object severeLevel;

    /** true: {@code (MessageLevel, Text, duration)}; false: {@code (MessageLevel, String, boolean, double)}. */
    private static boolean componentShape;
    /** legacy duration parameter is {@code int} rather than {@code double}. */
    private static boolean intDuration;
    private static Method textFactory; // static String -> Text
    private static Constructor<?> textConstructor; // fallback: new Text(String)

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[SodiumConsole] " + message);
    }

    public static boolean isSodiumAvailable() {
        ensureInitialized();
        return available;
    }

    /**
     * @param level 1 = info, 2 = warn, anything else = severe
     * @param messageFadeTimer seconds before the popup fades
     * @param message plain text to show
     */
    public static void logMessage(int level, int messageFadeTimer, String message) {
        ensureInitialized();
        if (!available) {
            return;
        }
        try {
            Object messageLevel = level == 1 ? infoLevel : level == 2 ? warnLevel : severeLevel;
            if (!componentShape) {
                logMessage.invoke(sink, messageLevel, message, false, (double) messageFadeTimer);
            } else if (intDuration) {
                logMessage.invoke(sink, messageLevel, createText(message), messageFadeTimer);
            } else {
                logMessage.invoke(sink, messageLevel, createText(message), (double) messageFadeTimer);
            }
        } catch (Throwable t) {
            available = false;
            debugLog("Disabled after a logging failure: " + t);
        }
    }

    private static synchronized void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            initialize();
        } catch (Throwable t) {
            available = false;
            debugLog("Initialization failed: " + t);
        }
    }

    private static void initialize() {
        Class<?> consoleClass = ReflectionUtils.firstClass(CONSOLE_CLASSES);
        if (consoleClass == null) {
            debugLog("Sodium not present - no console class found");
            return;
        }
        debugLog("Using console class " + consoleClass.getName());

        sink = resolveSink(consoleClass);
        if (sink == null) {
            debugLog("Could not obtain the Console instance");
            return;
        }

        logMessage = findLogMessage(sink.getClass());
        if (logMessage == null) {
            debugLog("No usable logMessage(...) on " + sink.getClass().getName());
            return;
        }
        logMessage.setAccessible(true);

        Class<?>[] params = logMessage.getParameterTypes();

        Class<?> levelClass = params[0];
        infoLevel = ReflectionUtils.getFieldValue(levelClass, "INFO");
        warnLevel = ReflectionUtils.getFieldValue(levelClass, "WARN");
        severeLevel = ReflectionUtils.getFieldValue(levelClass, "SEVERE");
        if (infoLevel == null || warnLevel == null || severeLevel == null) {
            debugLog("MessageLevel constants missing on " + levelClass.getName());
            return;
        }

        intDuration = params[params.length - 1] == int.class;

        if (params.length == 4 && params[1] == String.class) {
            componentShape = false;
            debugLog("Modern string-based console API");
        } else {
            componentShape = true;
            if (!resolveTextFactory(params[1])) {
                debugLog("Could not resolve a String -> " + params[1].getName() + " factory");
                return;
            }
            debugLog("Legacy component-based console API (" + params[1].getName()
                    + ", " + (intDuration ? "int" : "double") + " duration)");
        }

        available = true;
        debugLog("Ready yay");
    }

    private static Object resolveSink(Class<?> consoleClass) {
        Object value = ReflectionUtils.invokeMethod(consoleClass, "instance", new Class<?>[0]);
        return value != null ? value : ReflectionUtils.getFieldValue(consoleClass, "INSTANCE");
    }

    /** The text/duration types vary, but the method is always {@code logMessage(MessageLevel, ...)}. */
    private static Method findLogMessage(Class<?> sinkClass) {
        for (Method method : sinkClass.getMethods()) {
            if (!method.getName().equals("logMessage")) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length < 3 || !params[0].isEnum()) {
                continue;
            }
            Class<?> last = params[params.length - 1];
            if (last == int.class || last == double.class) {
                return method;
            }
        }
        return null;
    }

    private static boolean resolveTextFactory(Class<?> textClass) {
        try {
            textFactory = ReflectionUtils.tryMethods(textClass, new Class<?>[]{String.class}, TEXT_FACTORY_NAMES);
            return true;
        } catch (NoSuchMethodException ignored) {
        }
        return false;
    }

    private static Object createText(String message) throws ReflectiveOperationException {
        return textFactory != null ? textFactory.invoke(null, message) : textConstructor.newInstance(message);
    }
}
