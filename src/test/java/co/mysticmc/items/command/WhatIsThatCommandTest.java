package co.mysticmc.items.command;

import co.mysticmc.items.data.ItemEntry;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WhatIsThatCommandTest {
    private static ItemEntry item(int id, String name, String minecraftId) {
        JsonObject row = new JsonObject();
        row.addProperty("id", id);
        row.addProperty("ItemName", name);
        JsonObject stack = new JsonObject();
        stack.addProperty("id", minecraftId);
        row.addProperty("RawData", stack.toString());
        return new ItemEntry(row, false, null);
    }

    @Test void trimsDecorationsAndMatchesNamesRegardlessOfPunctuation() {
        ItemEntry kindlehoof = item(706, "✦ Kindlehoof ✦", "minecraft:goat_horn");
        ItemEntry charm = item(912, "✦ Freyja's Charm ✦", "minecraft:amethyst_shard");
        assertEquals("Kindlehoof", WhatIsThatCommand.cleanName(kindlehoof.name));
        assertEquals("Freyjas Charm", WhatIsThatCommand.cleanName(charm.name));
        assertEquals(List.of(kindlehoof), WhatIsThatCommand.matches(List.of(kindlehoof, charm),
                WhatIsThatCommand.canonical("✦ Kindlehoof ✦")));
        assertEquals(List.of(charm), WhatIsThatCommand.matches(List.of(kindlehoof, charm),
                WhatIsThatCommand.canonical("freyjas charm")));
    }

    @Test void exactNameTakesPriorityButKeepsAllDuplicateEntries() {
        ItemEntry sword = item(1, "✦ Mystic Sword ✦", "minecraft:diamond_sword");
        ItemEntry otherSword = item(2, "★ Mystic Sword ★", "minecraft:netherite_sword");
        ItemEntry longer = item(3, "Mystic Sword Replica", "minecraft:stick");
        assertEquals(List.of(sword, otherSword), WhatIsThatCommand.matches(List.of(sword, otherSword, longer),
                WhatIsThatCommand.canonical("mystic sword")));
    }

    @Test void showsAllRequestedFieldsFromRawItemData() {
        JsonObject row = new JsonObject();
        row.addProperty("id", 412);
        row.addProperty("ItemName", "✦ Odin's Brush ✦");
        JsonObject stack = new JsonObject();
        stack.addProperty("id", "minecraft:brush");
        JsonObject bukkitValues = new JsonObject();
        bukkitValues.addProperty("executableitems:ei-id", "VALHALLA_ODINSBRUSH");
        JsonObject customData = new JsonObject();
        customData.add("PublicBukkitValues", bukkitValues);
        JsonObject components = new JsonObject();
        components.add("minecraft:custom_data", customData);
        stack.add("components", components);
        row.addProperty("RawData", stack.toString());

        JsonObject group = new JsonObject();
        group.addProperty("URLTag", "Valhalla Stuff");
        ItemEntry entry = new ItemEntry(row, true, group);
        List<Component> details = WhatIsThatCommand.details(entry);
        assertEquals(List.of(
                "Item Name: ✦ Odin's Brush ✦",
                "Base Item: minecraft:brush",
                "ei-id: VALHALLA_ODINSBRUSH",
                "Type: Miscellaneous Item",
                "Database ID: 412"), details.stream().map(Component::getString).toList());
        assertEquals(5, details.stream().map(line -> line.getStyle().getColor()).distinct().count());
        assertEquals(new ClickEvent.CopyToClipboard("✦ Odin's Brush ✦"), valueClick(details.get(0)));
        assertEquals(new ClickEvent.CopyToClipboard("minecraft:brush"), valueClick(details.get(1)));
        assertEquals(new ClickEvent.CopyToClipboard("VALHALLA_ODINSBRUSH"), valueClick(details.get(2)));
        assertNull(valueClick(details.get(3)));
        assertEquals(new ClickEvent.OpenUrl(URI.create("https://items.mysticmc.co/miscitems/group/Valhalla%20Stuff")),
                valueClick(details.get(4)));
    }

    @Test void showsUnavailableIdForItemsWithoutExecutableItemsData() {
        ItemEntry entry = item(706, "Kindlehoof", "minecraft:goat_horn");
        List<Component> details = WhatIsThatCommand.details(entry);
        assertEquals(List.of(
                "Item Name: Kindlehoof",
                "Base Item: minecraft:goat_horn",
                "ei-id: N/A",
                "Type: Item",
                "Database ID: 706"), details.stream().map(Component::getString).toList());
        assertNull(valueClick(details.get(2)));
        assertEquals(new ClickEvent.OpenUrl(URI.create("https://items.mysticmc.co/items/item/706")),
                valueClick(details.get(4)));
    }

    private static ClickEvent valueClick(Component line) {
        return line.getSiblings().getFirst().getStyle().getClickEvent();
    }
}
