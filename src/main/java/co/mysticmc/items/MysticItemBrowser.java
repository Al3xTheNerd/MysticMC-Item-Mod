package co.mysticmc.items;

import co.mysticmc.items.capture.ItemCaptureFeature;
import co.mysticmc.items.command.WhatIsThatCommand;
import co.mysticmc.items.chat.CrateRewardLinks;
import co.mysticmc.items.data.ItemDatabase;
import co.mysticmc.items.data.ItemEntry;
import co.mysticmc.items.data.ItemRepository;
import co.mysticmc.items.data.StackFactory;
import co.mysticmc.items.gui.BrowserScreen;
import co.mysticmc.items.gui.BrowserState;
import co.mysticmc.items.preview.PreviewScreen;
import co.mysticmc.items.preview.VirtualInventory;
import co.mysticmc.items.preview.VirtualEquipmentRenderer;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.PackType;

public final class MysticItemBrowser implements ClientModInitializer {
    private static MysticItemBrowser instance;
    private final ItemRepository repository = new ItemRepository();
    private final StackFactory stacks = new StackFactory();
    private final BrowserState browserState = new BrowserState();
    private ItemDatabase lastDatabase;
    private ResourceManager lastResources;
    private ClientLevel lastLevel;
    private VirtualInventory previewInventory;
    private ClientLevel previewLevel;

    public static MysticItemBrowser instance() { return instance; }
    public ItemRepository repository() { return repository; }
    public StackFactory stacks() { return stacks; }
    public BrowserState browserState() { return browserState; }
    public boolean isPreviewActive() { return previewInventory != null; }
    public void applyVirtualEquipment(AvatarRenderState state) {
        if (previewInventory != null) VirtualEquipmentRenderer.apply(Minecraft.getInstance(), previewInventory, state);
    }
    public PreviewScreen previewScreen() { return new PreviewScreen(this, browserState, previewInventory); }

    public void showPreview() {
        if (previewInventory == null) beginPreview(null);
        else Minecraft.getInstance().setScreen(previewScreen());
    }

    public void addToPreview(ItemEntry entry) {
        if (previewInventory == null) beginPreview(entry);
        else {
            previewInventory.add(stacks.get(entry));
            Minecraft.getInstance().setScreen(previewScreen());
        }
    }

    public void beginPreview(ItemEntry initial) {
        Minecraft client = Minecraft.getInstance();
        if (previewInventory != null) previewInventory.clear();
        previewInventory = new VirtualInventory();
        previewLevel = client.level;
        if (initial != null) previewInventory.add(stacks.get(initial));
        client.setScreen(previewScreen());
        message("MysticMC Preview Mode started. Real inventory is hidden and unchanged.");
    }

