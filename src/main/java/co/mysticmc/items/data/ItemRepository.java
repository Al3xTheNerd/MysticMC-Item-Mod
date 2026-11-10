package co.mysticmc.items.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ItemRepository {
    private static final Logger LOG = LoggerFactory.getLogger("MysticMC Item Browser");
    private static final String BASE = "https://items.mysticmc.co/api/";
    private static final String ITEM_FIELDS = "id;CrateID;TagPrimary;TagSecondary;TagTertiary;TagQuaternary;TagQuinary;TagSenary;TagSeptenary;WinPercentage;RarityHuman;RarityHTML;ItemName;ItemNameHTML;Notes;RawData;ItemHuman;ItemHTML;ItemOrder;ConnectedItems;EfficiencyLevel;ImageType";
    private static final String MISC_FIELDS = "id;GroupID;ItemName;ItemNameHTML;Notes;RawData;ItemHuman;ItemHTML;ItemOrder;ImageType";
    private final Path cachePath = FabricLoader.getInstance().getConfigDir().resolve("mystic-item-browser/items-v1.json");
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "MysticMC item sync");
        thread.setDaemon(true);
        return thread;
    });
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build();
    private final AtomicBoolean syncing = new AtomicBoolean();
    private volatile ItemDatabase database;
    private volatile String status = "Loading cache";
    private volatile boolean cached = true;

    public ItemDatabase database() { return database; }
    public String status() { return status; }
    public boolean isCached() { return cached; }

    public void start() {
        worker.execute(() -> {
            try {
                if (Files.exists(cachePath)) {
                    database = new ItemDatabase(JsonParser.parseString(Files.readString(cachePath)).getAsJsonObject());
                    status = "Cached data loaded";
                }
            } catch (Exception error) {
                status = "Cache unavailable: " + error.getMessage();
                LOG.warn("Could not load MysticMC cache", error);
            }
            refresh(null);
        });
    }

    public boolean refresh(Consumer<String> callback) {
        if (!syncing.compareAndSet(false, true)) return false;
        status = "Downloading item data";
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject root = new JsonObject();
                root.addProperty("format", 1);
                root.addProperty("syncedAt", Instant.now().toString());
                root.add("items", fetchItems("items", ITEM_FIELDS));
                root.add("miscitems", fetchItems("miscitems", MISC_FIELDS));
                root.add("crates", fetchArray("crates", null));
                root.add("miscgroups", fetchArray("miscgroups", null));
                root.add("tags", fetchArray("tags", null));
                ItemDatabase next = new ItemDatabase(root);
                Files.createDirectories(cachePath.getParent());
                Path temp = Files.createTempFile(cachePath.getParent(), "items-", ".tmp");
                try {
                    Files.writeString(temp, root.toString(), StandardCharsets.UTF_8);
                    try { Files.move(temp, cachePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                    catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(temp, cachePath, StandardCopyOption.REPLACE_EXISTING); }
                } finally { Files.deleteIfExists(temp); }
                database = next;
                cached = false;
                status = "Fresh data";
                if (callback != null) callback.accept("MysticMC: loaded " + next.items.size() + " items.");
            } catch (Exception error) {
                status = "Refresh failed: " + error.getMessage();
                LOG.warn("MysticMC refresh failed; existing cache retained", error);
                if (callback != null) callback.accept("MysticMC refresh failed; " + (database == null ? "no cache available." : "cached items remain available."));
            } finally { syncing.set(false); }
        }, worker);
        return true;
    }

    private JsonObject fetchItems(String endpoint, String fields) throws IOException, InterruptedException {
        JsonElement response = fetch(endpoint, fields);
        if (!response.isJsonObject() || !response.getAsJsonObject().has("data")) throw new IOException("Missing data in " + endpoint);
        JsonElement data = response.getAsJsonObject().get("data");
        if (!data.isJsonArray() || data.getAsJsonArray().isEmpty()) throw new IOException("Empty " + endpoint);
        return response.getAsJsonObject();
    }

    private JsonArray fetchArray(String endpoint, String fields) throws IOException, InterruptedException {
        JsonElement response = fetch(endpoint, fields);
        if (!response.isJsonArray()) throw new IOException("Invalid " + endpoint);
        return response.getAsJsonArray();
    }

    private JsonElement fetch(String endpoint, String fields) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(BASE + endpoint)).timeout(Duration.ofSeconds(35)).GET();
        if (fields != null) request.header("I-INCLUDED-INFO", fields);
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) throw new IOException(endpoint + " returned HTTP " + response.statusCode());
        try { return JsonParser.parseString(response.body()); }
        catch (RuntimeException error) { throw new IOException("Invalid JSON from " + endpoint, error); }
    }
}
