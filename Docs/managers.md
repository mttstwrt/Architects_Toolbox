# Core Managers

All managers are singletons accessed via `getInstance()`.

## SelectionManager

Block selection state with cached bounds computation.

**State**: `List<BlockPos> selectedBlocks`, `ShapeMode`, `EllipsoidMode`, subdivision count, spline radius, cylinder radius offset, hollow mode, chunk boundaries, layer mode.

**Bounds caching**: `getMinPos()`/`getMaxPos()` lazily recompute only when `boundsCacheDirty` flag is set (by `addBlock`, `clearSelection`, `setSelectedBlocks`).

**Key methods**:
- `addBlock(pos)` / `clearSelection()` - auto-saves undo state
- `cycleSubdivision()` / `stepSubdivisionUp()`/`Down()` - preset values: 0,2,3,4,5,8,10,16
- `stepSplineRadiusUp()`/`Down()` - 0..16 range
- `stepCylinderRadiusUp()`/`Down()` - 0..256 range (0.5-block increments)
- `cycleLayerUp()`/`Down()` - navigate Y layers in hollow mode

## ClipboardManager

Copy/paste with rotation, mirroring, stacking, ghost block previews, locked placements, and layer view.

**Clipboard data**: `Map<BlockPos, BlockState>` stored as relative positions from anchor. Paste preview tracks `previewAnchorPos` + `previewRotation` (0-3, 90-degree increments).

**Key methods**:
- `copy(level)` - captures selection as relative block positions
- `paste(level)` - places blocks at preview position
- `rotateClockwise()`/`rotateCounterClockwise()` - 90-degree rotation
- `mirrorX()`/`mirrorZ()` - mirror operations
- `lockCurrentPlacement()` - convert preview to persistent ghost display
- `clearLockedPlacements()` / `removeLastPlacement()`
- Layer view: `isLayerViewEnabled()`, `cycleLayerUp()`/`Down()`

**Inner class**: `LockedPlacementData` - stores position, rotation, and computed visible blocks.

## UndoRedoManager

Undo/redo history for selection state changes.

**Storage**: Two stacks (`undoStack`, `redoStack`) of `List<BlockPos>` snapshots.

**Key methods**:
- `saveState()` - called automatically by SelectionManager before mutations
- `undo()` / `redo()` - restores selection state
- `beginCompoundOperation()` / `endCompoundOperation()` - group multiple changes

## ModConfig

Persistent JSON configuration using Gson. File: `{configDir}/architects_toolbox.json`.

Uses `Platform.getConfigFolder()` from Architectury (not ServiceLoader).

**Settings**:
- `ghostBlockRenderMode`: `WIREFRAME` (default) or `SOLID`
- `ghostBlockOpacity`: 0.0-1.0 (default 0.5)

Lazy singleton (loads on first `getInstance()` call). Auto-saves on every setter call.

## BlockCounter

Counts blocks within the current shape selection. Uses time-based caching (100ms TTL).

Calculations per shape mode:
- **Rectangle**: `dx * dy * dz`
- **Cylinder**: `pi * r^2 * height` (with radius offset)
- **Ellipsoid**: `(4/3) * pi * rx * ry * rz`
- **Spline/Line**: segment length + tube volume (if radius > 0)

Also provides distance/angle calculations for Line mode labels.
