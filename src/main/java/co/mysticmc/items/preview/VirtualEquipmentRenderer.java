package co.mysticmc.items.preview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Replaces only extracted render data; the player inventory and server equipment stay untouched. */
public final class VirtualEquipmentRenderer {
    private VirtualEquipmentRenderer() {}

    public static void apply(Minecraft client, VirtualInventory inventory, AvatarRenderState avatar) {
        if (client.player == null) return;
        avatar.headEquipment = inventory.get(36);
        avatar.chestEquipment = inventory.get(37);
        avatar.legsEquipment = inventory.get(38);
        avatar.feetEquipment = inventory.get(39);
        // Vanilla extracts this from the real helmet. Paper and feather hats use the
        // resource pack's HEAD item model instead of an armor equipment asset.
        avatar.wornHeadType = null;
        avatar.wornHeadProfile = null;
        if (HumanoidArmorLayer.shouldRender(avatar.headEquipment, EquipmentSlot.HEAD))
            avatar.headItem.clear();
        else
            client.getItemModelResolver().updateForLiving(avatar.headItem, avatar.headEquipment,
                    ItemDisplayContext.HEAD, client.player);
        ItemStack main = inventory.get(27 + inventory.selectedHotbar());
        ItemStack off = inventory.get(40);
        boolean rightMain = avatar.mainArm == HumanoidArm.RIGHT;
        avatar.rightHandItemStack = rightMain ? main : off;
        avatar.leftHandItemStack = rightMain ? off : main;
        client.getItemModelResolver().updateForLiving(avatar.rightHandItemState, avatar.rightHandItemStack,
                ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, client.player);
        client.getItemModelResolver().updateForLiving(avatar.leftHandItemState, avatar.leftHandItemStack,
                ItemDisplayContext.THIRD_PERSON_LEFT_HAND, client.player);
    }
}
