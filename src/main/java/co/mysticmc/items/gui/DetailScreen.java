package co.mysticmc.items.gui;

import co.mysticmc.items.MysticItemBrowser;
import co.mysticmc.items.data.ItemEntry;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class DetailScreen extends Screen {
    private final MysticItemBrowser mod;
    private final BrowserState state;
    private final ItemEntry entry;
    public DetailScreen(MysticItemBrowser mod, BrowserState state, ItemEntry entry) {
        super(Component.literal("MysticMC Item Details")); this.mod = mod; this.state = state; this.entry = entry;
    }
    @Override protected void init() {
        int x = width / 2 - 110;
        int y = height / 2 + 55;
        addRenderableWidget(Button.builder(Component.literal("Add to Preview"), b -> mod.addToPreview(entry))
                .bounds(x, y, 105, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(x + 110, y, 105, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Browse site"), b -> {
            try { net.minecraft.util.Util.getPlatform().openUri(siteUri()); }
            catch (Exception ignored) { }
        }).bounds(x, y + 24, 105, 20).build());
    }
    private URI siteUri() {
        if (!entry.miscellaneous) return URI.create("https://items.mysticmc.co/items/item/" + entry.id);
        return URI.create("https://items.mysticmc.co/miscitems/group/"
                + URLEncoder.encode(entry.urlTag, StandardCharsets.UTF_8).replace("+", "%20"));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xE014141A);
        int x = width / 2 - 110;
        int y = height / 2 - 80;
        ItemStack stack = mod.stacks().get(entry);
        g.item(stack, x, y);
        if (mx >= x && mx < x + 18 && my >= y && my < y + 18) g.setTooltipForNextFrame(font, stack, mx, my);
        g.text(font, entry.name, x + 23, y + 4, 0xFFFFFFFF);
        g.text(font, "ID: " + entry.minecraftId, x, y + 25, 0xFFDDDDDD);
        g.text(font, "Source: " + (entry.miscellaneous ? "Miscellaneous" : "Crate") + " / " + entry.groupName, x, y + 38, 0xFFDDDDDD);
        g.text(font, "Rarity: " + (entry.rarity.isBlank() ? "N/A" : entry.rarity), x, y + 51, 0xFFDDDDDD);
        g.text(font, "Tags: " + (entry.tags.isEmpty() ? "None" : String.join(", ", entry.tags)), x, y + 64, 0xFFBBCCFF);
        g.textWithWordWrap(font, Component.literal("Notes: " + entry.notes), x, y + 79, 230, 48);
        super.extractRenderState(g, mx, my, delta);
    }
    @Override public void onClose() { minecraft.setScreen(new BrowserScreen(mod, state)); }
}
