package co.mysticmc.items.gui;

import co.mysticmc.items.MysticItemBrowser;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class TagScreen extends Screen {
    private static final int LIST_TOP = 101;
    private final MysticItemBrowser mod;
    private final BrowserState state;
    private final Screen parent;
    private List<String> tags = List.of();
    private int scroll;
    private int rows;
    public TagScreen(MysticItemBrowser mod, BrowserState state, Screen parent) {
        super(Component.literal("MysticMC Tags")); this.mod = mod; this.state = state; this.parent = parent;
    }
    @Override protected void init() {
        tags = mod.repository().database() == null ? List.of() : mod.repository().database().tags;
        rows = Math.max(2, (height - LIST_TOP - 8) / 18);
        addRenderableWidget(Button.builder(Component.literal(state.requireAllTags ? "Match all" : "Match any"), b -> {
            state.requireAllTags = !state.requireAllTags;
            minecraft.setScreen(new TagScreen(mod, state, parent));
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
        for (int i = scroll; i < Math.min(tags.size(), scroll + rows * 2); i++) {
            int col = (i - scroll) / rows;
            int row = (i - scroll) % rows;
            int px = width / 2 - 140 + col * 145;
            int py = LIST_TOP + row * 18;
            g.fill(px, py, px + 138, py + 16, state.tags.contains(tags.get(i)) ? 0xFF495D40 : 0xFF353535);
            g.text(font, tags.get(i), px + 5, py + 4, 0xFFFFFFFF);
        }
        super.extractRenderState(g, x, y, delta);
    }
    @Override public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (super.mouseClicked(e, dbl)) return true;
        int col = (int)(e.x() - (width / 2 - 140)) / 145;
        int row = (int)(e.y() - LIST_TOP) / 18;
        int index = scroll + col * rows + row;
        if (e.y() >= LIST_TOP && col >= 0 && col < 2 && row >= 0 && row < rows && index < tags.size()) {
            String tag = tags.get(index);
            if (!state.tags.remove(tag)) state.tags.add(tag);
            return true;
        }
        return false;
    }
    @Override public boolean mouseScrolled(double x, double y, double h, double v) {
        scroll = Math.max(0, Math.min(Math.max(0, tags.size() - rows * 2), scroll - (int)Math.signum(v) * rows));
        return true;
    }
}
