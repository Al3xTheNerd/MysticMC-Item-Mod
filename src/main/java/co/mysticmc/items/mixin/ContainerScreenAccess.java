package co.mysticmc.items.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccess {
    @Accessor("hoveredSlot")
    Slot mysticItems$getHoveredSlot();

    @Accessor("leftPos")
    int mysticItems$getLeftPos();

    @Accessor("topPos")
    int mysticItems$getTopPos();

    @Accessor("imageWidth")
    int mysticItems$getImageWidth();
}
