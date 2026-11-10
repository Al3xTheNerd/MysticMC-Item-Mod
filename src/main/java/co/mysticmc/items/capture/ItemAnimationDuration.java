package co.mysticmc.items.capture;

import com.mojang.blaze3d.platform.NativeImage;
import co.mysticmc.items.mixin.ItemLayerAccess;
import co.mysticmc.items.mixin.ItemRenderStateAccess;
import co.mysticmc.items.mixin.SpriteSourceListAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceList;
import net.minecraft.client.renderer.texture.atlas.sources.DirectoryLister;
import net.minecraft.client.renderer.texture.atlas.sources.SingleFile;
import net.minecraft.client.resources.metadata.animation.AnimationFrame;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class ItemAnimationDuration {
    private static final int MAX_LOOP_TICKS = 1200;

    private ItemAnimationDuration() {}

    /** Zero means the resolved GUI model has no animated texture sprites. */
    static int loopTicks(Minecraft client, ItemStack stack, int seed) throws IOException {
        if (client.level == null) throw new IOException("No world is loaded");

        ItemStackRenderState state = new ItemStackRenderState();
        client.getItemModelResolver().updateForTopItem(
                state, stack, ItemDisplayContext.GUI, client.level, client.player, seed);

        ItemRenderStateAccess access = (ItemRenderStateAccess) state;
        Set<Identifier> seen = new HashSet<>();
        long combined = 0;
        for (int i = 0; i < access.mysticItems$getActiveLayerCount(); i++) {
            var layer = (ItemLayerAccess) access.mysticItems$getLayers()[i];
            for (var quad : layer.mysticItems$getQuads()) {
                TextureAtlasSprite sprite = quad.materialInfo().sprite();
                if (!sprite.contents().isAnimated() || !seen.add(sprite.contents().name())) continue;
                int ticks = spriteLoopTicks(client, sprite);
                combined = combined == 0 ? ticks : lcm(combined, ticks);
                if (combined > MAX_LOOP_TICKS) {
                    throw new IOException("This item's texture loop exceeds 60 seconds; no partial GIF was saved");
                }
            }
        }
        return (int) combined;
    }

    private static int spriteLoopTicks(Minecraft client, TextureAtlasSprite sprite) throws IOException {
        Identifier id = sprite.contents().name();
        Identifier textureFile = Identifier.fromNamespaceAndPath(
                id.getNamespace(), "textures/" + id.getPath() + ".png");
        Optional<Resource> resource = client.getResourceManager().getResource(textureFile);
        if (resource.isEmpty()) {
            Identifier sourceId = atlasSource(client, sprite);
            if (sourceId != null) {
                textureFile = SpriteSource.TEXTURE_ID_CONVERTER.idToFile(sourceId);
                resource = client.getResourceManager().getResource(textureFile);
            }
        }
        if (resource.isEmpty()) throw new IOException("Animated texture not found: " + textureFile);

        AnimationMetadataSection animation = resource.get().metadata()
                .getSection(AnimationMetadataSection.TYPE)
                .orElse(null);
        if (animation == null) throw new IOException("Animation metadata not found: " + textureFile);
        List<AnimationFrame> frames = animation.frames().orElse(null);
        if (frames != null) {
            int total = 0;
            try {
                for (AnimationFrame frame : frames) {
                    total = Math.addExact(total, frame.timeOr(animation.defaultFrameTime()));
                }
            } catch (ArithmeticException e) {
                throw new IOException("Animation duration is too large: " + textureFile, e);
            }
            if (total > 0) return total;
        }

        try (InputStream stream = resource.get().open(); NativeImage image = NativeImage.read(stream)) {
            int frameWidth = sprite.contents().width();
            int frameHeight = sprite.contents().height();
            int frameCount = (image.getWidth() / frameWidth) * (image.getHeight() / frameHeight);
            int total = Math.multiplyExact(frameCount, animation.defaultFrameTime());
            if (total > 0) return total;
        } catch (ArithmeticException e) {
            throw new IOException("Animation duration is too large: " + textureFile, e);
        }
        throw new IOException("Invalid animation duration: " + textureFile);
    }

    /** Atlas sprite IDs can be aliases for textures in different paths or namespaces. */
    private static Identifier atlasSource(Minecraft client, TextureAtlasSprite sprite) {
        Identifier atlasLocation = sprite.atlasLocation();
        String path = atlasLocation.getPath();
        String prefix = "textures/atlas/";
        if (!path.startsWith(prefix) || !path.endsWith(".png")) return null;
        Identifier atlasId = Identifier.fromNamespaceAndPath(atlasLocation.getNamespace(),
                path.substring(prefix.length(), path.length() - ".png".length()));
        SpriteSourceList list = SpriteSourceList.load(client.getResourceManager(), atlasId);
        List<SpriteSource> sources = ((SpriteSourceListAccess) list).mysticItems$getSources();
        Identifier spriteId = sprite.contents().name();
        for (int i = sources.size() - 1; i >= 0; i--) {
            if (sources.get(i) instanceof SingleFile single
                    && single.spriteId().orElse(single.resourceId()).equals(spriteId)) {
                return single.resourceId();
            }
            if (sources.get(i) instanceof DirectoryLister directory
                    && spriteId.getPath().startsWith(directory.idPrefix())) {
                String relativePath = spriteId.getPath().substring(directory.idPrefix().length());
                String sourcePath = directory.sourcePath().isEmpty()
                        ? relativePath : directory.sourcePath() + "/" + relativePath;
                Identifier candidate = Identifier.fromNamespaceAndPath(spriteId.getNamespace(), sourcePath);
                if (client.getResourceManager().getResource(
                        SpriteSource.TEXTURE_ID_CONVERTER.idToFile(candidate)).isPresent()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static long lcm(long first, long second) {
        long a = first;
        long b = second;
        while (b != 0) {
            long next = a % b;
            a = b;
            b = next;
        }
        return (first / a) * second;
    }
}
