package co.mysticmc.items.data;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TextRepairTest {
    @Test void repairsMojibakeWithoutChangingNormalText() {
        assertEquals("✦ Knockback Fish ✦", TextRepair.fix("âœ¦ Knockback Fish âœ¦"));
        assertEquals("Items — Shift-click", TextRepair.fix("Items â€” Shift-click"));
        assertEquals("Café ✦", TextRepair.fix("Café âœ¦"));
        assertEquals("normal text", TextRepair.fix("normal text"));
    }

    @Test void repairsNestedComponentText() {
        JsonObject component = new JsonObject();
        component.addProperty("text", "â€‹Visible text");
        JsonObject repaired = TextRepair.fixJson(component).getAsJsonObject();
        assertEquals("\u200BVisible text", repaired.get("text").getAsString());
    }

    @Test void decodesSerializedBlankLoreLines() {
        assertEquals("", TextRepair.decodeComponentString("\"\"").getAsString());
        assertEquals("plain lore", TextRepair.decodeComponentString("plain lore").getAsString());
    }
}
