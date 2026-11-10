package co.mysticmc.items.mixin;

import co.mysticmc.items.MysticItemBrowser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Redirects only the player's inventory screen. No real menu slots are ever used. */
@Mixin(Minecraft.class)
public abstract class InventoryRedirectMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void mysticItems$redirectInventory(Screen incoming, CallbackInfo info) {
        MysticItemBrowser mod = MysticItemBrowser.instance();
        if (mod == null || !mod.isPreviewActive()) return;
        if (incoming instanceof InventoryScreen || incoming instanceof CreativeModeInventoryScreen) {
            info.cancel();
            ((Minecraft)(Object)this).setScreen(mod.previewScreen());
        }
    }
}
