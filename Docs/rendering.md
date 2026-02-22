# Rendering

## Pipeline

```
WorldRendererMixin (@TAIL of LevelRenderer.renderLevel)
  -> MeasurementRenderer.render(camera, viewMatrix)
       -> if hollowMode: HollowShapeRenderer.render()
       -> else: ShapeRenderer.render() + renderLabels()
       -> if subdivisions > 0: SubdivisionRenderer.render()
       -> if chunkBoundaries: ChunkBoundaryRenderer.render()
       -> GhostBlockRenderer.render() (always, for paste previews)
```

## MeasurementRenderer (coordinator)

Singleton. Holds a map of `ShapeMode -> ShapeRenderer` and specialized renderers:
- `SubdivisionRenderer` - grid lines within bounding box
- `ChunkBoundaryRenderer` - chunk boundary visualization
- `HollowShapeRenderer` - individual block outlines (cached)
- `GhostBlockRenderer` - paste preview and locked placements (cached)

Cache invalidation methods: `invalidateHollowShapeCache()`, `invalidateGhostBlockCaches()`.

## ShapeRenderer Interface

```java
public interface ShapeRenderer {
    void render(Camera camera, Matrix4f viewMatrix, List<BlockPos> selectedBlocks, RenderConfig config);
    default void renderLabels(Camera camera, Matrix4f viewMatrix, List<BlockPos> selectedBlocks, RenderConfig config) {}

    record RenderConfig(float r, float g, float b, float a, int subdivisions, boolean showLabels) {}
}
```

## Shape Renderers

| Renderer | Shape | Key Features |
|----------|-------|-------------|
| `RectangleRenderer` | Bounding box | Wireframe + X/Y/Z dimension labels |
| `CylinderRenderer` | Cylinder | Center+radius wireframe, supports radius offset (0.5-block increments) |
| `EllipsoidRenderer` | Ellipsoid | Latitude rings, FIT_TO_BOX or CENTERED mode |
| `SplineRenderer` | Catmull-Rom spline | Smooth curve through points, optional tube radius |
| `LineRenderer` | Line segments | Distance + angle labels (L/R turn, up/dn pitch), optional tube |

## RenderUtils (static utilities)

- `drawLine(VertexConsumer, Matrix4f, coords, RGBA)` - single line segment with normals
- `drawBox(VertexConsumer, Matrix4f, minXYZ, maxXYZ, RGBA)` - 12-edge wireframe box
- `renderFloatingText(Camera, Matrix4f, text, Vec3, [RGB])` - billboard text with background
- `getBlockCount(dx, dy, dz)` - volume calculation

Uses a shared `ByteBufferBuilder LABEL_BUFFER` for text rendering.

## Caching Strategy

| Component | Cache Type | Invalidation |
|-----------|-----------|-------------|
| `SelectionManager` | Min/max bounds | On selection change (`addBlock`, `clearSelection`, `setSelectedBlocks`) |
| `BlockCounter` | Block counts | Time-based (100ms TTL) |
| `HollowShapeRenderer` | Hollow block set + vertex buffer | On selection change (via `invalidateHollowShapeCache()`) |
| `GhostBlockRenderer` | Vertex data for paste previews | On rotation/placement change (via `invalidateGhostBlockCaches()`) |

## HollowBlockCalculator

Computes surface blocks for each shape mode. Safety limits:
- `MAX_HOLLOW_BLOCKS` (50,000) - maximum rendered blocks
- `MAX_VOLUME` (10M) - skip calculation if exceeded
- Provides `wasLastCalculationLimited()` / `getLastLimitReason()` for status

## Buffer Pattern

Renderers use `ByteBufferBuilder` for vertex data, consumed via `RenderType.LINES` draw calls:
```java
private static final ByteBufferBuilder BUFFER = new ByteBufferBuilder(bufferSize);
```
