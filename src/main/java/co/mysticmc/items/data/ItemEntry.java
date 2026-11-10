package co.mysticmc.items.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ItemEntry {
    private static final String[] TAG_FIELDS = {"TagPrimary", "TagSecondary", "TagTertiary", "TagQuaternary", "TagQuinary", "TagSenary", "TagSeptenary"};
    public final JsonObject original;
    public final boolean miscellaneous;
    public final int id;
    public final String name;
    public final String minecraftId;
    public final String notes;
    public final String itemHuman;
    public final String rarity;
    public final String groupName;
    public final String urlTag;
    public final List<String> tags;
    public final String searchText;

    public ItemEntry(JsonObject original, boolean miscellaneous, JsonObject group) {
        this.original = original;
        this.miscellaneous = miscellaneous;
        this.id = number(original, "id");
        this.name = TextRepair.fix(string(original, "ItemName"));
        this.notes = TextRepair.fix(string(original, "Notes"));
        this.itemHuman = TextRepair.fix(string(original, "ItemHuman"));
        this.rarity = TextRepair.fix(string(original, "RarityHuman"));
        this.groupName = group == null ? "" : TextRepair.fix(string(group, miscellaneous ? "GroupName" : "CrateName"));
        this.urlTag = group == null ? "" : string(group, "URLTag");
        String itemId = "";
        String instrument = "";
        try {
            JsonObject parsed = JsonParser.parseString(string(original, "RawData")).getAsJsonObject();
            itemId = string(parsed, "id");
            String instrumentId = string(parsed.getAsJsonObject("components"), "minecraft:instrument");
            if (!instrumentId.isEmpty()) {
                instrument = instrumentId.substring(instrumentId.indexOf(':') + 1).replace('_', ' ');
            }
        } catch (RuntimeException ignored) { }
        this.minecraftId = itemId;
        List<String> found = new ArrayList<>();
        if (!miscellaneous) for (String field : TAG_FIELDS) {
            String value = TextRepair.fix(string(original, field)).trim();
            if (!value.isEmpty() && !found.contains(value)) found.add(value);
        }
        this.tags = List.copyOf(found);
        this.searchText = (name + " " + itemId + " " + instrument + " " + notes + " " + itemHuman + " "
                + groupName + " " + rarity + " " + String.join(" ", tags)).toLowerCase(Locale.ROOT);
    }

    public static String string(JsonObject object, String field) {
        if (object == null) return "";
        JsonElement value = object.get(field);
        return value == null || value.isJsonNull() ? "" : value.isJsonPrimitive() ? value.getAsString() : value.toString();
    }

    public static int number(JsonObject object, String field) {
        try { return Integer.parseInt(string(object, field)); }
        catch (NumberFormatException ignored) { return -1; }
    }
}
