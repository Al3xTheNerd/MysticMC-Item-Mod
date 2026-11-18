package co.mysticmc.items.gui;

import co.mysticmc.items.MysticItemBrowser;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class TagScreen extends Screen {
    private static final int LIST_TOP = 124;
    private final MysticItemBrowser mod;
    private final BrowserState state;
    private final Screen parent;
    private List<String> tags = List.of();
    private List<String> visibleTags = List.of();
    private String query = "";
    private int scroll;
    private int rows;
    public TagScreen(MysticItemBrowser mod, BrowserState state, Screen parent) {
        super(Component.literal("MysticMC Tags")); this.mod = mod; this.state = state; this.parent = parent;
    }
    @Override protected void init() {
        tags = mod.repository().database() == null ? List.of() : mod.repository().database().tags;
        rows = Math.max(1, (height - LIST_TOP - 8) / 18);
        addRenderableWidget(Button.builder(Component.literal(state.requireAllTags ? "Match all" : "Match any"), b -> {
            state.requireAllTags = !state.requireAllTags;
            b.setMessage(Component.literal(state.requireAllTags ? "Match all" : "Match any"));
        }).bounds(width / 2 - 140, 25, 85, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Clear tags"), b -> state.tags.clear()).bounds(width / 2 - 50, 25, 85, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> done()).bounds(width / 2 + 40, 25, 85, 18).build());
        int x = Math.max(2, (width - 300) / 2);
        addRenderableWidget(Button.builder(Component.literal(state.sourceLabel()), b -> {
            state.nextSource();
            b.setMessage(Component.literal(state.sourceLabel()));
        })
                .bounds(x, 48, 70, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Crate..."), b -> minecraft.setScreen(new FilterOptionScreen(mod, state, this, FilterOptionScreen.Kind.CRATE)))
                .bounds(x + 75, 48, 70, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Group..."), b -> minecraft.setScreen(new FilterOptionScreen(mod, state, this, FilterOptionScreen.Kind.GROUP)))
                .bounds(x + 150, 48, 70, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Rarity"), b -> {
            state.nextRarity();
        })
                .bounds(x + 225, 48, 70, 18).build());
        EditBox search = addRenderableWidget(new EditBox(font, x, 99, 300, 18, Component.literal("Search tags")));
        search.setValue(query);
        search.setResponder(value -> { query = value; filterTags(); });
        filterTags();
        setInitialFocus(search);
    }
    private void filterTags() {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        visibleTags = needle.isEmpty() ? tags : tags.stream()
                .filter(tag -> tag.toLowerCase(Locale.ROOT).contains(needle)).toList();
        scroll = 0;
    }
    private void done() {
        minecraft.setScreen(parent);
        if (parent instanceof BrowserScreen browser) browser.updateMatches();
        if (parent instanceof co.mysticmc.items.preview.PreviewScreen preview) preview.updateMatches();
    }
    @Override public void onClose() { done(); }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        g.fill(0, 0, width, height, 0xE015151B);
        g.centeredText(font, title, width / 2, 7, 0xFFFFFFFF);
        g.centeredText(font, "Source: " + state.sourceLabel() + " | Crate: " + (state.crate.isEmpty() ? "Any" : state.crate),
                width / 2, 71, 0xFFCCCCFF);
        g.centeredText(font, "Group: " + (state.group.isEmpty() ? "Any" : state.group)
                + " | Rarity: " + (state.rarity.isEmpty() ? "Any" : state.rarity),
                width / 2, 84, 0xFFCCCCFF);
        for (int i = scroll; i < Math.min(visibleTags.size(), scroll + rows * 2); i++) {
            int col = (i - scroll) / rows;
            int row = (i - scroll) % rows;
            int px = width / 2 - 140 + col * 145;
            int py = LIST_TOP + row * 18;
            g.fill(px, py, px + 138, py + 16, state.tags.contains(visibleTags.get(i)) ? 0xFF495D40 : 0xFF353535);
            g.text(font, font.plainSubstrByWidth(visibleTags.get(i), 128), px + 5, py + 4, 0xFFFFFFFF);
        }
        if (visibleTags.isEmpty()) g.centeredText(font, "No matching tags", width / 2, LIST_TOP + 4, 0xFFCCCCCC);
        super.extractRenderState(g, x, y, delta);
    }
    @Override public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (super.mouseClicked(e, dbl)) return true;
        int relativeX = (int)(e.x() - (width / 2 - 140));
        int relativeY = (int)(e.y() - LIST_TOP);
        int col = relativeX / 145;
        int row = relativeY / 18;
        int index = scroll + col * rows + row;
        if (relativeX >= 0 && relativeX % 145 < 138 && relativeY >= 0 && relativeY % 18 < 16
                && col < 2 && row < rows && index < visibleTags.size()) {
            String tag = visibleTags.get(index);
            if (!state.tags.remove(tag)) state.tags.add(tag);
            return true;
        }
        return false;
    }
    @Override public boolean mouseScrolled(double x, double y, double h, double v) {
        scroll = Math.max(0, Math.min(Math.max(0, visibleTags.size() - rows * 2), scroll - (int)Math.signum(v) * rows));
        return true;
    }
}
