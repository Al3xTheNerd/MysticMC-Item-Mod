package co.mysticmc.items.mixin;

import co.mysticmc.items.capture.ItemCaptureFeature;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererCaptureMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void mysticItems$captureRenderedGui(DeltaTracker delta, boolean drawWorld, CallbackInfo info) {
        ItemCaptureFeature.afterRender();
    }
}
