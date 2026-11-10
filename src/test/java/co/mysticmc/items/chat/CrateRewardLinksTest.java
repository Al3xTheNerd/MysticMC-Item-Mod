package co.mysticmc.items.chat;

import co.mysticmc.items.data.ItemEntry;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CrateRewardLinksTest {
    private static ItemEntry item(int id, String name, String crate) {
        JsonObject row = new JsonObject();
        row.addProperty("id", id);
        row.addProperty("ItemName", name);
        row.addProperty("RawData", "{\"id\":\"minecraft:cyan_concrete\"}");
        JsonObject group = new JsonObject();
        group.addProperty("CrateName", crate);
        return new ItemEntry(row, false, group);
    }

    @Test void matchesOwnAndGlobalCrateAnnouncements() {
        ItemEntry reward = item(325, "■ INFINITE CYAN CONCRETE ■", "Infinite Concrete");
        String own = "Crates » You got the ■ INFINITE CYAN CONCRETE ■ reward from the CONCRETE CRATE!";
        String global = "Crates » Player AlexTheNerd just got the ■ INFINITE CYAN CONCRETE ■ reward from the CONCRETE CRATE!";
        for (String text : List.of(own, global)) {
            CrateRewardLinks.Match match = CrateRewardLinks.match(text, List.of(reward));
            assertNotNull(match);
            assertSame(reward, match.entry());
            assertEquals("■ INFINITE CYAN CONCRETE ■", text.substring(match.start(), match.end()));
        }
        assertNull(CrateRewardLinks.match("Player says got the same reward from the CONCRETE CRATE!", List.of(reward)));
    }

    @Test void resolvesDuplicateNamesByCrateAndKeepsOriginalTextStyling() {
        ItemEntry concrete = item(325, "■ INFINITE CYAN CONCRETE ■", "Infinite Concrete");
        ItemEntry other = item(999, "■ INFINITE CYAN CONCRETE ■", "Rainbow Blocks");
        String text = "Crates » You got the ■ INFINITE CYAN CONCRETE ■ reward from the CONCRETE CRATE!";
        CrateRewardLinks.Match match = CrateRewardLinks.match(text, List.of(other, concrete));
        assertNotNull(match);
        assertSame(concrete, match.entry());

        Component original = Component.literal("Crates » You got the ")
                .append(Component.literal("■ INFINITE CYAN CONCRETE ■").withStyle(Style.EMPTY.withBold(true)))
                .append(" reward from the CONCRETE CRATE!");
        HoverEvent hover = new HoverEvent.ShowText(Component.literal("preview"));
        ClickEvent click = new ClickEvent.RunCommand("/itemlist show crate 325");
        Component linked = CrateRewardLinks.decorate(original, match.start(), match.end(), hover, click);
        assertEquals(original.getString(), linked.getString());
        List<String> linkedText = new ArrayList<>();
        linked.visit((style, segment) -> {
            if (style.getClickEvent() == click && style.getHoverEvent() == hover) {
                assertTrue(style.isBold());
                linkedText.add(segment);
            }
            return Optional.empty();
        }, Style.EMPTY);
        assertEquals(List.of("■ INFINITE CYAN CONCRETE ■"), linkedText);
    }

    @Test void matchesSeasonLabeledMysticCrateAnnouncement() {
        ItemEntry primordial = item(662, "✦ Mud Cycler ✦", "Season 9: Primordial");
        ItemEntry anotherSeason = item(1000, "✦ Mud Cycler ✦", "Season 8: Mythic");
        String announcement = "Crates » Player Big_Schmittyus just got the ✦ Mud Cycler ✦ "
                + "reward from the MYSTIC CRATE - PRIMORDIAL!";

        CrateRewardLinks.Match match = CrateRewardLinks.match(announcement, List.of(anotherSeason, primordial));
        assertNotNull(match);
        assertSame(primordial, match.entry());
        assertEquals("✦ Mud Cycler ✦", announcement.substring(match.start(), match.end()));
    }

    @Test void fallsBackToItemNameWhenCrateCannotBeIdentified() {
        ItemEntry first = item(662, "✦ Mud Cycler ✦", "Season 9: Primordial");
        ItemEntry second = item(1000, "✦ Mud Cycler ✦", "Season 8: Mythic");
        String announcement = "Crates » Player Big_Schmittyus just got the ✦ Mud Cycler ✦ "
                + "reward from the UNKNOWN CRATE!";

        CrateRewardLinks.Match match = CrateRewardLinks.match(announcement, List.of(first, second));
        assertNotNull(match);
        assertSame(first, match.entry());
    }

    @Test void matchesBloomfallAnnouncementsWithoutCrateWord() {
        ItemEntry guitar = item(525, "✿ Verdant Guitar ✿", "Bloomfall");
        ItemEntry leggings = item(537, "✿ Amethyst Leggings ✿", "Bloomfall");
        ItemEntry otherGuitar = item(999, "✿ Verdant Guitar ✿", "Other Crate");
        String first = "Crates » Player LadyLoveless just got the ✿ Verdant Guitar ✿ reward from the BLOOMFALL!";
        String second = "Crates » Player LadyLoveless just got the ✿ Amethyst Leggings ✿ reward from the BLOOMFALL!";

        CrateRewardLinks.Match guitarMatch = CrateRewardLinks.match(first, List.of(otherGuitar, guitar, leggings));
        assertNotNull(guitarMatch);
        assertSame(guitar, guitarMatch.entry());
        assertEquals("✿ Verdant Guitar ✿", first.substring(guitarMatch.start(), guitarMatch.end()));

        CrateRewardLinks.Match leggingsMatch = CrateRewardLinks.match(second, List.of(guitar, leggings));
        assertNotNull(leggingsMatch);
        assertSame(leggings, leggingsMatch.entry());
        assertEquals("✿ Amethyst Leggings ✿", second.substring(leggingsMatch.start(), leggingsMatch.end()));
    }
}
