package de.isuewo.euphoria_patcher;

import me.jellysquid.mods.sodium.client.gui.console.Console;
import me.jellysquid.mods.sodium.client.gui.console.ConsoleSink;
import me.jellysquid.mods.sodium.client.gui.console.message.MessageLevel;
import net.minecraft.text.Text;

public class SodiumConsole {
    private static final ConsoleSink CONSOLE = Console.instance();

    public static void logMessage(int level, String message) {
        if (level == 0) {
            CONSOLE.logMessage(MessageLevel.INFO, Text.of(message), 10.0);
        } else if (level == 1) {
            CONSOLE.logMessage(MessageLevel.WARN, Text.of(message), 60.0);
        } else if (level == 2) {
            CONSOLE.logMessage(MessageLevel.SEVERE, Text.of(message), 60.0);
        }
    }
}