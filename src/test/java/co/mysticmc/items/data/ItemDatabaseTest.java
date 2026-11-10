package co.mysticmc.items.data;

import co.mysticmc.items.gui.BrowserState;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemDatabaseTest {
    private static ItemDatabase sample() {
        JsonObject root = JsonParser.parseString("""
            {"format":1,"syncedAt":"2026-11-08T00:00:00Z",
             "items":{"data":[
               {"id":1,"CrateID":"1","ItemName":"Mystic Sword","ItemHuman":"Right-Click to make animals breed around you.","RawData":"invalid","TagPrimary":"Sword","TagSeptenary":"Speed Attribute","RarityHuman":"Rare"},
               {"id":2,"CrateID":"1","ItemName":"Mystic Axe","RawData":"invalid","TagQuinary":"Axe","RarityHuman":"Exotic"}]},
             "miscitems":{"data":[{"id":3,"GroupID":"2","ItemName":"Vote Key","RawData":"invalid"}]},
             "crates":[{"id":1,"CrateName":"Origins"}],"miscgroups":[{"id":2,"GroupName":"Currency"}],"tags":["Sword","Speed Attribute","Axe"]}
            """).getAsJsonObject();
        return new ItemDatabase(root);
    }

    @Test void combinesSearchAndSourceWithAssociations() {
        ItemDatabase db = sample();
        BrowserState state = new BrowserState();
        state.query = "currency";
        assertEquals(1, state.filter(db).size());
        assertTrue(state.filter(db).getFirst().miscellaneous);
        state.source = 1;
        assertTrue(state.filter(db).isEmpty());
        state.query = "mYsTiC";
        assertEquals(2, state.filter(db).size());
    }

    @Test void searchesPlainTextItemDescription() {
        ItemDatabase db = sample();
        BrowserState state = new BrowserState();
        state.query = "breed";
        assertEquals(1, state.filter(db).size());
        assertEquals("Mystic Sword", state.filter(db).getFirst().name);
    }

    @Test void searchesInstrumentNameShownByVanillaTooltip() {
        JsonObject raw = new JsonObject();
        raw.addProperty("id", 706);
        raw.addProperty("ItemName", "Kindlehoof");
        JsonObject stack = new JsonObject();
        stack.addProperty("id", "minecraft:goat_horn");
        JsonObject components = new JsonObject();
        components.addProperty("minecraft:instrument", "minecraft:ponder_goat_horn");
        stack.add("components", components);
        raw.addProperty("RawData", stack.toString());
        ItemEntry entry = new ItemEntry(raw, false, null);
        assertTrue(entry.searchText.contains("ponder"));
        assertFalse(entry.searchText.contains("minecraft:ponder_goat_horn"));
    }

    @Test void matchesTagsAcrossSlotsWithAndOr() {
        ItemDatabase db = sample();
        BrowserState state = new BrowserState();
        state.tags.add("Sword"); state.tags.add("Speed Attribute");
        assertEquals(1, state.filter(db).size());
        state.requireAllTags = true;
        assertEquals(1, state.filter(db).size());
        state.tags.add("Axe");
        assertTrue(state.filter(db).isEmpty());
        state.requireAllTags = false;
        assertEquals(2, state.filter(db).size());
    }

    @Test void clearResetsEveryFilterAndRarityCyclesFromAny() {
        ItemDatabase db = sample();
        BrowserState state = new BrowserState();
        state.nextRarity();
        assertEquals("Rare", state.rarity);
        state.nextRarity();
        assertEquals("Legendary", state.rarity);
        state.nextRarity();
        assertEquals("Exotic", state.rarity);
        state.nextRarity();
        assertEquals("Mystic", state.rarity);
        state.nextRarity();
        assertEquals("Eternal", state.rarity);
        state.nextRarity();
        assertEquals("", state.rarity);
        state.rarity = "rare";
        assertEquals(1, state.filter(db).size());
        state.rarity = "ETERNAL";
        state.nextRarity();
        assertEquals("", state.rarity);
        state.query = "sword";
        state.tags.add("Sword");
        state.crate = "Origins";
        state.source = 1;
        state.clear();
        assertEquals("", state.query);
        assertTrue(state.tags.isEmpty());
        assertEquals(0, state.source);
        assertEquals("", state.crate);
        assertEquals(3, state.filter(db).size());
    }
}
