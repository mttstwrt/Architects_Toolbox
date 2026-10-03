# Architect's Toolbox

A client-side Minecraft mod for planning builds. Select blocks, see the shape and its measurements in the world, count what's inside, and preview copied structures as ghost blocks before you build them.

It runs only on your client. It works in singleplayer and on multiplayer servers that don't have the mod installed.

## Features

- **Shapes.** Rectangle, cylinder, ellipsoid, spline (a smooth curve through your points) and line, each with live dimension labels.
- **Measurements.** Subdivision guides for splitting a shape into equal parts, plus hollow mode, which shows only the shell.
- **Layer view.** Step through a selection one Y-level at a time with the arrow keys.
- **Block counting.** An on-screen breakdown of the blocks inside the current shape.
- **Clipboard and ghost blocks.** Copy a selection, then paste it as a translucent preview you can rotate and lock in place to build against.
- **Undo and redo** for selection changes.
- **Overlays.** Chunk boundaries, biome borders and structure bounding boxes. Structure bounds work in singleplayer only.
- **Commands** for exact coordinates and lookups (listed below).

## Controls

| Input | Action |
| --- | --- |
| **G** (tap) | Add the block you're looking at to the selection, or confirm a paste |
| **G** (hold) | Open the radial menu (shape, copy/paste, overlays, undo/redo). Release over an option to pick it |
| **Scroll** over a menu option | Adjust that option: subdivisions, cylinder radius, spline or line thickness |
| **Left / Right** arrows | Rotate the paste preview |
| **Up / Down** arrows | Move between layers when layer view is on |

You can rebind G under Options → Controls → Architect's Toolbox.

## Commands

| Command | What it does |
| --- | --- |
| `/atb pos1 <x> <y> <z>` | Set the first corner |
| `/atb pos2 <x> <y> <z>` | Set the second corner |
| `/atb clear` | Clear the selection |
| `/atb shape <rectangle\|cylinder\|ellipsoid\|spline\|line>` | Switch shape |
| `/atb info` | Print the bounds, size and volume. Click a coordinate to copy it |
| `/atb identify [radius]` | List the biomes and structures in the selection, or around you if nothing is selected |

## Requirements

| | Version |
| --- | --- |
| Minecraft | 1.21.11 (backports on the `1.21.10` and `1.21.1` branches) |
| Java | 21 |
| Loader | Fabric 0.19.2+ with Fabric API, **or** NeoForge 21.11+ |
| Required mod | [Architectury API](https://modrinth.com/mod/architectury-api) 19.x |
| Optional | [Mod Menu](https://modrinth.com/mod/modmenu) (Fabric) for the in-game config screen |

## Building from source

```bash
./gradlew build
```

The jars are written to `fabric/build/libs/` and `neoforge/build/libs/`. To launch a development client:

```bash
./gradlew :fabric:runClient
./gradlew :neoforge:runClient
```

## Project layout

```
common/    All game logic, rendering, GUI and the mixin (shared by both loaders)
fabric/    Fabric entry point and Mod Menu hook
neoforge/  NeoForge entry point
Docs/      Developer documentation
scripts/   backport-renames.sh, used to cherry-pick features onto older-version branches
```

For more detail, see [Docs/architecture.md](Docs/architecture.md), [Docs/rendering.md](Docs/rendering.md) and [Docs/extending.md](Docs/extending.md).

## Supporting several Minecraft versions

`master` targets the newest supported version. Each older version has its own branch. A new feature is built on `master`, cherry-picked onto each version branch, and then adapted with `scripts/backport-renames.sh` plus a small adapter commit. Code that changes between versions sits behind `render/RenderBuffer`, `util/ToolboxText` and `util/RegistryHelpers`, so only those files need editing for each version.

## License

All Rights Reserved.
