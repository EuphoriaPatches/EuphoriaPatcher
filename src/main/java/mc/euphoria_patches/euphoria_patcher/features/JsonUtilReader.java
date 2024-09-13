package mc.euphoria_patches.euphoria_patcher.features;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class JsonUtilReader {
    private static final String JSON_FILE_PATH = "/randomUtil.json";
    private static final Map<String, List<String>> messageCategories = new HashMap<>();
    private static final Random random = new Random();

    static {
        loadMessages();
    }

    private static void loadMessages() {
        try (InputStream inputStream = JsonUtilReader.class.getResourceAsStream(JSON_FILE_PATH);
             InputStreamReader reader = new InputStreamReader(inputStream)) {

            JsonElement jsonElement = new JsonParser().parse(reader);
            JsonObject jsonObject = jsonElement.getAsJsonObject();

            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                String categoryName = entry.getKey();
                JsonElement value = entry.getValue();
                List<String> messages = new ArrayList<>();

                if (value.isJsonArray()) {
                    JsonArray messagesArray = value.getAsJsonArray();
                    for (JsonElement message : messagesArray) {
                        messages.add(message.getAsString());
                    }
                } else if (value.isJsonPrimitive()) {
                    messages.add(value.getAsString());
                }

                if (!messages.isEmpty()) {
                    messageCategories.put(categoryName, messages);
                }
            }
        } catch (Exception e) {
            EuphoriaPatcher.log(3, 0, "Error loading messages: " + e.getMessage());
        }
    }

    public static String getRandomMessage(String category) {
        List<String> messages = messageCategories.get(category);
        if (messages != null && !messages.isEmpty()) {
            int randomIndex = random.nextInt(messages.size());
            return messages.get(randomIndex);
        } else {
            return "No messages available for category: " + category;
        }
    }

    public static Set<String> getAllCategories() {
        return messageCategories.keySet();
    }
}