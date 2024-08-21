package mc.euphoria_patches.euphoria_patcher;

import me.jellysquid.mods.sodium.client.gui.console.Console;
import me.jellysquid.mods.sodium.client.gui.console.ConsoleSink;
import me.jellysquid.mods.sodium.client.gui.console.message.MessageLevel;
//import net.caffeinemc.mods.sodium.client.console.Console; // Idk how to do this correctly is mixin a better approach?
//import net.caffeinemc.mods.sodium.client.console.ConsoleSink;
//import net.caffeinemc.mods.sodium.client.console.message.MessageLevel;
import net.minecraft.text.Text;

public class SodiumConsole {
    private static final ConsoleSink CONSOLE = Console.instance();

    public static void logMessage(int level, String message) {
        if (level == 1) {
            CONSOLE.logMessage(MessageLevel.INFO, Text.of(message), 5.0);
        } else if (level == 2) {
            CONSOLE.logMessage(MessageLevel.WARN, Text.of(message), 15.0);
        } else if (level == 3) {
        CONSOLE.logMessage(MessageLevel.SEVERE, Text.of(message), 15.0);
        }
    }
}