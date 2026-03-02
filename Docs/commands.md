# Commands

All commands are registered as **client-side commands** via `ClientCommandRegistrationEvent` (Architectury). They work in both singleplayer and multiplayer without requiring the mod on the server.

Entry point: `ToolboxCommands.register()` called from `ArchitectsToolboxClient.initClient()`.

## Command Reference

### `/atb pos1 <x> <y> <z>`
Sets the first corner of the selection to the given coordinates. If a selection already exists, updates position 1 (index 0 in `selectedBlocks`) while preserving all other points.

Saves undo state. Invalidates hollow shape renderer cache.

### `/atb pos2 <x> <y> <z>`
Sets the second corner. Requires at least one point to exist (returns an error if selection is empty). Updates position 2 (index 1 in `selectedBlocks`) while preserving all other points.

Saves undo state. Invalidates hollow shape renderer cache.

### `/atb clear`
Clears the entire selection. Equivalent to the radial menu clear action. Calls `SelectionManager.clearSelection()` (which auto-saves undo state).

### `/atb shape <mode>`
Sets the current shape mode. Tab-completes to available modes.

| Subcommand | ShapeMode |
|---|---|
| `rectangle` | `ShapeMode.RECTANGLE` |
| `cylinder` | `ShapeMode.CYLINDER` |
| `ellipsoid` | `ShapeMode.ELLIPSOID` |
| `spline` | `ShapeMode.SPLINE` |
| `line` | `ShapeMode.LINE` |

### `/atb info`
Prints the current selection info to chat with **clickable coordinate components**.

**Output lines:**
1. Header
2. Shape mode + control point count
3. Min and Max bounds — each `[x, y, z]` is **clickable** (copies `x y z` to clipboard)
4. Both corners combined — `[x1, y1, z1  →  x2, y2, z2]` is **clickable** (copies `x1 y1 z1 x2 y2 z2`)
5. Dimensions (X × Y × Z) and total bounding volume
6. Block count breakdown (only shown when block counting is enabled)

### `/atb identify [radius]`
Scans for biomes and structures within the selection area. All IDs are **clickable** (copies the full resource ID to clipboard).

**Fallback behavior:**
- With selection: scans the selection bounds
- Without selection: uses the player's current position
- With optional `radius` (0–256): expands the scan area by that many blocks in all directions

**Biomes** (client-side, works everywhere):
- Sampled at 4-block intervals (matches biome storage resolution)
- When using radius with a selection, biomes found only outside the selection show distance

**Structures** (singleplayer only — requires integrated server access):
- Scans loaded chunks in the area for structure starts
- Shows structure center position as clickable coordinates
- Shows distance from selection when the structure is outside the selection bounds
- On multiplayer servers, displays a notice that structure data is unavailable

**Example output:**
```
--- Identify ---
Search radius: 32
Biomes:
  minecraft:plains
  minecraft:forest (~18 blocks away)
Structures:
  minecraft:village at [120, 64, -340]
```

## Clickable Coordinates

Coordinate components use `ClickEvent.Action.COPY_TO_CLIPBOARD`. Hovering shows a tooltip with the text to be copied.

**Formats copied:**
- Single corner: `x y z` (space-separated, no brackets)
- Both corners: `x1 y1 z1 x2 y2 z2` (space-separated, suitable for pasting into WorldEdit-style tools or isometric renderers that accept two-corner input)

## Implementation Notes

- **Source type**: `ClientCommandRegistrationEvent.ClientCommandSourceStack` (Architectury's client command source)
- **Messaging**: `Minecraft.getInstance().player.displayClientMessage()` (consistent with rest of codebase)
- **Undo integration**: `pos1`/`pos2` call `UndoRedoManager.getInstance().saveState()` before modifying selection via `setSelectedBlocks()`. (`setSelectedBlocks` does not auto-save undo state, unlike `addBlock`/`clearSelection`.)
- **Renderer invalidation**: `MeasurementRenderer.getInstance().invalidateHollowShapeCache()` is called after `pos1`, `pos2`, and `clear` to keep the hollow shape renderer in sync.
