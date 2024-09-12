package mc.euphoria_patches.euphoria_patcher.features;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RandomStartupMessage {
    private static List<String> messages;
    private static final Random random = new Random();

    static {
        loadMessages();
    }

    private static void loadMessages() {
        messages = new ArrayList<>();
        try (InputStream inputStream = RandomStartupMessage.class.getResourceAsStream("/startupMessages.json");
             InputStreamReader reader = new InputStreamReader(inputStream)) {

            JsonElement jsonElement = new JsonParser().parse(reader);
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            JsonArray messagesArray = jsonObject.getAsJsonArray("messages");

            for (JsonElement message : messagesArray) {
                messages.add(message.getAsString());
            }
        } catch (Exception e) {
            EuphoriaPatcher.log(3, 0, "Error loading startup messages: " + e.getMessage());
        }
    }

    public static String getRandomMessage() {
        if (messages != null && !messages.isEmpty()) {
            int randomIndex = random.nextInt(messages.size());
            return messages.get(randomIndex);
        } else {
            return "No startup messages available.";
        }
    }
}
