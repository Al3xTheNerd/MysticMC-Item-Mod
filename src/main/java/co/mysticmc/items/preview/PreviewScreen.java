package co.mysticmc.items.preview;

import co.mysticmc.items.MysticItemBrowser;
import co.mysticmc.items.data.ItemDatabase;
import co.mysticmc.items.data.ItemEntry;
import co.mysticmc.items.gui.BrowserScreen;
import co.mysticmc.items.gui.BrowserState;
import co.mysticmc.items.gui.FilterOptionScreen;
import co.mysticmc.items.gui.TagScreen;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class PreviewScreen extends Screen {
    private final MysticItemBrowser mod;
    private final BrowserState state;
    private final VirtualInventory inventory;
    private final ClientLevel openingLevel;
    private EditBox search;
    private List<ItemEntry> matches = List.of();
    private ItemDatabase displayed;
    private boolean itemsTab = true;
    private int scroll;
    private int gridX, gridY, gridRows;
    private int controlsX;
    private int slotsX, slotsY;
    private int dragLast = -1;

    public PreviewScreen(MysticItemBrowser mod, BrowserState state, VirtualInventory inventory) {
        super(Component.literal("MysticMC Preview Mode"));
        this.mod = mod; this.state = state;
        this.inventory = inventory;
        this.openingLevel = net.minecraft.client.Minecraft.getInstance().level;
    }

    @Override protected void init() {
        gridX = (width - 9 * 20) / 2;
        controlsX = Math.max(1, (width - 317) / 2);
        gridY = 95;
        slotsX = (width - 9 * 18) / 2;
        slotsY = Math.max(130, height - 91);
        gridRows = Math.max(1, Math.min(8, (height - gridY - 35) / 20));
        addRenderableWidget(Button.builder(Component.literal("Items"), b -> itemsTab = true).bounds(controlsX, 19, 48, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Inventory"), b -> itemsTab = false).bounds(controlsX + 51, 19, 72, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Tags"), b -> minecraft.setScreen(new TagScreen(mod, state, this)))
                .bounds(controlsX, 43, 47, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Source"), b -> {
            state.nextSource(); refreshMatches();
        }).bounds(controlsX + 50, 43, 53, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Crate"), b -> minecraft.setScreen(new FilterOptionScreen(mod, state, this, FilterOptionScreen.Kind.CRATE)))
                .bounds(controlsX + 106, 43, 49, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Group"), b -> minecraft.setScreen(new FilterOptionScreen(mod, state, this, FilterOptionScreen.Kind.GROUP)))
                .bounds(controlsX + 158, 43, 51, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Rarity"), b -> {
            state.nextRarity(); refreshMatches();
        }).bounds(controlsX + 212, 43, 54, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Clear"), b -> {
            state.clear(); search.setValue(""); refreshMatches();
        }).bounds(controlsX + 269, 43, 48, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Clear items"), b -> inventory.clear())
                .bounds(controlsX, 65, 60, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Delete cursor"), b -> inventory.deleteCursor())
                .bounds(controlsX + 63, 65, 80, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Browser"), b -> {
            minecraft.setScreen(new BrowserScreen(mod, state));
        }).bounds(controlsX + 146, 65, 68, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose()).bounds(controlsX + 217, 65, 48, 18).build());
        addRenderableWidget(Button.builder(Component.literal("End"), b -> mod.endPreview()).bounds(controlsX + 268, 65, 48, 18).build());
        search = addRenderableWidget(new EditBox(font, controlsX + 126, 19, 190, 18, Component.literal("Search catalog")));
        search.setValue(state.query);
        search.setResponder(value -> { state.query = value; refreshMatches(); });
        refreshMatches();
    }

    public void updateMatches() { refreshMatches(); }
    private void refreshMatches() {
        displayed = mod.repository().database();
        matches = state.filter(displayed); scroll = 0;
    }

    @Override public void tick() {
        if (minecraft.level != openingLevel) { mod.endPreview(); return; }
        if (displayed != mod.repository().database()) refreshMatches();
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xE0101018);
        g.centeredText(font, title, width / 2, 5, 0xFFFFD780);
        if (itemsTab) {
            int first = scroll * 9;
            int last = Math.min(matches.size(), first + gridRows * 9);
            for (int i = first; i < last; i++) {
                int x = gridX + (i - first) % 9 * 20, y = gridY + (i - first) / 9 * 20;
                slotBackground(g, x, y, 18);
                ItemStack stack = mod.stacks().get(matches.get(i));
                g.item(stack, x + 1, y + 1);
                if (inside(mx, my, x, y, 18)) g.setTooltipForNextFrame(font, stack, mx, my);
            }
            g.text(font, matches.size() + " items", gridX, gridY + gridRows * 20 + 2, 0xFFDDDDDD);
            g.text(font, font.plainSubstrByWidth("Click item, then Inventory | Shift-click to add", 317), controlsX,
                    gridY + gridRows * 20 + 13, 0xFFAAAAAA);
        } else {
            g.text(font, "Virtual equipment", slotsX, 88, 0xFFDDDDDD);
            renderPlayer(g);
        }
        if (itemsTab) {
            String filterStatus = "Source: " + state.sourceLabel()
                    + (state.crate.isEmpty() ? "" : " | " + state.crate)
                    + (state.group.isEmpty() ? "" : " | " + state.group)
                    + (state.rarity.isEmpty() ? "" : " | " + state.rarity)
                    + (state.tags.isEmpty() ? "" : " | Tags: " + state.tags.size());
            g.text(font, font.plainSubstrByWidth(filterStatus, 317), controlsX, 85, 0xFFAAAADD);
        }
        if (!itemsTab) for (int i = 0; i < VirtualInventory.SIZE; i++) {
            int x = slotX(i), y = slotY(i);
            slotBackground(g, x, y, 18);
            ItemStack stack = inventory.get(i);
            if (!stack.isEmpty()) {
                g.item(stack, x + 1, y + 1); g.itemDecorations(font, stack, x + 1, y + 1);
                if (inside(mx, my, x, y, 18)) g.setTooltipForNextFrame(font, stack, mx, my);
            } else if (i >= 36) g.blitSprite(RenderPipelines.GUI_TEXTURED, slotIcon(i), x + 1, y + 1, 16, 16);
        }
        if (!inventory.cursor().isEmpty()) {
            g.item(inventory.cursor(), mx - 8, my - 8);
            g.itemDecorations(font, inventory.cursor(), mx - 8, my - 8);
        }
        super.extractRenderState(g, mx, my, delta);
    }

    private void renderPlayer(GuiGraphicsExtractor g) {
        if (minecraft.player == null || minecraft.level == null) return;
        var extracted = minecraft.getEntityRenderDispatcher().extractEntity(minecraft.player, 0);
        if (!(extracted instanceof AvatarRenderState avatar)) return;
        avatar.isInvisible = false;
        avatar.isSpectator = false;
        mod.applyVirtualEquipment(avatar);
        avatar.bodyRot = 180;
        avatar.yRot = 0;
        avatar.xRot = 0;
        int x1 = Math.min(width - 64, slotsX + 166), x2 = x1 + 60;
        int y1 = 95, y2 = Math.max(y1 + 50, height - 27);
        g.entity(avatar, 28, new Vector3f(0, avatar.boundingBoxHeight / 2 + 0.0625f, 0),
                new Quaternionf().rotateZ((float)Math.PI), new Quaternionf(), x1, y1, x2, y2);
    }

    private static void slotBackground(GuiGraphicsExtractor g, int x, int y, int size) {
        g.fill(x, y, x + size, y + size, 0xFF777777);
        g.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xFF333333);
    }
    private static Identifier slotIcon(int slot) {
        return switch (slot) {
            case 36 -> InventoryMenu.EMPTY_ARMOR_SLOT_HELMET;
            case 37 -> InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE;
            case 38 -> InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS;
            case 39 -> InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS;
            default -> InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD;
        };
    }
    private static boolean inside(double mx, double my, int x, int y, int size) {
        return mx >= x && mx < x + size && my >= y && my < y + size;
    }
    private int slotX(int i) {
        if (i < 27) return slotsX + i % 9 * 18;
        if (i < 36) return slotsX + (i - 27) * 18;
        return slotsX + (i - 36) * 36;
    }
    private int slotY(int i) {
        if (i < 27) return slotsY + i / 9 * 18;
        if (i < 36) return slotsY + 60;
        return slotsY - 23;
    }
    private int hitSlot(double x, double y) {
        for (int i = 0; i < VirtualInventory.SIZE; i++) if (inside(x, y, slotX(i), slotY(i), 18)) return i;
        return -1;
    }
    @Override public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (super.mouseClicked(e, dbl)) return true;
        int slot = itemsTab ? -1 : hitSlot(e.x(), e.y());
        if (slot >= 0) { inventory.click(slot, e.button(), e.hasShiftDown()); dragLast = slot; return true; }
        if (itemsTab && e.y() >= gridY && e.y() < gridY + gridRows * 20 && e.x() >= gridX && e.x() < gridX + 9 * 20) {
            int col = ((int)e.x() - gridX) / 20, row = ((int)e.y() - gridY) / 20;
            int index = (scroll + row) * 9 + col;
            if (index < matches.size()) { inventory.selectCatalogItem(mod.stacks().get(matches.get(index)), e.button(), e.hasShiftDown()); return true; }
        }
        return false;
    }
    @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        int slot = itemsTab ? -1 : hitSlot(e.x(), e.y());
        if (slot >= 0 && slot != dragLast && !inventory.cursor().isEmpty()) {
            inventory.click(slot, 1, false); dragLast = slot; return true;
        }
        return super.mouseDragged(e, dx, dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent e) { dragLast = -1; return super.mouseReleased(e); }
    @Override public boolean mouseScrolled(double x, double y, double h, double v) {
        if (itemsTab) scroll = Math.max(0, Math.min(Math.max(0, (matches.size() + 8) / 9 - gridRows), scroll - (int)Math.signum(v)));
        return true;
    }
    @Override public boolean keyPressed(KeyEvent e) {
        int digit = e.getDigit();
        int slot = hitSlot(minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth(),
                minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight());
        if (digit >= 1 && digit <= 9) {
            if (slot >= 0) inventory.hotbarSwap(slot, digit - 1);
            else inventory.selectHotbar(digit - 1);
            return true;
        }
        return super.keyPressed(e);
    }
    @Override public void onClose() { minecraft.setScreen(null); }
}
