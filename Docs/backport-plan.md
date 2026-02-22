# Plan: Backport Architect's Toolbox from MC 1.21.10 to MC 1.21.1

## Context

The mod targets MC 1.21.10. The user wants a 1.21.1 backport on a separate branch, with version-specific code abstracted so new features developed on `master` (1.21.10) can be easily merged into the 1.21.1 branch.

## Analysis Summary

**Version-specific code** is concentrated in 5 areas:

| Area | Files | Impact |
|------|-------|--------|
| `ByteBufferBuilder` -> `BufferBuilder` | 10 renderer files | Most widespread (introduced in 1.21.2+) |
| Mixin `renderLevel` signature | `WorldRendererMixin.java` | Different params between versions |
| `KeyMapping.Category` -> String | `ArchitectsToolboxClient.java` | Category registration changed |
| `KeyEvent` -> `(int, int, int)` | `RadialMenuScreen.java` | Screen input event API changed |
| `InputConstants.isKeyDown` takes Window vs long | `ArchitectsToolboxClient.java` | 4 call sites |

**Everything else** (VertexConsumer API, DeltaTracker, GuiGraphics, Component, all game logic, shapes, config) is identical between 1.21.1 and 1.21.10.

---

## Phase 1: Abstract ByteBufferBuilder on master (before branching)

This ensures new renderers added on main will merge cleanly to 1.21.1.

### 1a. Create `RenderBuffer.java` wrapper

**New file:** `common/src/main/java/com/ArchitectsToolbox/render/RenderBuffer.java`

```java
package com.ArchitectsToolbox.render;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.renderer.MultiBufferSource;

public class RenderBuffer {
    private final ByteBufferBuilder buffer;

    public RenderBuffer(int initialCapacity) {
        this.buffer = new ByteBufferBuilder(initialCapacity);
    }

    public MultiBufferSource.BufferSource createSource() {
        return MultiBufferSource.immediate(buffer);
    }
}
```

### 1b. Update all 10 renderer files

Mechanical transformation in each file:
- Remove `import com.mojang.blaze3d.vertex.ByteBufferBuilder;`
- `private static final ByteBufferBuilder X = new ByteBufferBuilder(N);` -> `private static final RenderBuffer X = new RenderBuffer(N);`
- `MultiBufferSource.immediate(X)` -> `X.createSource()`

Files (12 changes across 10 files):

| File | Field(s) | Size(s) |
|------|----------|---------|
| `render/RenderUtils.java:19` | `LABEL_BUFFER` | 256 |
| `render/RectangleRenderer.java:18` | `BUFFER` | 1536 |
| `render/CylinderRenderer.java:18` | `BUFFER` | 4096 |
| `render/EllipsoidRenderer.java:19` | `BUFFER` | 8192 |
| `render/SplineRenderer.java:19` | `BUFFER` | 8192 |
| `render/LineRenderer.java:19` | `BUFFER` | 8192 |
| `render/SubdivisionRenderer.java:18` | `BUFFER` | 4096 |
| `render/ChunkBoundaryRenderer.java:19` | `BUFFER` | 8192 |
| `render/HollowShapeRenderer.java:19` | `BUFFER` | 65536 |
| `render/GhostBlockRenderer.java:26,27` | `BUFFER`, `LINE_BUFFER` | 262144, 8192 |

### 1c. Commit on master

---

## Phase 2: Create `1.21.1` branch

```bash
git checkout -b 1.21.1
```

---

## Phase 3: Update dependency versions

### 3a. `gradle.properties`

| Property | 1.21.10 (current) | 1.21.1 (target) |
|----------|-------------------|-----------------|
| `minecraft_version` | `1.21.10` | `1.21.1` |
| `architectury_api_version` | `18.0.6` | `13.0.8` |
| `fabric_loader_version` | `0.18.4` | `0.16.9` |
| `fabric_api_version` | `0.138.4+1.21.10` | `0.106.0+1.21.1` |
| `neoforge_version` | `21.10.50-beta` | `21.1.77` |

