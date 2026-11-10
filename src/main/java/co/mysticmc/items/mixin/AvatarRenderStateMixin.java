package co.mysticmc.items.mixin;

import co.mysticmc.items.MysticItemBrowser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shows virtual equipment on the local avatar in third-person world rendering. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRenderStateMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void mysticItems$showPreviewEquipment(Avatar entity, AvatarRenderState state, float delta, CallbackInfo info) {
        Minecraft client = Minecraft.getInstance();
        MysticItemBrowser mod = MysticItemBrowser.instance();
        if (mod != null && entity == client.player) mod.applyVirtualEquipment(state);
    }
}
