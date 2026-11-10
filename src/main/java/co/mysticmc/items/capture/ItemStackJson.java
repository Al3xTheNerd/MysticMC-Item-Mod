package co.mysticmc.items.capture;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonWriter;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.io.StringWriter;

final class ItemStackJson {
    private static final Gson GSON = new Gson();

    private ItemStackJson() {}

    static String encode(Minecraft client, ItemStack stack) throws IOException {
        if (client.level == null || stack.isEmpty()) {
            throw new IOException("No item is available to capture");
        }

        JsonElement data;
        try {
            data = ItemStack.CODEC.encodeStart(
                    RegistryOps.create(JsonOps.INSTANCE, client.level.registryAccess()), stack)
                    .getOrThrow();
        } catch (RuntimeException e) {
            throw new IOException("Could not serialize the item stack", e);
        }

        StringWriter output = new StringWriter();
        JsonWriter writer = new JsonWriter(output);
        writer.setIndent("    ");
        GSON.toJson(data, writer);
        writer.close();
        return output.toString() + System.lineSeparator();
    }
}