### 3b. `fabric/build.gradle` - ModMenu version

`modImplementation "com.terraformersmc:modmenu:14.0.0"` -> `modImplementation "com.terraformersmc:modmenu:11.0.3"`

### 3c. `fabric/src/main/resources/fabric.mod.json`

- `"fabricloader": ">=0.18.4"` -> `">=0.16.0"`
- `"minecraft": "~1.21.10"` -> `"~1.21.1"`
- `"architectury": ">=18.0.6"` -> `">=13.0.6"`

### 3d. `neoforge/src/main/resources/META-INF/neoforge.mods.toml`

- NeoForge versionRange: `"[21.10,)"` -> `"[21.1,)"`
- Minecraft versionRange: `"[1.21.10,)"` -> `"[1.21.1,)"`
- Architectury versionRange: `"[18.0.6,)"` -> `"[13.0.6,)"`

---

## Phase 4: Update version-specific code

### 4a. `WorldRendererMixin.java` - Mixin signature

Replace entire file. The 1.21.1 `renderLevel` signature:
```java
(DeltaTracker, boolean, Camera, GameRenderer, LightTexture, Matrix4f, Matrix4f)
```
vs 1.21.10:
```java
(GraphicsResourceAllocator, DeltaTracker, boolean, Camera, Matrix4f, Matrix4f, Matrix4f, GpuBufferSlice, Vector4f, boolean)
```

Body unchanged: `MeasurementRenderer.getInstance().render(camera, positionMatrix);`

### 4b. `RenderBuffer.java` - Switch to BufferBuilder

Replace `ByteBufferBuilder` internals with `BufferBuilder` for 1.21.1:
```java
private final BufferBuilder buffer;
public RenderBuffer(int size) { buffer = new BufferBuilder(size); }
public MultiBufferSource.BufferSource createSource() { return MultiBufferSource.immediate(buffer); }
```

### 4c. `ArchitectsToolboxClient.java` - KeyMapping category

Change `KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(ResourceLocation...)` to `String KEY_CATEGORY = "key.categories.architects_toolbox"`. The `KeyMapping` constructor accepts String in 1.21.1.

### 4d. `ArchitectsToolboxClient.java` - InputConstants.isKeyDown

4 call sites: `InputConstants.isKeyDown(client.getWindow(), ...)` -> `InputConstants.isKeyDown(client.getWindow().getWindow(), ...)` (needs `long` handle in 1.21.1, not `Window` object).

### 4e. `RadialMenuScreen.java` - keyReleased signature

`keyReleased(KeyEvent keyEvent)` -> `keyReleased(int keyCode, int scanCode, int modifiers)`.
`keyEvent.key()` -> `keyCode`. Remove `KeyEvent` import.

### 4f. Language file - Add key category translation

Add `"key.categories.architects_toolbox": "Architect's Toolbox"` to `en_us.json` (needed for string-based key categories in 1.21.1).

---

## Phase 5: Build and verify

```bash
./gradlew clean build
```

If build fails, check:
1. `BufferBuilder` constructor signature (may need `Tesselator.getInstance().getBuilder()` pattern instead)
2. Loom version compatibility (downgrade `1.11-SNAPSHOT` to `1.9-SNAPSHOT` if needed)
3. NeoForge loader version in `neoforge.mods.toml`

---

## Files NOT changing (confirmed compatible)

All game logic, managers, shapes, config, math utils, and platform entry points use APIs that exist identically in both 1.21.1 and 1.21.10.

## Future merge workflow

After this setup:
- Develop new features on `master` (1.21.10)
- Cherry-pick/merge to `1.21.1` branch
- Conflicts limited to: `gradle.properties`, `RenderBuffer.java`, `WorldRendererMixin.java`, `ArchitectsToolboxClient.java`, `RadialMenuScreen.java`, and mod descriptors
- New renderers using `RenderBuffer` merge with zero conflicts
