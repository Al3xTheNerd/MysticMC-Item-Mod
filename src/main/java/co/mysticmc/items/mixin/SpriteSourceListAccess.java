package co.mysticmc.items.mixin;

import java.util.List;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SpriteSourceList.class)
public interface SpriteSourceListAccess {
    @Accessor("sources")
    List<SpriteSource> mysticItems$getSources();
}
