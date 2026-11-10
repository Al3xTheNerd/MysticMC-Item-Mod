package co.mysticmc.items.gui;

import co.mysticmc.items.MysticItemBrowser;
import co.mysticmc.items.data.ItemDatabase;
import co.mysticmc.items.data.ItemEntry;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class BrowserScreen extends Screen {
    private final MysticItemBrowser mod;
    private final BrowserState state;
    private EditBox search;
    private List<ItemEntry> matches = List.of();
    private ItemDatabase displayed;
    private int scroll;
    private int left;
    private int top;
    private int columns;
    private int rows;
    private int controlsX;

    public BrowserScreen(MysticItemBrowser mod, BrowserState state) {
        super(Component.literal("MysticMC Item Browser"));
        this.mod = mod; this.state = state;
    }

    @Override protected void init() {
        controlsX = Math.max(1, (width - 317) / 2);
        top = 94;
        columns = Math.max(5, Math.min(13, (width - 24) / 22));
        rows = Math.max(1, (height - top - 36) / 22);
        left = (width - columns * 22) / 2;
        search = addRenderableWidget(new EditBox(font, controlsX, 22, Math.min(190, width - 8), 18, Component.literal("Search items")));
        search.setValue(state.query);
        search.setResponder(value -> { state.query = value; updateMatches(); });
        addRenderableWidget(Button.builder(Component.literal("Tags"), b -> minecraft.setScreen(new TagScreen(mod, state, this)))
                .bounds(controlsX, 45, 47, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Source"), b -> {
            state.nextSource(); updateMatches();
        }).bounds(controlsX + 50, 45, 53, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Crate"), b -> minecraft.setScreen(new FilterOptionScreen(mod, state, this, FilterOptionScreen.Kind.CRATE)))
                .bounds(controlsX + 106, 45, 49, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Group"), b -> minecraft.setScreen(new FilterOptionScreen(mod, state, this, FilterOptionScreen.Kind.GROUP)))
                .bounds(controlsX + 158, 45, 51, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Rarity"), b -> {
            state.nextRarity(); updateMatches();
        })
                .bounds(controlsX + 212, 45, 54, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Clear"), b -> { state.clear(); search.setValue(""); updateMatches(); })
                .bounds(controlsX + 269, 45, 48, 18).build());
        int bottom = Math.min(height - 25, top + rows * 22 + 12);
        addRenderableWidget(Button.builder(Component.literal("Preview"), b -> mod.showPreview())
                .bounds(left, bottom, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> mod.refreshWithChat())
                .bounds(left + 84, bottom, 75, 20).build());
        updateMatches();
    }

    public void updateMatches() {
        displayed = mod.repository().database();
        matches = state.filter(displayed);
        scroll = 0;
    }

    @Override public void tick() {
        if (displayed != mod.repository().database()) updateMatches();
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xD0101018);
        graphics.centeredText(font, title, width / 2, 7, 0xFFFFFFFF);
        String summary = matches.size() + " items" + (mod.repository().isCached() ? " (cached)" : "")
                + " | Source: " + state.sourceLabel() + " | Tags: " + state.tags.size();
        graphics.text(font, font.plainSubstrByWidth(summary, columns * 22), left, 67, 0xFFDDDDDD);
        if (displayed == null) graphics.centeredText(font, mod.repository().status() + " - Refresh to retry", width / 2, height / 2, 0xFFFFAAAA);
        String filters = String.join(" | ", List.of(
                state.crate.isEmpty() ? "" : "Crate: " + state.crate,
                state.group.isEmpty() ? "" : "Group: " + state.group,
                state.rarity.isEmpty() ? "" : "Rarity: " + state.rarity).stream().filter(s -> !s.isEmpty()).toList());
        if (!filters.isEmpty()) graphics.text(font, font.plainSubstrByWidth(filters, columns * 22), left, 80, 0xFFAAAAFF);
        int first = scroll * columns;
        int last = Math.min(matches.size(), first + columns * rows);
        for (int i = first; i < last; i++) {
            int x = left + (i - first) % columns * 22;
            int y = top + (i - first) / columns * 22;
            graphics.fill(x, y, x + 20, y + 20, 0xFF555555);
            graphics.fill(x + 1, y + 1, x + 19, y + 19, 0xFF272727);
            ItemStack stack = mod.stacks().get(matches.get(i));
            graphics.item(stack, x + 2, y + 2);
            if (mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20)
                graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
        }
        if (displayed != null && matches.isEmpty()) graphics.centeredText(font, "No matching items", width / 2, height / 2, 0xFFCCCCCC);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        int col = (int) (event.x() - left) / 22;
        int row = (int) (event.y() - top) / 22;
        if (event.x() >= left && event.y() >= top && col >= 0 && col < columns && row >= 0 && row < rows) {
            int index = (scroll + row) * columns + col;
            if (index >= 0 && index < matches.size()) {
                minecraft.setScreen(new DetailScreen(mod, state, matches.get(index)));
                return true;
            }
        }
        return false;
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        scroll = Math.max(0, Math.min(Math.max(0, (matches.size() + columns - 1) / columns - rows), scroll - (int) Math.signum(vertical)));
        return true;
    }

    @Override public void onClose() { minecraft.setScreen(null); }
}
