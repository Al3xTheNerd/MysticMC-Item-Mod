package co.mysticmc.items.preview;

import net.minecraft.world.item.ItemStack;

/** All operations are local and have no reference to a player menu or network connection. */
public final class VirtualInventory {
    public static final int SIZE = 41;
    private final ItemStack[] slots = new ItemStack[SIZE];
    private ItemStack cursor = ItemStack.EMPTY;
    private int selectedHotbar;

    public VirtualInventory() { clear(); }
    public ItemStack get(int slot) { return slots[slot]; }
    public ItemStack cursor() { return cursor; }
    public int selectedHotbar() { return selectedHotbar; }
    public void selectHotbar(int slot) { if (slot >= 0 && slot < 9) selectedHotbar = slot; }
    public void clear() {
        for (int i = 0; i < SIZE; i++) slots[i] = ItemStack.EMPTY;
        cursor = ItemStack.EMPTY;
        selectedHotbar = 0;
    }

    public void selectCatalogItem(ItemStack prototype, int button, boolean shift) {
        if (prototype.isEmpty()) return;
        ItemStack copy = prototype.copyWithCount(button == 1 ? 1 : prototype.getMaxStackSize());
        if (shift) add(copy);
        else cursor = copy;
    }

    public void add(ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (int i = 0; i < 36 && !remaining.isEmpty(); i++) {
            if (!slots[i].isEmpty() && ItemStack.isSameItemSameComponents(slots[i], remaining)) {
                int space = slots[i].getMaxStackSize() - slots[i].getCount();
                int moved = Math.min(space, remaining.getCount());
                if (moved > 0) { slots[i].grow(moved); remaining.shrink(moved); }
            }
        }
        for (int i = 0; i < 36 && !remaining.isEmpty(); i++) {
            if (slots[i].isEmpty()) {
                int moved = Math.min(remaining.getMaxStackSize(), remaining.getCount());
                slots[i] = remaining.split(moved);
            }
        }
    }

    public void click(int slot, int button, boolean shift) {
        if (slot < 0 || slot >= SIZE) return;
        if (shift) { transfer(slot); return; }
        ItemStack target = slots[slot];
        if (button == 2) { if (!target.isEmpty()) cursor = target.copyWithCount(target.getMaxStackSize()); return; }
        if (button == 0) {
            if (cursor.isEmpty()) { cursor = target; slots[slot] = ItemStack.EMPTY; }
            else if (target.isEmpty()) { slots[slot] = cursor; cursor = ItemStack.EMPTY; }
            else if (ItemStack.isSameItemSameComponents(cursor, target) && target.getCount() < target.getMaxStackSize()) {
                int moved = Math.min(cursor.getCount(), target.getMaxStackSize() - target.getCount());
                target.grow(moved); cursor.shrink(moved);
                if (cursor.isEmpty()) cursor = ItemStack.EMPTY;
            } else { slots[slot] = cursor; cursor = target; }
        } else if (button == 1) {
            if (cursor.isEmpty()) {
                int take = (target.getCount() + 1) / 2;
                cursor = target.split(take);
                if (target.isEmpty()) slots[slot] = ItemStack.EMPTY;
            } else if (target.isEmpty()) {
                slots[slot] = cursor.split(1);
            } else if (ItemStack.isSameItemSameComponents(cursor, target) && target.getCount() < target.getMaxStackSize()) {
                target.grow(1); cursor.shrink(1);
            }
            if (cursor.isEmpty()) cursor = ItemStack.EMPTY;
        }
    }

    public void hotbarSwap(int slot, int hotbar) {
        if (slot < 0 || slot >= SIZE || hotbar < 0 || hotbar >= 9) return;
        int other = 27 + hotbar;
        ItemStack old = slots[slot]; slots[slot] = slots[other]; slots[other] = old;
    }

    public void deleteCursor() { cursor = ItemStack.EMPTY; }

    private void transfer(int slot) {
        ItemStack stack = slots[slot];
        if (stack.isEmpty()) return;
        int begin = slot < 27 ? 27 : 0;
        int end = slot < 27 ? 36 : 27;
        for (int i = begin; i < end && !stack.isEmpty(); i++) {
            ItemStack target = slots[i];
            if (!target.isEmpty() && ItemStack.isSameItemSameComponents(stack, target)) {
                int amount = Math.min(stack.getCount(), target.getMaxStackSize() - target.getCount());
                if (amount > 0) { target.grow(amount); stack.shrink(amount); }
            }
        }
        for (int i = begin; i < end && !stack.isEmpty(); i++) if (slots[i].isEmpty()) {
            slots[i] = stack.copy(); stack = ItemStack.EMPTY;
        }
        slots[slot] = stack.isEmpty() ? ItemStack.EMPTY : stack;
    }
}
