package co.mysticmc.items.command;

import co.mysticmc.items.data.ItemEntry;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
