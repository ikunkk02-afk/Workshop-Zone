# Changelog

All notable changes to Workshop Zone will be documented in this file.

## [1.0.0] - 2026-08-05

### Added

- **Workshop sidebar**: automatically displays connected containers and workstations when a supported GUI is opened. Quick switching with a single click.
- **Container labels**: exact-item labels, Item Tag category labels, and custom multi-label whitelists (up to 32 entries). Labels restrict insertion via manual, Shift, hopper, and Transfer API paths; extraction is never restricted.
- **Double chest support**: connected chests are merged into a single logical container. Labels are synchronized across both halves.
- **One-click deposit**: moves matching items from the player inventory into labeled containers. Exact labels take priority; Shift-click includes the hotbar.
- **Workshop inventory search**: candidates are limited to items actually present in the workshop. Supports Chinese, English, and registry ID search. Displays total quantity and container counts. Container opening and single/all highlighting.
- **Warehouse-assisted crafting**: supports shaped and shapeless vanilla 3×3 `CraftingRecipe` variants. Regular click fills one set of materials; Shift-click fills the maximum safe batch (up to 64 iterations).
- **Batch crafting**: server-authoritative maximum calculation using binary search on a min-cost flow solver. Respects per-slot stack limits and a 64-iteration safety cap. Single outer Fabric Transfer API transaction.
- **Crafting confirmation overlay**: reusable overlay with scrollable material list, player/storage breakdown, recipe output preview, and accept/cancel buttons. Suppresses recipe book during display.
- **Responsive layout**: supports right, left, top, bottom, and custom (drag-saved) positions. Adapts to window size, GUI scale, and text width. Compact and narrow modes for tight spaces.
- **JEI/EMI/REI detection**: automatic recipe-viewer-aware sidebar placement. Public JEI exclusion area integration. All three viewers are optional; the vanilla recipe book is the primary crafting workflow.
- **Multiplayer and dedicated server support**: per-player session isolation, concurrent access safety, transaction atomicity.
- **Simplified Chinese and English localization**: all UI text, messages, and tooltips are translatable via resource JSON files.

### Security

- All client requests are re-validated server-side against the current session, revision, sync ID, and permissions.
- Clients cannot specify arbitrary dimensions, container coordinates, or workshop areas.
- Unloaded chunks are never force-loaded.
- Vanilla locks and protection callbacks are respected.
- Failed transactions roll back completely — no partial extraction or item loss.
- Workshop inventory search does not expose the contents of inaccessible containers.

### Known Limitations

- Minecraft 1.21.1 Fabric only.
- Workshop is dimension-scoped; unloaded chunks are not accessed.
- No persistent workshop core block.
- Search catalog updates on manual refresh, not every tick.
- Warehouse-assisted crafting supports only the vanilla 3×3 `CraftingRecipe` grid. The grid must be empty before requesting a refill.
- Recursive sub-recipes, auto-smelting, and automatic continuous restocking are not supported.
- Modded machine inventories are not supported.
- Mods that bypass `Inventory` or Transfer API to write slots directly are not guaranteed compatible.
- Server land-claim permissions require compatible callbacks or protection-mod adaptation.