    public void endPreview() {
        if (previewInventory == null) return;
        previewInventory.clear();
        previewInventory = null;
        previewLevel = null;
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof PreviewScreen) client.setScreen(null);
        message("MysticMC Preview Mode ended. Real inventory is visible again.");
    }

    private void message(String text) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) client.gui.getChat().addClientSystemMessage(Component.literal(text));
    }

    @Override
    public void onInitializeClient() {
        instance = this;
        repository.start();
        CrateRewardLinks.initialize(this);
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(Identifier.parse("mystic-item-browser:stack-cache"), new SimpleReloadListener<Void>() {
            @Override protected Void prepare(PreparableReloadListener.SharedState state) { return null; }
            @Override protected void apply(Void ignored, PreparableReloadListener.SharedState state) { stacks.clear(); }
        });
        KeyMapping.Category keyCategory = KeyMapping.Category.register(Identifier.parse("mystic-item-browser:main"));
        KeyMapping open = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mystic-item-browser.open",
                InputConstants.Type.KEYSYM, -1, keyCategory));
        ItemCaptureFeature.initialize(keyCategory);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (open.consumeClick()) client.setScreen(new BrowserScreen(this, browserState));
            if (previewInventory != null && client.level != previewLevel) endPreview();
            if (lastDatabase != repository.database() || lastResources != client.getResourceManager() || lastLevel != client.level) {
                stacks.clear();
                lastDatabase = repository.database();
                lastResources = client.getResourceManager();
                lastLevel = client.level;
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            endPreview();
        });
        HudElementRegistry.replaceElement(VanillaHudElements.HOTBAR, original -> (g, delta) -> {
            if (previewInventory == null) { original.extractRenderState(g, delta); return; }
            int left = g.guiWidth() / 2 - 91;
            int top = g.guiHeight() - 22;
            g.fill(left - 2, top - 2, left + 184, top + 22, 0xDD111111);
            for (int i = 0; i < 9; i++) {
                int x = left + i * 20;
                g.fill(x, top, x + 19, top + 19, i == previewInventory.selectedHotbar() ? 0xFFEED588 : 0xFF777777);
                g.fill(x + 1, top + 1, x + 18, top + 18, 0xFF333333);
                var stack = previewInventory.get(27 + i);
                if (!stack.isEmpty()) { g.item(stack, x + 1, top + 1); g.itemDecorations(Minecraft.getInstance().font, stack, x + 1, top + 1); }
            }
            g.text(Minecraft.getInstance().font, "PREVIEW", left + 66, top - 10, 0xFFFFD780);
        });
        HudElementRegistry.replaceElement(VanillaHudElements.HELD_ITEM_TOOLTIP, original -> (g, delta) -> {
            if (previewInventory == null) original.extractRenderState(g, delta);
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> {
            WhatIsThatCommand.register(dispatcher, repository);
            dispatcher.register(ClientCommands.literal("itemlist")
                    .executes(cmd -> { cmd.getSource().getClient().execute(() -> cmd.getSource().getClient().setScreen(new BrowserScreen(this, browserState))); return 1; })
                    .then(ClientCommands.literal("preview")
                            .then(ClientCommands.literal("start").executes(cmd -> { cmd.getSource().getClient().execute(() -> beginPreview(null)); return 1; }))
                            .then(ClientCommands.literal("end").executes(cmd -> { cmd.getSource().getClient().execute(this::endPreview); return 1; })))
                    .then(ClientCommands.literal("refresh").executes(cmd -> { refreshWithChat(); return 1; }))
                    .then(ClientCommands.literal("show")
                            .then(ClientCommands.literal("crate")
                                    .then(ClientCommands.argument("id", IntegerArgumentType.integer(1))
                                            .executes(cmd -> { cmd.getSource().getClient().execute(() -> CrateRewardLinks.open(this, false,
                                                    IntegerArgumentType.getInteger(cmd, "id"))); return 1; })))
                            .then(ClientCommands.literal("misc")
                                    .then(ClientCommands.argument("id", IntegerArgumentType.integer(1))
                                            .executes(cmd -> { cmd.getSource().getClient().execute(() -> CrateRewardLinks.open(this, true,
                                                    IntegerArgumentType.getInteger(cmd, "id"))); return 1; }))))
                    .then(ClientCommands.literal("status").executes(cmd -> { cmd.getSource().sendFeedback(statusMessage()); return 1; })));
        });
    }

    private Component statusMessage() {
        ItemDatabase db = repository.database();
        return Component.literal("MysticMC: " + (db == null ? "0 items" : db.items.size() + " items (" + db.crateCount + " crate, " + db.miscCount + " misc)")
                + "; sync: " + repository.status() + "; " + (repository.isCached() ? "cached" : "fresh")
                + "; preview: " + (previewInventory == null ? "off" : "active")
                + (db == null ? "" : "; last success: " + db.syncedAt));
    }

    public void refreshWithChat() {
        Minecraft client = Minecraft.getInstance();
        boolean started = repository.refresh(message -> client.execute(() -> {
            if (client.player != null) client.gui.getChat().addClientSystemMessage(Component.literal(message));
        }));
        if (client.player != null) client.gui.getChat().addClientSystemMessage(Component.literal(started ? "MysticMC: refreshing items..." : "MysticMC: refresh already running."));
    }
}
