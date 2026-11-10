package co.mysticmc.items.gui;

import co.mysticmc.items.MysticItemBrowser;
import co.mysticmc.items.data.ItemDatabase;
import co.mysticmc.items.preview.PreviewScreen;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** A visible choice list for filters with too many values to cycle through. */
public final class FilterOptionScreen extends Screen {
    public enum Kind { CRATE, GROUP }

    private final MysticItemBrowser mod;
    private final BrowserState state;
    private final Screen parent;
    private final Kind kind;
    private List<String> options = List.of();
    private int scroll;
    private int rows;
    private int left;

    public FilterOptionScreen(MysticItemBrowser mod, BrowserState state, Screen parent, Kind kind) {
        super(Component.literal("Select " + kind.name().toLowerCase()));
        this.mod = mod; this.state = state; this.parent = parent; this.kind = kind;
    }

    @Override protected void init() {
        ItemDatabase db = mod.repository().database();
        options = db == null ? List.of() : switch (kind) {
            case CRATE -> db.crates.stream().filter(s -> !s.isBlank()).distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
            case GROUP -> db.groups.stream().filter(s -> !s.isBlank()).distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        };
        rows = Math.max(1, (height - 82) / 19);
        left = Math.max(5, (width - 300) / 2);
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(left, 27, 60, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Any"), b -> select("")).bounds(left + 64, 27, 60, 18).build());
    }

    private void select(String choice) {
        switch (kind) {
            case CRATE -> { state.crate = choice; state.group = ""; if (!choice.isEmpty()) state.source = 1; }
            case GROUP -> { state.group = choice; state.crate = ""; if (!choice.isEmpty()) state.source = 2; }
        }
        onClose();
    }

    @Override public void onClose() {
        minecraft.setScreen(parent);
        if (parent instanceof BrowserScreen browser) browser.updateMatches();
        if (parent instanceof PreviewScreen preview) preview.updateMatches();
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xE015151B);
        g.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
        String current = switch (kind) { case CRATE -> state.crate; case GROUP -> state.group; };
        g.text(font, "Selected: " + (current.isEmpty() ? "Any" : current), left, 52, 0xFFCCCCFF);
        for (int i = scroll; i < Math.min(options.size(), scroll + rows); i++) {
            int y = 70 + (i - scroll) * 19;
            String option = options.get(i);
            g.fill(left, y, left + 300, y + 18, option.equals(current) ? 0xFF495D40 : 0xFF353535);
            g.text(font, font.plainSubstrByWidth(option, 288), left + 5, y + 5, 0xFFFFFFFF);
        }
        super.extractRenderState(g, mx, my, delta);
    }

    @Override public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (super.mouseClicked(e, dbl)) return true;
        int row = (int)(e.y() - 70) / 19;
        int index = scroll + row;
        if (e.x() >= left && e.x() < left + 300 && e.y() >= 70 && row >= 0 && row < rows && index < options.size()) {
            select(options.get(index)); return true;
        }
        return false;
    }

    @Override public boolean mouseScrolled(double x, double y, double h, double v) {
        scroll = Math.max(0, Math.min(Math.max(0, options.size() - rows), scroll - (int)Math.signum(v)));
        return true;
    }
}
