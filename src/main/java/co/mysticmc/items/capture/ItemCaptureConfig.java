package co.mysticmc.items.capture;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Simple file settings; keybinds are managed by Minecraft's Controls screen. */
public record ItemCaptureConfig(String apiKey, String dataEndpoint) {
    private static final Path PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("mystic-item-browser-upload.json");

    public static void initialize() {
        if (Files.exists(PATH)) return;
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, "{\n  \"apiKey\": \"\",\n  \"dataEndpoint\": \"\"\n}\n",
                    StandardCharsets.UTF_8);
        } catch (IOException error) {
            System.err.println("[MysticMC Items] Could not create upload config: " + error);
        }
    }

    public static ItemCaptureConfig get() throws IOException {
        try {
            JsonObject object = JsonParser.parseString(Files.readString(PATH, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            return new ItemCaptureConfig(value(object, "apiKey"), value(object, "dataEndpoint"));
        } catch (RuntimeException error) {
            throw new IOException("Invalid upload config: " + PATH, error);
        }
    }

    private static String value(JsonObject object, String name) {
        return object.has(name) && !object.get(name).isJsonNull()
                ? object.get(name).getAsString().trim() : "";
    }
}
