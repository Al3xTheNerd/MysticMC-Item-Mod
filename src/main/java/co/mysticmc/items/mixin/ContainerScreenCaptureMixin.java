package co.mysticmc.items.mixin;

import co.mysticmc.items.capture.ItemCaptureFeature;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenCaptureMixin {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void mysticItems$captureItem(KeyEvent event, CallbackInfoReturnable<Boolean> result) {
        if (ItemCaptureFeature.onKey((AbstractContainerScreen<?>) (Object) this, event)) {
            result.setReturnValue(true);
        }
    }

    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void mysticItems$hideTooltipDuringCapture(GuiGraphicsExtractor graphics, int mouseX,
                                                       int mouseY, CallbackInfo callback) {
        if (ItemCaptureFeature.shouldSuppressHoverEffects((AbstractContainerScreen<?>) (Object) this)) {
            callback.cancel();
        }
    }

    @Inject(method = {"extractSlotHighlightBack", "extractSlotHighlightFront"},
            at = @At("HEAD"), cancellable = true)
    private void mysticItems$hideSlotHighlightDuringCapture(GuiGraphicsExtractor graphics,
                                                            CallbackInfo callback) {
        if (ItemCaptureFeature.shouldSuppressHoverEffects((AbstractContainerScreen<?>) (Object) this)) {
            callback.cancel();
        }
    }
}
