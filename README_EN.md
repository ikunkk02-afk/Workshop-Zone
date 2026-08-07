# Workshop Zone (工坊域)

![Workshop Zone Cover](src/main/resources/assets/workshop_zone/cover.png)

[简体中文](README.md) | [English](README_EN.md)

[![GitHub Release](https://img.shields.io/github/v/release/ikunkk02-afk/Workshop-Zone?label=release)](https://github.com/ikunkk02-afk/Workshop-Zone/releases/latest)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-success)](https://minecraft.net)
[![Fabric](https://img.shields.io/badge/Fabric-0.19.3%2B-blue)](https://fabricmc.net)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://adoptium.net)
[![License](https://img.shields.io/badge/License-MIT-lightgrey)](LICENSE)

Workshop Zone connects nearby containers and vanilla workstations into a temporary workshop. It adds a responsive sidebar for quick switching, smart storage labels, one-click deposit, inventory search, container highlighting, and safe warehouse-assisted crafting — all without replacing vanilla screens.

It does not add new machines or replace the vanilla GUI. Instead, it provides a unified workshop management layer on top of existing containers and workstations.

## Features

### Workshop Sidebar

- Automatically appears next to supported container and workstation GUIs.
- Quickly switch between chests, barrels, crafting tables, furnaces, anvils, and more.
- Double chests appear as a single logical container with merged inventory.
- Unloaded chunks are never force-loaded.

### Container Labels

- **Exact-item labels**: restrict input to a single item type.
- **Item Tag labels**: filter by Minecraft item categories (e.g. all logs, all planks).
- **Custom multi-label whitelists**: mix exact items and Item Tags, up to 32 entries.
- Labels restrict insertion; extraction is always allowed.
- Works with manual insertion, Shift quick-move, vanilla hoppers, and standard Fabric Transfer API input.
- Double chest halves keep labels in sync automatically.

### One-Click Deposit

- Automatically moves matching items from your inventory into labeled containers.
- Exact-item labels take priority over categories and whitelists.
- Regular click excludes the hotbar; Shift-click includes the hotbar.
- Unlabeled containers do not receive items automatically.

### Workshop Inventory Search

- Candidates are limited to items actually present in the current workshop — not the entire item registry.
- Search by Chinese name, English name, or registry ID.
- Shows total quantity and the number of containers storing each item.
- Click a result to open the corresponding container.
- Highlight a single container or all matching containers (including both double-chest halves).

### Warehouse-Assisted Crafting

- Supports all standard shaped and shapeless `CraftingRecipe` variants in the vanilla 3×3 crafting table.
- Player inventory materials are used first; workshop storage only fills the deficit.
- **Regular click** on a recipe-book recipe: shows a confirmation panel when storage can complete the recipe. Confirms one craft at a time.
- **Shift-click** on a recipe-book recipe: calculates the maximum safe combined batch using player and storage inventories (up to 64), shows a batch confirmation panel, then fills each grid slot with the batch quantity.
- The 64-iteration cap is constrained by available stock, per-slot stack limits, and a hard safety limit.
- No items are moved or reserved before confirmation. Confirmation re-validates: reduced materials cause a full-batch rejection with no partial fill; increased materials do not silently expand the batch.
- Only the 3×3 grid is filled — the mod does not directly generate output items. The vanilla output slot handles results, remainders, statistics, and unlocks.
- A batch fill uses a single outer transaction, never a loop of single-craft transactions.
- JEI and REI standard crafting-table transfer buttons use the same warehouse-refill pipeline. EMI remains optional and load-safe, but its fill-button takeover is blocked by its current public handler-order API.

### Responsive Layout

- Supports right, left, top, bottom, and custom positions.
- Drag the title bar to save a custom position.
- Adapts to window size, GUI scale, and language text width.
- Automatically detects JEI, EMI, and REI; AUTO mode prefers the top position when a recipe viewer is present.
- JEI, EMI, and REI are never required.

## Supported Blocks

**Storage containers:**
- Chest
- Trapped Chest
- Barrel
- Double Chest (logically merged)

**Workstations:**
- Crafting Table
- Furnace
- Blast Furnace
- Smoker
- Smithing Table
- Anvil / Chipped Anvil / Damaged Anvil
- Stonecutter
- Grindstone
- Loom
- Cartography Table
- Brewing Stand
- Enchanting Table

**Not currently supported:**
- Shulker Boxes
- Hoppers as workshop storage containers (hopper input filtering is supported)
- Droppers, Dispensers, Fletching Tables
- Crafter
- Modded machines

## Requirements

**Required:**
- Minecraft 1.21.1
- Fabric Loader 0.19.3 or higher
- Fabric API 0.116.15+1.21.1 or higher
- Java 21

**Optional:**
- JEI
- EMI
- REI

**Installation:**
1. Install Fabric Loader for Minecraft 1.21.1.
2. Place Fabric API and the Workshop Zone JAR into the `mods` folder.
3. Launch the game.
4. For multiplayer, install Workshop Zone and Fabric API on both the server and all joining clients.

## Quick Start

1. Place chests, barrels, and workstations nearby.
2. Open any supported GUI.
3. Click entries in the workshop sidebar to switch devices.
4. Open the container label editor to set storage rules.
5. Click the deposit button to sort matching items from your inventory.
6. Use the search button to find items across the workshop.
7. In the crafting table, click a recipe-book recipe: confirm when your inventory falls short.
8. Shift-click a recipe-book recipe for batch material preparation.
9. With JEI or REI installed, use its standard crafting-table transfer button for the same single/batch refill flow.
10. Retrieve crafted results from the vanilla output slot after the grid is filled.

> The active scan range follows server-side logic (8 blocks horizontal, 4 blocks vertical). Targets must be in loaded chunks and within accessible range. The mod does not bypass vanilla locks or protection callbacks.

## Recipe Viewer Crafting Compatibility

- Standard 3×3 crafting-table transfers are supported for **JEI 19.43.0.393** and **REI 16.0.799**.
- A regular transfer requests one refill; JEI max-transfer and REI stacked-transfer request a batch refill.
- Every request calls vanilla `ClientPlayerInteractionManager#clickRecipe`, then follows `CraftRequestC2SPacket` and the existing server validation/transaction pipeline.
- If the player's inventory is sufficient, vanilla filling proceeds without a Workshop Zone confirmation. The existing confirmation overlay appears only when workshop storage is required.
- Workshop Zone only fills the vanilla crafting grid. It never creates output directly or sends crafted items to the cursor or inventory.
- Recipe viewers remain optional. Their adapter classes are not loaded when the corresponding mod is absent.
- Only standard `CraftingRecipe` displays with a real Recipe ID are supported. Virtual recipes, machine processing, recursive sub-recipes, and auto-smelting are not supported.
- JEI and REI sidebar exclusion zones use their public APIs.
- **EMI 1.1.24+1.21.1 is load-safe but its Fill button is not intercepted in this release.** EMI's public API only appends handlers while its built-in crafting handler is registered first and selected first. Reliable takeover would require prohibited internal access, reflection, or a Mixin.

## Known Limitations

- Minecraft 1.21.1 Fabric only.
- The workshop is dimension-scoped and does not access unloaded chunks.
- There is no persistent workshop core block at this time.
- The search catalog updates on entering search mode or manual refresh — not every tick.
- Warehouse-assisted crafting supports only the vanilla 3×3 `CraftingRecipe` grid.
- EMI Fill is not connected to warehouse refill yet; this awaits a public EMI handler-priority or prepend API.
- The crafting grid must be empty before requesting or confirming a refill.
- Recursive sub-recipes, auto-smelting, and automatic continuous restocking are not supported.
- Modded machine inventories are not supported.
- Mods that bypass `Inventory` or Transfer API to write slots directly are not guaranteed compatible.
- Server land-claim permissions require compatible callbacks or protection-mod adaptation.

## Bug Reports

- For crashes, include `latest.log` or `crash-report`.
- For functional issues, include Minecraft version, Fabric Loader, Fabric API, mod list, and reproduction steps.
- Submit via [GitHub Issues](https://github.com/ikunkk02-afk/Workshop-Zone/issues).
- Do not upload tokens, server passwords, or personal information in issues.

## Building from Source

Windows:
```powershell
.\gradlew.bat clean build
```

Linux / macOS:
```bash
./gradlew clean build
```

Output: `build/libs`

Development runs:
```bash
# Client
./gradlew runClient

# Server
./gradlew runServer

# JEI compatibility test
./gradlew runClient -Precipe_viewer=jei

# EMI load-safety test (Fill interception is not available in this release)
./gradlew runClient -Precipe_viewer=emi

# REI compatibility test
./gradlew runClient -Precipe_viewer=rei
```

- No recipe viewer is required for normal gameplay.
- Java 21 is required to build and run.

## Technical Documentation

Detailed technical documentation for developers, maintainers, and compatibility mod authors is available in [docs/TECHNICAL.md](docs/TECHNICAL.md).

## License

Workshop Zone is licensed under the [MIT License](LICENSE).

Author: 寿云 (Shouyun)
