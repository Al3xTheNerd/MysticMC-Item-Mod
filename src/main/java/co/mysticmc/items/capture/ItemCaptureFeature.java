package co.mysticmc.items.capture;

import com.mojang.blaze3d.platform.NativeImage;
import co.mysticmc.items.mixin.ContainerScreenAccess;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ItemCaptureFeature {
    private static final ExecutorService SAVE_WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "mystic-item-browser-item-capture-writer");
        return thread;
    });
    private static long tick;
    private static Session active;
    private static KeyMapping uploadKey;
    private static KeyMapping imageKey;

    private ItemCaptureFeature() {}

    public static void initialize(KeyMapping.Category category) {
        ItemCaptureConfig.initialize();
        uploadKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mystic-item-browser.capture_upload",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_SUBTRACT, category));
        imageKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mystic-item-browser.capture_image",
                InputConstants.Type.KEYSYM, -1, category));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> SAVE_WORKER.shutdown());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tick++;
            if (active != null && client.screen != active.screen) {
                active = null;
                notifyPlayer(client, "Item capture cancelled: container closed");
            }
        });
    }

    /** Called from the container screen's keyPressed handler, including while a GUI is open. */
    public static boolean onKey(AbstractContainerScreen<?> screen, KeyEvent event) {
        boolean imageOnly = imageKey.matches(event);
        if (!imageOnly && !uploadKey.matches(event)) return false;

        Minecraft client = Minecraft.getInstance();
        if (active != null) {
            notifyPlayer(client, "Item capture already in progress");
            return true;
        }
        ContainerScreenAccess access = (ContainerScreenAccess) screen;
        Slot slot = access.mysticItems$getHoveredSlot();
        if (slot == null || !slot.hasItem()) {
            notifyPlayer(client, "Hover an item slot to capture it");
            return true;
        }
        ItemStack stack = slot.getItem().copy();
        try {
            ItemCaptureConfig config = imageOnly ? null : ItemCaptureConfig.get();
            String json = imageOnly ? null : ItemStackJson.encode(client, stack);
            int loopTicks = ItemAnimationDuration.loopTicks(client, stack,
                    slot.x + slot.y * access.mysticItems$getImageWidth());
            if (!imageOnly) client.keyboardHandler.setClipboard(json);
            active = new Session(screen, slot, access.mysticItems$getLeftPos() + slot.x - 1,
                    access.mysticItems$getTopPos() + slot.y - 1, stack,
                    stack.getHoverName().getString(), json, loopTicks, tick,
                    imageOnly ? null : config.dataEndpoint(),
                    imageOnly ? null : config.apiKey(), imageOnly);
            notifyPlayer(client, loopTicks > 0
                    ? "Recording item animation (" + loopTicks + " ticks)"
                    : "Capturing item preview");
        } catch (IOException | RuntimeException error) {
            notifyPlayer(client, "Item capture failed: " + error.getMessage());
            System.err.println("[MysticMC Items] Item capture failed: " + error);
        }
        return true;
    }

    /** Keep hover tooltips and slot highlights out of the captured preview. */
    public static boolean shouldSuppressHoverEffects(AbstractContainerScreen<?> screen) {
        return active != null && active.screen == screen;
    }

    /** Called after a rendered frame so the screenshot contains the fully drawn GUI. */
    public static void afterRender() {
        Session session = active;
        if (session == null || session.inFlight) return;
        Minecraft client = Minecraft.getInstance();
        if (client.screen != session.screen || !ItemStack.isSameItemSameComponents(
                session.stack, session.slot.getItem())) {
            active = null;
            notifyPlayer(client, "Item capture cancelled: selected slot changed");
            return;
        }
        long elapsed = tick - session.startTick;
        if (session.loopTicks > 0 && elapsed >= session.loopTicks) {
            finish(client, session);
            return;
        }
        if (session.lastSampleTick == tick) return;

        session.lastSampleTick = tick;
        session.inFlight = true;
        int sampleTick = (int) elapsed;
        try {
            Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> {
                try (image) {
                    BufferedImage crop = cropSlot(client, image, session.guiX, session.guiY);
                    client.execute(() -> acceptFrame(client, session, crop, sampleTick));
                } catch (RuntimeException error) {
                    client.execute(() -> fail(client, session, error));
                }
            });
        } catch (RuntimeException error) {
            fail(client, session, error);
        }
    }

    private static BufferedImage cropSlot(Minecraft client, NativeImage image, int guiX, int guiY) {
        // The renderer projects GUI coordinates using framebuffer dimensions / guiScale.
        // getGuiScaledHeight() is rounded up, so dividing by it can shift a crop by pixels.
        int scale = client.getWindow().getGuiScale();
        int left = guiX * scale;
        int top = guiY * scale;
        int right = (guiX + 18) * scale;
        int bottom = (guiY + 18) * scale;
        if (left < 0 || top < 0 || right > image.getWidth() || bottom > image.getHeight()
                || left >= right || top >= bottom) {
            throw new IllegalStateException("Hovered slot is outside the screenshot");
        }
        BufferedImage result = new BufferedImage(right - left, bottom - top, BufferedImage.TYPE_INT_RGB);
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                result.setRGB(x - left, y - top, image.getPixel(x, y));
            }
        }
        return result;
    }

    private static void acceptFrame(Minecraft client, Session session, BufferedImage crop, int sampleTick) {
        if (active != session) return;
        session.inFlight = false;
        session.frames.add(new ItemCaptureFiles.Frame(crop, sampleTick));
        if (session.loopTicks == 0) finish(client, session);
    }

    private static void finish(Minecraft client, Session session) {
        if (active != session) return;
        active = null;
        List<ItemCaptureFiles.Frame> frames = List.copyOf(session.frames);
        SAVE_WORKER.execute(() -> {
            try {
                var capture = ItemCaptureFiles.save(client, session.itemName, session.json,
                        frames, session.loopTicks, session.imageOnly);
                if (session.imageOnly) {
                    client.execute(() -> notifyPlayer(client, "Item image saved to screenshots: " + capture.imagePath().getFileName()));
                    return;
                }
                if (session.endpoint == null || session.endpoint.isBlank()) {
                    client.execute(() -> notifyPlayer(client, "Item saved to " + capture.folder().getFileName()));
                    return;
                }
                client.execute(() -> notifyPlayer(client, "Item saved; uploading to Item Site"));
                try {
                    ItemCaptureUpload.send(session.endpoint, session.apiKey, session.json, capture)
                            .whenComplete((status, error) -> {
                                if (error != null) {
                                    System.err.println("[MysticMC Items] Item upload failed: " + error);
                                    client.execute(() -> notifyPlayer(client,
                                            "Item saved locally; upload failed (network error)"));
                                } else if (status >= 200 && status < 300) {
                                    client.execute(() -> notifyPlayer(client, "Item uploaded to Item Site"));
                                } else {
                                    System.err.println("[MysticMC Items] Item upload returned HTTP " + status);
                                    client.execute(() -> notifyPlayer(client,
                                            "Item saved locally; upload failed (HTTP " + status + ")"));
                                }
                            });
                } catch (IOException | RuntimeException error) {
                    System.err.println("[MysticMC Items] Item upload could not start: " + error);
                    client.execute(() -> notifyPlayer(client,
                            "Item saved locally; upload not sent: " + error.getMessage()));
                }
            } catch (IOException | RuntimeException error) {
                client.execute(() -> notifyPlayer(client, "Item save failed: " + error.getMessage()));
                System.err.println("[MysticMC Items] Item save failed: " + error);
            }
        });
    }

    private static void fail(Minecraft client, Session session, RuntimeException error) {
        if (active != session) return;
        active = null;
        notifyPlayer(client, "Item capture failed: " + error.getMessage());
        System.err.println("[MysticMC Items] Item capture failed: " + error);
    }

    private static void notifyPlayer(Minecraft client, String message) {
        if (client.player != null) {
            client.gui.getChat().addClientSystemMessage(Component.literal("[MysticMC Items] " + message));
        }
    }

    private static final class Session {
        final AbstractContainerScreen<?> screen;
        final Slot slot;
        final int guiX;
        final int guiY;
        final ItemStack stack;
        final String itemName;
        final String json;
        final int loopTicks;
        final long startTick;
        final String endpoint;
        final String apiKey;
        final boolean imageOnly;
        final List<ItemCaptureFiles.Frame> frames = new ArrayList<>();
        long lastSampleTick = Long.MIN_VALUE;
        boolean inFlight;

        Session(AbstractContainerScreen<?> screen, Slot slot, int guiX, int guiY,
                ItemStack stack, String itemName, String json, int loopTicks, long startTick,
                String endpoint, String apiKey, boolean imageOnly) {
            this.screen = screen;
            this.slot = slot;
            this.guiX = guiX;
            this.guiY = guiY;
            this.stack = stack;
            this.itemName = itemName;
            this.json = json;
            this.loopTicks = loopTicks;
            this.startTick = startTick;
            this.endpoint = endpoint;
            this.apiKey = apiKey;
            this.imageOnly = imageOnly;
        }
    }
}
