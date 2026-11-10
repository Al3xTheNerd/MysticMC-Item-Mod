package co.mysticmc.items.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Reconstructs on the client thread and retains one immutable prototype per item. */
public final class StackFactory {
    private static final Logger LOG = LoggerFactory.getLogger("MysticMC Item Browser");
    private static int diagnostics;
    private final Map<String, ItemStack> cache = new HashMap<>();

    public void clear() { cache.clear(); }

    public ItemStack get(ItemEntry entry) {
        return cache.computeIfAbsent((entry.miscellaneous ? "m" : "c") + entry.id, key -> reconstruct(entry));
    }

    private ItemStack reconstruct(ItemEntry entry) {
        String raw = ItemEntry.string(entry.original, "RawData");
        try {
            JsonObject object = JsonParser.parseString(raw).getAsJsonObject();
            RegistryAccess access = Minecraft.getInstance().level == null
                    ? RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)
                    : Minecraft.getInstance().level.registryAccess();
            normalizeComponents(object, access, 0);
            var result = ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, access), object);
            if (result.result().isPresent() && !result.result().get().isEmpty()) return result.result().get();
            diagnostic(entry, result.error().map(Object::toString).orElse("empty stack"));
            return partial(entry, object, access);
        } catch (Exception error) {
            diagnostic(entry, error.toString());
            try { return fallback(entry, JsonParser.parseString(raw).getAsJsonObject()); }
            catch (Exception ignored) { return fallback(entry, null); }
        }
    }

    private static ItemStack partial(ItemEntry entry, JsonObject object, RegistryAccess access) {
        ItemStack stack = fallback(entry, object);
        JsonElement value = object.get("components");
        if (value != null && value.isJsonObject()) {
            for (var component : value.getAsJsonObject().entrySet()) {
                JsonObject one = new JsonObject();
                one.addProperty("id", ItemEntry.string(object, "id"));
                one.addProperty("count", Math.max(1, stack.getCount()));
                JsonObject components = new JsonObject();
                components.add(component.getKey(), component.getValue());
                one.add("components", components);
                ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, access), one).result().ifPresent(parsed -> {
                    try { stack.applyComponents(parsed.getComponentsPatch()); }
                    catch (RuntimeException error) { diagnostic(entry, component.getKey() + ": " + error); }
                });
            }
        }
        return stack;
    }

    private static void normalizeComponents(JsonObject object, RegistryAccess access, int depth) {
        if (depth > 8) return;
        JsonElement value = object.get("components");
        if (value == null || !value.isJsonObject()) return;
        JsonObject components = value.getAsJsonObject();
        // The API includes pre-26.1 stack JSON. The cache retains that source unchanged.
        components.remove("minecraft:hide_additional_tooltip");
        for (String name : List.of("minecraft:enchantments", "minecraft:stored_enchantments")) {
            JsonElement component = components.get(name);
            if (component != null && component.isJsonObject() && component.getAsJsonObject().has("levels"))
                components.add(name, component.getAsJsonObject().get("levels"));
        }
        JsonElement modifiers = components.get("minecraft:attribute_modifiers");
        if (modifiers != null && modifiers.isJsonObject()) {
            JsonObject old = modifiers.getAsJsonObject();
            JsonElement list = old.get("modifiers");
            if (list != null && list.isJsonArray()) {
                JsonArray supported = new JsonArray();
                for (JsonElement element : list.getAsJsonArray()) if (element.isJsonObject()) {
                    JsonObject modifier = element.getAsJsonObject();
                    String type = ItemEntry.string(modifier, "type");
                    for (String prefix : List.of("minecraft:generic.", "minecraft:player.")) {
                        if (type.startsWith(prefix)) {
                            String current = "minecraft:" + type.substring(prefix.length());
                            Identifier id = Identifier.tryParse(current);
                            if (id != null && BuiltInRegistries.ATTRIBUTE.containsKey(id)) {
                                modifier.addProperty("type", current);
                                type = current;
                            }
                        }
                    }
                    Identifier id = Identifier.tryParse(type);
                    if (id != null && BuiltInRegistries.ATTRIBUTE.containsKey(id)) supported.add(modifier);
                    else appendLegacyLine(components, "Server attribute: " + type);
                }
                if (supported.isEmpty()) components.remove("minecraft:attribute_modifiers");
                else components.add("minecraft:attribute_modifiers", supported);
            }
        }
        JsonElement model = components.get("minecraft:custom_model_data");
        if (model != null && model.isJsonPrimitive() && model.getAsJsonPrimitive().isNumber()) {
            JsonArray floats = new JsonArray(); floats.add(model.getAsFloat());
            JsonObject converted = new JsonObject(); converted.add("floats", floats);
            components.add("minecraft:custom_model_data", converted);
        }
        JsonElement dye = components.get("minecraft:dyed_color");
        if (dye != null && dye.isJsonObject() && dye.getAsJsonObject().has("rgb"))
            components.add("minecraft:dyed_color", dye.getAsJsonObject().get("rgb"));
        for (String name : List.of("minecraft:custom_name", "minecraft:item_name")) {
            JsonElement component = components.get(name);
            if (component != null && component.isJsonPrimitive() && component.getAsJsonPrimitive().isString()) {
                String text = component.getAsString();
                components.add(name, TextRepair.decodeComponentString(text));
            }
        }
        JsonElement lore = components.get("minecraft:lore");
        if (lore != null && lore.isJsonArray()) {
            JsonArray normalized = new JsonArray();
            for (JsonElement line : lore.getAsJsonArray()) {
                if (line.isJsonPrimitive() && line.getAsJsonPrimitive().isString()) {
                    normalized.add(TextRepair.decodeComponentString(line.getAsString()));
                } else normalized.add(line);
            }
            components.add("minecraft:lore", normalized);
        }
        for (String name : List.of("minecraft:custom_name", "minecraft:item_name", "minecraft:lore")) {
            JsonElement text = components.get(name);
            if (text != null) components.add(name, TextRepair.fixJson(text));
        }
        for (String name : List.of("minecraft:enchantments", "minecraft:stored_enchantments")) {
            JsonElement enchantments = components.get(name);
            if (enchantments == null || !enchantments.isJsonObject()) continue;
            var registry = access.lookup(Registries.ENCHANTMENT);
            if (registry.isEmpty()) continue;
            JsonObject supported = new JsonObject();
            for (var enchantment : enchantments.getAsJsonObject().entrySet()) {
                Identifier id = Identifier.tryParse(enchantment.getKey());
                if (id != null && registry.get().containsKey(id)) supported.add(enchantment.getKey(), enchantment.getValue());
                else appendLegacyLine(components, "Server enchantment: " + enchantment.getKey() + " " + enchantment.getValue().getAsString());
            }
            if (supported.isEmpty()) components.remove(name);
            else components.add(name, supported);
        }
        JsonElement container = components.get("minecraft:container");
        if (container != null && container.isJsonArray()) {
            for (JsonElement slot : container.getAsJsonArray()) {
                if (!slot.isJsonObject()) continue;
                JsonElement nested = slot.getAsJsonObject().get("item");
                if (nested != null && nested.isJsonObject()) normalizeComponents(nested.getAsJsonObject(), access, depth + 1);
            }
        }
    }

    private static void appendLegacyLine(JsonObject components, String text) {
        JsonElement lore = components.get("minecraft:lore");
        JsonArray lines = lore != null && lore.isJsonArray() ? lore.getAsJsonArray() : new JsonArray();
        JsonObject line = new JsonObject();
        line.addProperty("text", TextRepair.fix(text));
        line.addProperty("color", "gray");
        line.addProperty("italic", false);
        lines.add(line);
        components.add("minecraft:lore", lines);
    }

    private static void diagnostic(ItemEntry entry, String reason) {
        if (diagnostics++ < 50) LOG.warn("Item reconstruction {} {}: {}", entry.miscellaneous ? "misc" : "crate", entry.id,
                reason.length() > 200 ? reason.substring(0, 200) + "..." : reason);
        else if (diagnostics == 51) LOG.warn("Further item reconstruction diagnostics suppressed");
    }

    private static ItemStack fallback(ItemEntry entry, JsonObject raw) {
        Item item = Items.BARRIER;
        Identifier id = Identifier.tryParse(entry.minecraftId);
        if (id != null) {
            Item found = BuiltInRegistries.ITEM.getValue(id);
            if (found != null && found != Items.AIR) item = found;
        }
        int count = raw == null ? 1 : Math.max(1, Math.min(99, ItemEntry.number(raw, "count")));
        ItemStack stack = new ItemStack(item, count);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(entry.name.isBlank() ? "Unknown MysticMC item" : entry.name));
        if (raw != null && raw.has("components") && raw.get("components").isJsonObject()) {
            JsonElement lore = raw.getAsJsonObject("components").get("minecraft:lore");
            if (lore != null && lore.isJsonArray()) {
                List<Component> lines = new ArrayList<>();
                for (JsonElement line : lore.getAsJsonArray()) {
                    try {
                        JsonElement decoded = line;
                        if (line.isJsonPrimitive() && line.getAsJsonPrimitive().isString())
                            decoded = TextRepair.decodeComponentString(line.getAsString());
                        lines.add(ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, decoded).result().orElse(Component.literal(line.toString())));
                    }
                    catch (RuntimeException ignored) { }
                }
                stack.set(DataComponents.LORE, new ItemLore(lines));
            }
        }
        return stack;
    }
}
