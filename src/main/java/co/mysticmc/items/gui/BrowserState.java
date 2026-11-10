package co.mysticmc.items.gui;

import co.mysticmc.items.data.ItemDatabase;
import co.mysticmc.items.data.ItemEntry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class BrowserState {
    private static final List<String> RARITIES = List.of("Rare", "Legendary", "Exotic", "Mystic", "Eternal");
    public String query = "";
    public final Set<String> tags = new HashSet<>();
    public boolean requireAllTags;
    public int source; // 0 all, 1 crate, 2 miscellaneous
    public String crate = "";
    public String group = "";
    public String rarity = "";

    public List<ItemEntry> filter(ItemDatabase database) {
        if (database == null) return List.of();
        List<ItemEntry> result = new ArrayList<>();
        String needle = query.toLowerCase(Locale.ROOT).trim();
        for (ItemEntry item : database.items) {
            if (!needle.isEmpty() && !item.searchText.contains(needle)) continue;
            if (source == 1 && item.miscellaneous || source == 2 && !item.miscellaneous) continue;
            if (!crate.isEmpty() && (item.miscellaneous || !crate.equals(item.groupName))) continue;
            if (!group.isEmpty() && (!item.miscellaneous || !group.equals(item.groupName))) continue;
            if (!rarity.isEmpty() && !rarity.equalsIgnoreCase(item.rarity.trim())) continue;
            if (!tags.isEmpty()) {
                if (requireAllTags ? !item.tags.containsAll(tags) : item.tags.stream().noneMatch(tags::contains)) continue;
            }
            result.add(item);
        }
        return result;
    }

    public void nextSource() {
        source = (source + 1) % 3;
        if (source == 1) group = "";
        if (source == 2) crate = "";
    }

    public String sourceLabel() {
        return switch (source) { case 1 -> "Crate items"; case 2 -> "Misc items"; default -> "All items"; };
    }

    public void nextRarity() {
        int current = -1;
        for (int i = 0; i < RARITIES.size(); i++) if (RARITIES.get(i).equalsIgnoreCase(rarity)) current = i;
        int next = current + 1;
        rarity = next >= RARITIES.size() ? "" : RARITIES.get(next);
    }

    public void clear() { query = ""; tags.clear(); requireAllTags = false; source = 0; crate = ""; group = ""; rarity = ""; }
}
