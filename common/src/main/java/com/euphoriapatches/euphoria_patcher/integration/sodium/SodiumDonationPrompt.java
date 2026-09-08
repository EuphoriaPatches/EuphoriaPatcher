package com.euphoriapatches.euphoria_patcher.integration.sodium;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.ReflectionUtils;
import com.euphoriapatches.euphoria_patcher.util.UserPersistentData;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class SodiumDonationPrompt {

    public static final int DAYS_THRESHOLD = 3;
    public static final int SETTINGS_CHANGED_THRESHOLD = 10;

    private static final String[] SCREEN_PROMPT_CLASSES = {
            "net.caffeinemc.mods.sodium.client.gui.prompt.ScreenPrompt",
            "me.jellysquid.mods.sodium.client.gui.prompt.ScreenPrompt"
    };
    private static final String[] SCREEN_PROMPTABLE_CLASSES = {
            "net.caffeinemc.mods.sodium.client.gui.prompt.ScreenPromptable",
            "me.jellysquid.mods.sodium.client.gui.prompt.ScreenPromptable"
    };
    private static final String[] DIM2I_CLASSES = {
            "net.caffeinemc.mods.sodium.client.util.Dim2i",
            "me.jellysquid.mods.sodium.client.util.Dim2i"
    };

    private static boolean initialized;
    private static Constructor<?> promptCtor;    // ScreenPrompt(ScreenPromptable, List, int, int, Action)
    private static Constructor<?> actionCtor;    // ScreenPrompt$Action(Component, Runnable)
    private static Constructor<?> dim2iCtor;     // Dim2i(int, int, int, int)
    private static Class<?> promptableClass;

    private static Boolean sessionEligible;

    // Active prompt handle for input hooks lacking a direct instance reference
    private static Object active;

    private SodiumDonationPrompt() {
    }

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[SodiumDonationPrompt] " + message);
    }

    public static boolean isAvailable() {
        if (!initialized) {
            initialized = true;
            try {
                initialize();
            } catch (Throwable t) {
                debugLog("Donation prompt init failed: " + t);
            }
        }
        return promptCtor != null;
    }

    public static boolean shouldShow() {
        if (sessionEligible == null) {
            try {
                sessionEligible = evaluateEligibility();
            } catch (Throwable t) {
                debugLog("Donation prompt eligibility check failed, treating as not eligible: " + t);
                sessionEligible = false;
            }
            debugLog("Donation prompt eligibility for this session: " + sessionEligible);
        }
        return sessionEligible;
    }

    private static boolean evaluateEligibility() {
        UserPersistentData.PersistentShaderData data = UserPersistentData.load();
        if (data == null || Boolean.TRUE.equals(data.clickedSupportPopup)) {
            debugLog("User has already dismissed the donation prompt, or persistent data is missing");
            return false;
        }
        if (data.timesSettingsChanged == null || data.timesSettingsChanged < SETTINGS_CHANGED_THRESHOLD) {
            debugLog("User has changed shader settings only " + data.timesSettingsChanged
                    + " times, below the threshold of " + SETTINGS_CHANGED_THRESHOLD);
            return false;
        }
        if (data.firstEPOptionsTimestamp == null) {
            debugLog("User's first EP options timestamp is missing");
            return false;
        }
        try {
            Instant firstOpened = Instant.parse(data.firstEPOptionsTimestamp);
            Instant now = Instant.now();
            int daysSinceFirstOpened = (int) ChronoUnit.DAYS.between(firstOpened, now);
            debugLog("Days since first EP options opened: " + daysSinceFirstOpened + " (threshold: " + DAYS_THRESHOLD + ")");
            return daysSinceFirstOpened >= DAYS_THRESHOLD;
        } catch (Exception e) {
            debugLog("Unparseable firstEPOptionsTimestamp \"" + data.firstEPOptionsTimestamp + "\": " + e);
            return false;
        }
    }

    private static void initialize() {
        Class<?> promptClass = ReflectionUtils.firstClass(SCREEN_PROMPT_CLASSES);
        promptableClass = ReflectionUtils.firstClass(SCREEN_PROMPTABLE_CLASSES);
        Class<?> dim2iClass = ReflectionUtils.firstClass(DIM2I_CLASSES);
        if (promptClass == null || promptableClass == null || dim2iClass == null) {
            debugLog("Sodium ScreenPrompt not found - donation prompt disabled");
            return;
        }
        try {
            dim2iCtor = dim2iClass.getConstructor(int.class, int.class, int.class, int.class);
            promptCtor = ReflectionUtils.findConstructor(promptClass, 5);
            for (Class<?> nested : promptClass.getDeclaredClasses()) {
                if (nested.getSimpleName().equals("Action")) {
                    actionCtor = ReflectionUtils.findConstructor(nested, 2);
                }
            }
        } catch (Throwable t) {
            debugLog("Sodium ScreenPrompt has an unexpected shape: " + t);
        }
        if (promptCtor == null || actionCtor == null) {
            promptCtor = null;
            debugLog("Could not resolve the ScreenPrompt / Action constructors");
        }
    }

    /**
     * Builds and focuses a prompt.
     *
     * @param screenWidth/screenHeight the host screen size (the box is centred on it)
     * @param boxWidth/boxHeight       the popup box size
     * @param paragraphs               Minecraft {@code FormattedText} objects, one per line
     * @param actionLabel              a Minecraft {@code Component} for the action button
     * @param onAction                 runs when the action button is clicked
     * @param onDismiss                runs once when the prompt is dismissed
     * @return an opaque handle, or {@code null} on failure
     */
    public static Object show(int screenWidth, int screenHeight, int boxWidth, int boxHeight,
                              List<?> paragraphs, Object actionLabel, Runnable onAction, Runnable onDismiss) {
        if (!isAvailable()) {
            return null;
        }
        try {
            Handle handle = new Handle(screenWidth, screenHeight, onDismiss);
            Object parent = Proxy.newProxyInstance(promptableClass.getClassLoader(),
                    new Class<?>[]{promptableClass}, handle);
            Object action = actionCtor.newInstance(actionLabel, (Runnable) () -> {
                try {
                    onAction.run();
                } catch (Throwable t) {
                    debugLog("Donation action failed: " + t);
                }
            });
            handle.prompt = promptCtor.newInstance(parent, paragraphs, boxWidth, boxHeight, action);
            // "init" uses fixed name; setFocused/render/input match by signature due to obfuscation.
            ReflectionUtils.invokeMethod(handle.prompt, "init", new Class<?>[0]);
            ReflectionUtils.invokeBySignature(handle.prompt, void.class, new Class<?>[]{boolean.class}, Boolean.TRUE);
            active = handle;
            return handle;
        } catch (Throwable t) {
            debugLog("Failed to create donation prompt: " + t);
            return null;
        }
    }

    public static boolean isShowing(Object handle) {
        return handle instanceof Handle && ((Handle) handle).prompt != null && ((Handle) handle).open[0] != null;
    }

    public static boolean isShowing() {
        return isShowing(active);
    }

    public static boolean forwardInput(Class<?>[] paramTypes, Object... args) {
        return forwardInput(active, paramTypes, args);
    }

    /** Re-centres the prompt for a new host screen size. */
    public static void relayout(Object handle, int screenWidth, int screenHeight) {
        if (!(handle instanceof Handle)) {
            return;
        }
        try {
            ((Handle) handle).screenWidth = screenWidth;
            ((Handle) handle).screenHeight = screenHeight;
            ReflectionUtils.invokeMethod(((Handle) handle).prompt, "init", new Class<?>[0]);
        } catch (Throwable t) {
            debugLog("Donation prompt relayout failed: " + t);
        }
    }

    public static void render(Object handle, Object guiGraphics, int mouseX, int mouseY, float delta) {
        if (!isShowing(handle) || guiGraphics == null) {
            return;
        }
        try {
            ReflectionUtils.invokeBySignature(((Handle) handle).prompt, void.class,
                    new Class<?>[]{guiGraphics.getClass(), int.class, int.class, float.class},
                    guiGraphics, mouseX, mouseY, delta);
        } catch (Throwable t) {
            debugLog("Donation prompt render failed: " + t);
        }
    }

    /**
     * Forwards input events to the prompt via signature-matching and consumes inputs while active.
     *
     * @return {@code true} if prompt is active
     */
    public static boolean forwardInput(Object handle, Class<?>[] paramTypes, Object... args) {
        if (!isShowing(handle)) {
            return false;
        }
        try {
            ReflectionUtils.invokeBySignature(((Handle) handle).prompt, boolean.class, paramTypes, args);
        } catch (Throwable t) {
            debugLog("Donation prompt input forwarding failed: " + t);
        }
        return true;
    }

    /** Handle holding state and proxying calls for Sodium's {@code ScreenPromptable} interface. */
    private static final class Handle implements InvocationHandler {
        private Object prompt;
        private final Object[] open = new Object[1];  // ScreenPrompt.setPrompt(this) / setPrompt(null)
        private final Runnable onDismiss;
        private boolean dismissed;
        private int screenWidth;
        private int screenHeight;

        Handle(int screenWidth, int screenHeight, Runnable onDismiss) {
            this.screenWidth = screenWidth;
            this.screenHeight = screenHeight;
            this.onDismiss = onDismiss;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            try {
                return dispatch(proxy, method, args);
            } catch (Throwable t) {
                debugLog("Donation promptable proxy call " + method.getName() + " failed: " + t);
                return method.getReturnType() == int.class ? 0 : null;
            }
        }

        private Object dispatch(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "getDimensions":
                    return dim2iCtor.newInstance(0, 0, screenWidth, screenHeight);
                case "setPrompt":
                    Object next = (args != null && args.length > 0) ? args[0] : null;
                    // setPrompt(null) while active triggers dismissal logic.
                    if (next == null && open[0] != null && !dismissed) {
                        dismissed = true;
                        if (onDismiss != null) {
                            try {
                                onDismiss.run();
                            } catch (Throwable t) {
                                debugLog("Donation prompt onDismiss failed: " + t);
                            }
                        }
                    }
                    open[0] = next;
                    return null;
                case "getPrompt":
                    return open[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == (args != null && args.length > 0 ? args[0] : null);
                case "toString":
                    return "EuphoriaPatcher$SodiumPromptable";
                default:
                    // Dimensioned's default int getters, should any Sodium path call them on the parent.
                    return method.getReturnType() == int.class ? 0 : null;
            }
        }
    }
}
