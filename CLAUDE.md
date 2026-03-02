# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Architect's Toolbox is a client-side Minecraft mod for measuring, counting, and visualizing block selections with shape modes, clipboard operations, and ghost block previews. Built with **Architectury API** for cross-loader support (Fabric + NeoForge).

- **Minecraft**: 1.21.10 | **Java**: 21 | **Architectury API**: 18.0.6
- **Fabric Loader**: 0.18.4 | **Fabric API**: 0.138.4+1.21.10 | **NeoForge**: 21.10.50-beta
- **Mod ID**: `architects_toolbox` | **Package**: `com.ArchitectsToolbox`

## Build Commands

```bash
./gradlew build                    # Build all platforms (outputs to fabric/build/libs/ and neoforge/build/libs/)
./gradlew :fabric:runClient        # Run Fabric client
./gradlew :neoforge:runClient      # Run NeoForge client
./gradlew clean                    # Clean build artifacts
./gradlew genSources               # Generate IDE sources
```

## Project Structure

```
common/   - Shared code (all game logic, rendering, GUI, mixins)
fabric/   - Fabric entry point + ModMenu integration (2 files)
neoforge/ - NeoForge entry point (1 file)
```

Platform modules are thin wrappers that call `ArchitectsToolbox.init()` + `ArchitectsToolboxClient.initClient()`. All logic lives in `common/`.

> Detailed docs: [Docs/architecture.md](Docs/architecture.md) | [Docs/rendering.md](Docs/rendering.md) | [Docs/managers.md](Docs/managers.md) | [Docs/gui.md](Docs/gui.md) | [Docs/commands.md](Docs/commands.md) | [Docs/extending.md](Docs/extending.md)

## Package Layout (common module)

```
com.ArchitectsToolbox/
  ArchitectsToolbox.java           # MOD_ID, Logger, init()
  ArchitectsToolboxClient.java     # Keybinding, tick/HUD events, input handling
  SelectionManager.java            # Block selection state (singleton, cached bounds)
  ClipboardManager.java            # Copy/paste, ghost blocks, locked placements
  BlockCounter.java                # Block counting within shapes (time-cached)
  ModConfig.java                   # Persistent JSON config (Platform.getConfigFolder())
  UndoRedoManager.java             # Undo/redo with compound operations
  shapes/ShapeMode.java            # RECTANGLE, CYLINDER, ELLIPSOID, SPLINE, LINE
  shapes/EllipsoidMode.java        # FIT_TO_BOX, CENTERED
  util/SplineMath.java             # Catmull-Rom spline math
  command/ToolboxCommands.java     # Client-side /atb commands (Brigadier + Architectury)
  mixin/WorldRendererMixin.java    # Injects into LevelRenderer.renderLevel @TAIL
  render/                          # See Docs/rendering.md
  gui/                             # See Docs/gui.md
```

## Key Architectury Patterns

All cross-loader abstractions are in `ArchitectsToolboxClient.initClient()`:
```java
KeyMappingRegistry.register(KEY_BINDING_MEASURE);                                   // dev.architectury.registry.client.keymappings
ClientTickEvent.CLIENT_POST.register(instance -> onClientTick(...));                 // dev.architectury.event.events.client
ClientGuiEvent.RENDER_HUD.register((gui, delta) -> onHudRender(...));
ClientCommandRegistrationEvent.EVENT.register(ToolboxCommands::register);            // dev.architectury.event.events.client
```

Config uses `Platform.getConfigFolder()` (not ServiceLoader). No platform-specific interfaces needed.

## Design Patterns

- **Singletons**: All managers use eager `private static final INSTANCE = new ...()` (except ModConfig which is lazy)
- **Renderer interface**: `ShapeRenderer.render(Camera, Matrix4f, List<BlockPos>, RenderConfig)`
- **Caching**: SelectionManager (bounds), BlockCounter (100ms TTL), HollowShapeRenderer (selection-change), GhostBlockRenderer (vertex data)
- **Buffer allocation**: Renderers maintain reusable `ByteBufferBuilder` instances

## Rendering Pipeline

`WorldRendererMixin` -> `MeasurementRenderer.render()` -> shape-specific `ShapeRenderer` -> `RenderUtils.drawLine()`/`drawBox()`/`renderFloatingText()`

## Commands

Client-side commands registered via `ClientCommandRegistrationEvent` (Architectury). See [Docs/commands.md](Docs/commands.md).

- `/atb pos1 <x> <y> <z>` — Set selection corner 1
- `/atb pos2 <x> <y> <z>` — Set selection corner 2
- `/atb clear` — Clear selection
- `/atb shape <mode>` — Switch shape mode (rectangle/cylinder/ellipsoid/spline/line)
- `/atb info` — Print selection info with clickable coordinates (copies to clipboard)
- `/atb identify [radius]` — Scan biomes + structures (clickable IDs); falls back to player pos if no selection

Commands use raw Brigadier builders (`LiteralArgumentBuilder.literal()`) not `Commands.literal()` (which is tied to server `CommandSourceStack`).

## Input

- **G (tap)**: Add block / confirm paste | **G (hold 200ms)**: Radial menu
- **Arrow keys**: Layer navigation or paste rotation
- **Scroll**: Shape-specific adjustments (subdivisions, radius, tube radius)
