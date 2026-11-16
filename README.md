# Architect's Toolbox

A client-side Minecraft mod for planning builds. Measure shapes in the world, count the blocks inside them, and copy builds as ghost guides or place them in creative.

Works in singleplayer and on multiplayer servers without a server-side install. Structure bounds are singleplayer-only; placing real blocks on a multiplayer server requires command permission.

## Installation

Choose the jar for your Minecraft version and loader, remove any older Architect's Toolbox jar, and put the new jar in your client's `mods` folder.

| Minecraft | Java | Fabric | NeoForge |
| --- | --- | --- | --- |
| 1.21.1 | 21 | Loader 0.16.0+ and Fabric API for 1.21.1 | 21.1.252 |
| 26.3 | 25 | Loader 0.19.5+ and Fabric API for 26.3 | 26.3.0.71-beta |

The NeoForge versions listed are the build targets. Architectury API is no longer required. Version 2 supports 1.21.1 and 26.3 on both loaders, including 26.3's OpenGL and Vulkan backends. There are no v2 builds for 1.21.10 or 1.21.11.

## Getting started

1. Tap **G** while looking at a block to add a measurement point. For a box, select two opposite corners.
2. Hold **G** to open the wheel. Hover an item and release **G** to choose it, or click the item. Press **Esc** to cancel.
3. Scroll to change wheel pages, or click a tab. Use **Shift + scroll** over an item to adjust its setting.
4. Choose a shape on the **Shape** page. Turn on block counting, hollow view or layer view on **Measurement**.
5. To start another measurement, pick a colour on **Colour**, or choose **New measurement** on **Edit** to keep the same colour.

The next-point preview shows what another tap would add. Toggle **Preview** on the Edit page to hide it while keeping the existing measurement visible. Points can still be added with preview off.

## Measurements and shapes

Keep several measurements visible at once, each with its own colour, shape and settings. The default limit is 16. Picking a colour starts a new measurement when the active one already has points; it does not recolour the existing measurement. Use **Active measurement** on Edit to cycle between them, or Shift + scroll over it to cycle in either direction.

| Shape | What it measures | Shift + scroll over its wheel item |
| --- | --- | --- |
| Rectangle | An inclusive box around the points, with X/Y/Z dimensions | Change subdivision guides |
| Cylinder | A cylinder fitted to the points' box, including elliptical cross-sections and even diameters | Cycle Y, X and Z axes, and centre + rim mode |
| Ellipsoid | An ellipsoid fitted to the points' box | Switch between box-fit and centre + radius |
| Line | Straight segments through the points, with lengths and angles | Change tube radius |
| Spline | A smooth curve through the points, with length labels | Change tube radius |

Switching shapes keeps the points. Line and spline follow the order in which points were added.

On the **Measurement** page, **Count** lists non-air blocks inside the filled shape, **Hollow** shows its block shell, and **Layer** slices the displayed shape one Y-level at a time. These settings belong to the active measurement. Counts for enabled measurements appear together in their colours. Count still uses the filled shape when hollow view is on.

**Clear** removes the active measurement; **Clear all** removes every measurement. Locked ghost placements have their own **Clear placements** action. **Undo** and **Redo** cover measurement edits, locked placements and real block placement.

## Clipboard and placement

Choose **Copy** on Edit to copy non-air block states inside the active shape. Choose **Paste** to attach a ghost preview to your crosshair, rotate it with **Left / Right**, and tap **G** to confirm. Rotation resets for each new paste. Choose **Cancel paste** to dismiss the preview.

- **Survival and adventure:** confirmation locks a ghost guide for building by hand.
- **Creative singleplayer:** confirmation places real blocks.
- **Creative multiplayer:** confirmation places through paced `/fill` and `/setblock` commands when you have permission. Without permission, it locks a ghost guide and shows a message. Server rules and command limits still apply.

Placement fills air by default. Enable **Placing replaces blocks** in settings to overwrite existing blocks. Copy stores block states only: inventory contents, sign text and other block-entity data are not copied. Undo restores block states, not block-entity contents. Copy and placement use chunks loaded by your client.

Layer view also slices locked ghost guides. Measurements, the clipboard, ghost guides and undo history stay in memory while the client runs, including across world changes, but are not saved to disk. Real placed blocks are saved with the world.

## World overlays

The **World** page controls chunk boundaries, biome borders and structure bounds independently of the active measurement.

