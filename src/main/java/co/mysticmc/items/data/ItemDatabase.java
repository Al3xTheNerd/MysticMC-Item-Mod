package co.mysticmc.items.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ItemDatabase {
    public final JsonObject original;
    public final List<ItemEntry> items;
    public final List<String> tags;
    public final List<String> crates;
    public final List<String> groups;
    public final int crateCount;
    public final int miscCount;
    public final Instant syncedAt;

    public ItemDatabase(JsonObject original) {
        this.original = original;
        if (ItemEntry.number(original, "format") != 1) throw new IllegalArgumentException("Unknown cache format");
        JsonArray crateItems = array(original, "items");
        JsonArray miscItems = array(original, "miscitems");
        JsonArray crateList = array(original, "crates");
        JsonArray groupList = array(original, "miscgroups");
        this.crateCount = crateItems.size();
        this.miscCount = miscItems.size();
        if (crateCount + miscCount == 0) throw new IllegalArgumentException("Empty item database");
        this.syncedAt = Instant.parse(ItemEntry.string(original, "syncedAt"));
        Map<Integer, JsonObject> crateMap = byId(crateList);
        Map<Integer, JsonObject> groupMap = byId(groupList);
        List<ItemEntry> all = new ArrayList<>(crateCount + miscCount);
        Set<String> tagSet = new HashSet<>();
        for (JsonElement element : crateItems) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            ItemEntry entry = new ItemEntry(item, false, crateMap.get(ItemEntry.number(item, "CrateID")));
            all.add(entry);
            tagSet.addAll(entry.tags);
        }
        for (JsonElement element : miscItems) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            all.add(new ItemEntry(item, true, groupMap.get(ItemEntry.number(item, "GroupID"))));
        }
        this.items = List.copyOf(all);
        for (JsonElement element : array(original, "tags")) if (element.isJsonPrimitive()) tagSet.add(TextRepair.fix(element.getAsString()));
        this.tags = tagSet.stream().filter(s -> !s.isBlank()).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        this.crates = names(crateList, "CrateName");
        this.groups = names(groupList, "GroupName");
    }

    private static JsonArray array(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value != null && value.isJsonObject()) value = value.getAsJsonObject().get("data");
        if (value == null || !value.isJsonArray()) throw new IllegalArgumentException("Missing " + name);
        return value.getAsJsonArray();
    }

    private static Map<Integer, JsonObject> byId(JsonArray array) {
        Map<Integer, JsonObject> result = new HashMap<>();
        for (JsonElement element : array) if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            result.put(ItemEntry.number(object, "id"), object);
        }
        return result;
    }

    private static List<String> names(JsonArray array, String field) {
        List<String> names = new ArrayList<>();
        for (JsonElement element : array) if (element.isJsonObject()) names.add(TextRepair.fix(ItemEntry.string(element.getAsJsonObject(), field)));
        return List.copyOf(names);
    }
}
