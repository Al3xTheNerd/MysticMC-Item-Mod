package co.mysticmc.items.command;

import co.mysticmc.items.data.ItemDatabase;
import co.mysticmc.items.data.ItemEntry;
import co.mysticmc.items.data.ItemRepository;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Looks up the Minecraft registry ID behind a MysticMC catalog name. */
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
        Map<String, ItemEntry> ids = new LinkedHashMap<>();
        for (ItemEntry entry : matches) ids.putIfAbsent(entry.minecraftId, entry);
        source.sendFeedback(Component.literal(cleanName(matches.getFirst().name) + ": "
                + (ids.size() == 1 ? "Minecraft item ID" : ids.size() + " Minecraft item IDs")));
        for (ItemEntry entry : ids.values()) {
            source.sendFeedback(Component.literal("  " + (entry.minecraftId.isBlank() ? "unknown" : entry.minecraftId)
                    + " (catalog " + (entry.miscellaneous ? "misc" : "crate") + " #" + entry.id + ")"));
        }
        return 1;
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
