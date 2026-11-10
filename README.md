# MysticMC Item Browser

Client-side Fabric mod for Minecraft Java Edition **26.1.2**. Browse and search the MysticMC item database, inspect native Minecraft tooltips, and experiment with items in an isolated virtual inventory. The mod uses Minecraft's unobfuscated Mojang names and Java 25.

## Install

1. Install Fabric Loader for Minecraft 26.1.2 and a compatible Fabric API.
2. Copy `build/libs/mystic-item-browser-26.1.2-v1.0.0.jar` into the Minecraft `mods` folder.
3. Start Minecraft. The first sync happens once, in the background. The cache lives at `config/mystic-item-browser/items-v1.json` and supports later offline browsing.

Build from source with Java 25: `./gradlew build` (`.\gradlew.bat build` on Windows). The release JAR is in `build/libs/`.

Every branch push to GitHub runs `.github/workflows/release-on-push.yml`. After a successful build and test run, the workflow publishes the mod JAR as a GitHub Release tagged `build-<commit SHA>`. It uses the repository's `GITHUB_TOKEN` with `contents: write`; repeat runs for the same commit leave its existing release in place.

## Commands and controls

| Command | Effect |
| --- | --- |
| `/itemlist` | Open the item browser. |
| `/itemlist preview start` | Start a fresh virtual preview session and open the Mystic catalog. |
| `/itemlist preview end` | Discard the virtual inventory and show the real inventory again. |
| `/itemlist refresh` | Force one asynchronous full API refresh. |
| `/itemlist status` | Show item counts, sync time and status, and preview status. |
| `/whatisthat <item name>` | Look up the underlying Minecraft item ID from a catalog name. Tab completion offers names without decorative symbols. |

The **Open MysticMC Item Browser** keybind appears in Controls; it is unassigned by default to avoid conflicting with existing controls.

When a MysticMC crate announcement names an item in the catalog, hover over the reward name in chat to see its reconstructed item tooltip and lore. Click the reward name to open that item's detail screen. The rest of the server message keeps its original text and formatting. Announcements with no unique catalog match remain unchanged.

## Item capture and upload

While hovering an item in a container, use **Capture and upload hovered item** (numpad minus by default) to copy its four-space-indented item stack JSON to the clipboard, save `item.json` and a cropped `preview.png` or animated `preview.gif` under `screenshots/mystic-item-browser-item-queue/`, and upload them when configured. Animated textures record one complete loop; loops longer than 60 seconds are skipped. The container and item must stay open until recording finishes. Saved files remain available if upload fails.

Use **Save hovered item image** to save only a PNG or GIF directly in Minecraft's `screenshots/` folder. This keybind is unassigned by default. It does not copy JSON, create a queue folder, or upload. Set both keybinds in Minecraft's vanilla **Options → Controls → Key Binds** menu.

On first launch, the mod creates `config/mystic-item-browser-upload.json`. Put your API key in `apiKey` and the full upload URL in `dataEndpoint`. Leave `dataEndpoint` blank for local-only capture. The file is read each time you start a capture, so edits do not require restarting Minecraft. Uploads use a multipart POST with `apiKey`, `json`, `image`, and `extension` fields. The endpoint must use HTTPS, except HTTP on localhost for testing. A 2xx response counts as success; failed uploads do not retry automatically. This mod has no config menu.

In Preview Mode, the normal inventory key opens the Mystic item catalog and virtual inventory. The game HUD displays the virtual hotbar while the session is active. **Close** or Escape hides the preview screen so you can type `/itemlist preview end`; **End** on the screen does the same action directly. Returning to the browser and reopening Preview preserves the session. A new `start` discards the previous virtual session. Disconnecting or changing worlds also discards it.

The browser and Preview Mode both support searching and filters for tags (any/all), source, crate, miscellaneous group, and rarity. Crate and Group open choice menus. Source cycles through all, crate, and miscellaneous items. Rarity cycles through Rare, Legendary, Exotic, Mystic, Eternal, and Any; matching ignores case. Clear resets the search and all filters. Preview Mode has a separate **Clear items** button for the virtual inventory. On the Items tab, left-click a catalog item to pick up a virtual copy, then switch to Inventory to place or equip it; right-click picks up one; Shift-click adds a stack directly to virtual inventory. In virtual slots, left-click picks up or places, right-click splits or places one, Shift-click moves between main inventory and hotbar, middle-click duplicates, number keys swap with a hotbar slot, and dragging places single items. The four armor slots and offhand slot sit above the main inventory with the vanilla empty-slot icons. The player model uses virtual armor and hand items on the Inventory tab and in third-person world view. Paper and feather headwear uses the resource pack's head item model.

## Inventory safety

The mod never writes to the real player inventory, real cursor, selected hotbar slot, or server container. Preview Mode uses a separate client-only `VirtualInventory` and custom `Screen`, then redirects the normal inventory screen while the session is active. No inventory click, Creative Mode modification, or item drop packets are sent by preview interactions. Ending Preview Mode simply removes the visual replacement; it does not need to restore ItemStacks because it never changed them.

## Data and limitations

The API data comes from the [MysticMC API](https://items.mysticmc.co/apidocs): `/api/items`, `/api/miscitems`, `/api/crates`, `/api/miscgroups`, and `/api/tags`. A full sync runs once per launch because the API does not document a reliable data change token. The complete responses are cached with atomic replacement after validation. Failed refreshes leave the last valid cache in place.

ItemStacks are reconstructed lazily through Minecraft's native codec. The API contains some older component shapes; the mod converts their enchantment, attribute, custom model data, color, and text shapes before decoding, including items inside containers. It also repairs misdecoded Unicode and serialized blank lines in names and lore. Custom enchantment IDs unavailable in the active client registry appear as tooltip lines while supported enchantments remain on the item. The original JSON remains in the cache. Custom models and armor appearance require their resource pack assets to be active. The mod does not download website icons as fallback images. **Browse site** opens the specific catalog page for crate items or the containing group page for miscellaneous items.

Preview equipment changes only the local player's rendered third-person model. Other players and the server still see the real equipment. First-person held-item rendering still uses the real item. The current reconstruction and rendering changes compile and pass the automated tests; the revised third-person armor and wing appearance still needs a visual in-game check with the intended resource pack.