- **Chunks:** boundaries around your current chunk, using the world's actual height.
- **Biomes:** nearby borders and biome IDs at your current elevation.
- **Structures:** boxes and IDs for structures intersecting client-loaded chunks, with a colour per structure type. Shift + scroll over the item to show piece boxes, such as village houses or stronghold rooms. Singleplayer only.

## Controls

All bindings are under **Options → Controls → Architect's Toolbox**, and can be changed to keyboard or mouse buttons.

| Default input | Action |
| --- | --- |
| G, tap | Add a point or confirm a paste |
| G, hold | Open the wheel; release to choose the hovered item |
| Scroll in the wheel | Change page |
| Shift + scroll over a wheel item | Adjust its option |
| Up / Down | Step through layers |
| Left / Right | Rotate the paste preview by 90° |
| Unbound | Toggle next-point preview |
| Unbound | Pick the next palette colour |

## Commands

These commands run on the client. Measurement commands act on the active measurement.

| Command | Action |
| --- | --- |
| `/atb pos1 <x> <y> <z>` | Set the first point |
| `/atb pos2 <x> <y> <z>` | Set the second point; requires a first point |
| `/atb clear` | Remove the active measurement |
| `/atb clear all` | Remove every measurement |
| `/atb shape <rectangle\|cylinder\|ellipsoid\|spline\|line>` | Change shape |
| `/atb color <name\|#rrggbb>` | Pick a colour, starting a new measurement if the active one has points |
| `/atb info` | Show shape, points, bounds, box size/volume and the completed count when enabled; click coordinates to copy |
| `/atb identify [radius]` | List biomes and, in singleplayer, structures in the measurement's box, or around you without points; optional radius 0–256 |
| `/atb config` | Open settings on either loader |

For example, `/atb color blue` or `/atb color #55aaff` picks a colour. Command completion suggests colour names and palette entries.

## Settings and limits

Open `/atb config`, or use the mod-list config button on NeoForge. Fabric does not require Mod Menu and uses the command.

Settings are saved in `config/architects_toolbox.json`: ghost style (solid or wireframe), ghost opacity, whether placement replaces blocks, the colour palette and the measurement limit. New installations use solid ghosts at 50% opacity, an eight-colour palette and a 16-measurement limit.

Large operations have limits and display messages when exceeded. Copy allows up to 1,000,000 copied blocks and a 16,000,000-block scan. Ghost models stop above 250,000 displayed blocks, and detailed outlines fall back to a box above 200,000 faces. Very large copies can briefly pause the client.

## Upgrading from 1.0.0

Version 2 rebuilds the tools around multiple measurements, a paged wheel, live next-point previews, shape-aware copying and creative placement. Structure scanning, rebound hotkeys and ghost style/opacity settings have been rebuilt or fixed.

- Keep `config/architects_toolbox.json`. The existing `ghostBlockRenderMode` and `ghostBlockOpacity` values carry over; new settings get defaults. An existing `WIREFRAME` setting stays wireframe.
- The measure key's ID is unchanged, so its saved binding carries over. The arrow actions are now rebindable; preview and next-colour shortcuts start unbound.
- Plain scroll now changes wheel pages. Use Shift + scroll to change shape options or structure-piece visibility.
- Cylinder defaults to box-fit instead of centre + radius offset. Use Shift + scroll for its axis or centre + rim mode. Ellipsoid mode is now a per-measurement wheel option.
- Clear affects the active measurement. Use Clear all for all measurements and Clear placements for locked ghosts.
- Confirming a paste in creative can now change the world. Replacement is off by default.
- The old unreachable mirror, stack-paste and exact-point-copy helpers are removed; nudge is not included.

The mod ID remains `architects_toolbox`, and the existing `/atb` command names and argument order are retained. No measurement data needs world migration because measurements are not saved with worlds.

## Building from source

Use JDK 25 to run Gradle, with JDK 21 available for 1.21.1 development clients. Stonecutter builds both Minecraft versions from shared sources; no branch switching is needed.

```bash
./gradlew build
```

Loader jars are written under `<loader>/versions/<mc>/build/libs/`. Use the jar without a `sources`, `dev-shadow` or `raw` classifier. To build or launch one target, use its node path, for example:

```bash
./gradlew :fabric:1.21.1:build
./gradlew :neoforge:26.3:runClient
```

Shared logic lives in `common/src/`; `fabric/` and `neoforge/` contain loader integration. Version-specific source files live under each module's `versions/<mc>/src/`, and dependency pins are in `versions/<mc>.properties`.

## License

All Rights Reserved.
