package de.isuewo.euphoria_patcher;

import me.jellysquid.mods.sodium.client.gui.console.Console;
import me.jellysquid.mods.sodium.client.gui.console.ConsoleSink;
import me.jellysquid.mods.sodium.client.gui.console.message.MessageLevel;
import net.minecraft.text.Text;

public class SodiumConsole {
    static final ConsoleSink console = Console.instance();

    static void logMessage(int level, String message) {
        if (level == 0) {
            console.logMessage(MessageLevel.INFO, Text.of(message), 5.0);
        } else if (level == 1) {
            console.logMessage(MessageLevel.WARN, Text.of(message), 10.0);
        } else if (level == 2) {
            console.logMessage(MessageLevel.SEVERE, Text.of(message), 30.0);
        }
    }
}

