package co.mysticmc.items.command;

import co.mysticmc.items.data.ItemDatabase;
import co.mysticmc.items.data.ItemEntry;
import co.mysticmc.items.data.ItemRepository;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/** Looks up catalog details for a MysticMC item name. */
public final class WhatIsThatCommand {
    private WhatIsThatCommand() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, ItemRepository repository) {
        dispatcher.register(ClientCommands.literal("whatisthat")
                .executes(context -> {
                    context.getSource().sendFeedback(Component.literal("Usage: /whatisthat <item name>"));
                    return 0;
                })
                .then(ClientCommands.argument("item name", StringArgumentType.greedyString())
                        .suggests((context, builder) -> {
                            ItemDatabase database = repository.database();
                            if (database == null) return builder.buildFuture();
                            String typed = canonical(builder.getRemaining());
                            database.items.stream().map(entry -> cleanName(entry.name))
                                    .filter(name -> !name.isEmpty())
                                    .distinct()
                                    .filter(name -> canonical(name).startsWith(typed))
                                    .sorted(String.CASE_INSENSITIVE_ORDER)
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> lookup(context.getSource(), repository,
                                StringArgumentType.getString(context, "item name")))));
    }

    private static int lookup(FabricClientCommandSource source, ItemRepository repository, String input) {
        ItemDatabase database = repository.database();
        if (database == null) {
            source.sendFeedback(Component.literal("Item catalog is still loading. Try again shortly."));
            return 0;
        }
        String needle = canonical(input);
        if (needle.isEmpty()) {
            source.sendFeedback(Component.literal("Enter an item name after /whatisthat."));
            return 0;
        }
        List<ItemEntry> matches = matches(database.items, needle);
        if (matches.isEmpty()) {
            source.sendFeedback(Component.literal("No item named " + cleanName(input) + " was found."));
            return 0;
        }
        String firstName = canonical(matches.getFirst().name);
        if (matches.stream().anyMatch(entry -> !canonical(entry.name).equals(firstName))) {
            String names = matches.stream().map(entry -> cleanName(entry.name)).distinct().limit(8)
                    .reduce((left, right) -> left + ", " + right).orElse("");
            source.sendFeedback(Component.literal("Several names match: " + names
                    + (matches.stream().map(entry -> cleanName(entry.name)).distinct().count() > 8 ? ", ..." : "")
                    + ". Use Tab or enter the full name."));
            return 0;
        }
        for (ItemEntry entry : matches) {
            for (Component line : details(entry)) source.sendFeedback(line);
        }
        return 1;
    }

    static List<Component> details(ItemEntry entry) {
        URI siteUri = siteUri(entry);
        return List.of(
                copyLine("Item Name: ", ChatFormatting.AQUA, entry.name),
                copyLine("Base Item: ", ChatFormatting.GOLD, entry.minecraftId),
                copyLine("ei-id: ", ChatFormatting.LIGHT_PURPLE, entry.executableItemId),
                line("Type: ", ChatFormatting.GREEN, entry.miscellaneous ? "Miscellaneous Item" : "Item", null, null),
                line("Database ID: ", ChatFormatting.YELLOW, Integer.toString(entry.id),
                        siteUri == null ? null : new ClickEvent.OpenUrl(siteUri), "Open item page"));
    }

    private static Component copyLine(String label, ChatFormatting color, String value) {
        return line(label, color, value.isBlank() ? "N/A" : value,
                value.isBlank() ? null : new ClickEvent.CopyToClipboard(value), "Copy to clipboard");
    }

    private static Component line(String label, ChatFormatting color, String value, ClickEvent click, String hint) {
        MutableComponent result = Component.literal(label).withStyle(color);
        MutableComponent valueText = Component.literal(value).withStyle(ChatFormatting.WHITE);
        if (click != null) valueText.withStyle(style -> style.withUnderlined(true).withClickEvent(click)
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hint))));
        return result.append(valueText);
    }

    static URI siteUri(ItemEntry entry) {
        if (entry.miscellaneous) {
            if (entry.urlTag.isBlank()) return null;
            return URI.create("https://items.mysticmc.co/miscitems/group/"
                    + URLEncoder.encode(entry.urlTag, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return entry.id < 0 ? null : URI.create("https://items.mysticmc.co/items/item/" + entry.id);
    }

    static List<ItemEntry> matches(List<ItemEntry> items, String needle) {
        List<ItemEntry> exact = items.stream().filter(entry -> canonical(entry.name).equals(needle)).toList();
        if (!exact.isEmpty()) return exact;
        return items.stream().filter(entry -> canonical(entry.name).startsWith(needle)).toList();
    }

    static String cleanName(String text) {
        return text.replaceAll("\\p{P}", "")
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ")
                .trim().replaceAll("\\s+", " ");
    }

    static String canonical(String text) {
        return text.replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase(Locale.ROOT);
    }
}
