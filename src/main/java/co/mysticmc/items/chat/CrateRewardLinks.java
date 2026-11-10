package co.mysticmc.items.chat;

import co.mysticmc.items.MysticItemBrowser;
import co.mysticmc.items.data.ItemDatabase;
import co.mysticmc.items.data.ItemEntry;
import co.mysticmc.items.gui.DetailScreen;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Adds a local item preview to crate reward names in server game messages. */
public final class CrateRewardLinks {
    private static final Pattern REWARD = Pattern.compile(
            "^\\s*Crates\\s*[»>]\\s*.*?\\bgot\\s+the\\s+(.+?)\\s+reward\\s+from\\s+the\\s+(.+?)\\s*[.!]?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern LABELED_CRATE = Pattern.compile("(?i)^.*?\\bcrate\\b\\s*[-–—:]\\s*(.+)$");
    private static final Pattern TRAILING_CRATE = Pattern.compile("(?i)^(.+?)\\s+crate$");

    private CrateRewardLinks() {}

    public static void initialize(MysticItemBrowser mod) {
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
            if (overlay) return message;
            ItemDatabase database = mod.repository().database();
            if (database == null) return message;
            Match match = match(message.getString(), database.items);
            if (match == null) return message;
            ItemStack stack = mod.stacks().get(match.entry());
            if (stack.isEmpty()) return message;
            HoverEvent hover = new HoverEvent.ShowItem(ItemStackTemplate.fromNonEmptyStack(stack));
            ClickEvent click = new ClickEvent.RunCommand("/itemlist show "
                    + (match.entry().miscellaneous ? "misc " : "crate ") + match.entry().id);
            return decorate(message, match.start(), match.end(), hover, click);
        });
    }

    /** Prefers a crate match, then falls back to the first catalog item with that name. */
    static Match match(String text, List<ItemEntry> entries) {
        Matcher message = REWARD.matcher(text);
        if (!message.matches()) return null;
        String wanted = canonical(message.group(1));
        final String crateHint = canonical(crateHint(message.group(2)));
        List<ItemEntry> candidates = entries.stream()
                .filter(entry -> canonical(entry.name).equals(wanted)).toList();
        if (candidates.isEmpty()) return null;
        ItemEntry selected = candidates.getFirst();
        if (!crateHint.isEmpty()) {
            List<ItemEntry> inCrate = candidates.stream()
                    .filter(entry -> !entry.miscellaneous && !canonical(entry.groupName).isEmpty()
                            && (canonical(entry.groupName).contains(crateHint)
                            || crateHint.contains(canonical(entry.groupName))))
                    .toList();
            if (!inCrate.isEmpty()) selected = inCrate.getFirst();
        }
        return new Match(message.start(1), message.end(1), selected);
    }

    private static String crateHint(String source) {
        String name = source.trim();
        Matcher labeled = LABELED_CRATE.matcher(name);
        if (labeled.matches()) return labeled.group(1);
        Matcher trailing = TRAILING_CRATE.matcher(name);
        return trailing.matches() ? trailing.group(1) : name;
    }

    private static String canonical(String text) {
        return text.replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase(Locale.ROOT);
    }

    static Component decorate(Component message, int start, int end, HoverEvent hover, ClickEvent click) {
        MutableComponent result = Component.empty();
        int[] offset = {0};
        message.visit((style, text) -> {
            int segmentStart = offset[0];
            int segmentEnd = segmentStart + text.length();
            int markedStart = Math.max(start, segmentStart);
            int markedEnd = Math.min(end, segmentEnd);
            if (markedStart >= markedEnd) {
                result.append(Component.literal(text).withStyle(style));
            } else {
                if (segmentStart < markedStart) result.append(Component.literal(text.substring(0, markedStart - segmentStart)).withStyle(style));
                result.append(Component.literal(text.substring(markedStart - segmentStart, markedEnd - segmentStart))
                        .withStyle(style.withHoverEvent(hover).withClickEvent(click)));
                if (markedEnd < segmentEnd) result.append(Component.literal(text.substring(markedEnd - segmentStart)).withStyle(style));
            }
            offset[0] = segmentEnd;
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    public static void open(MysticItemBrowser mod, boolean miscellaneous, int id) {
        ItemDatabase database = mod.repository().database();
        if (database == null) return;
        for (ItemEntry entry : database.items) {
            if (entry.miscellaneous == miscellaneous && entry.id == id) {
                Minecraft.getInstance().setScreen(new DetailScreen(mod, mod.browserState(), entry));
                return;
            }
        }
    }

    record Match(int start, int end, ItemEntry entry) {}
}
