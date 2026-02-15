# Extending the Mod

## Adding a New Shape Mode

1. **Add enum value** to `shapes/ShapeMode.java`:
   ```java
   MY_SHAPE("my_shape", "shape.architects_toolbox.my_shape")
   ```

2. **Create renderer** implementing `ShapeRenderer`:
   ```java
   public class MyShapeRenderer implements ShapeRenderer {
       @Override
       public void render(Camera camera, Matrix4f viewMatrix, List<BlockPos> selectedBlocks, RenderConfig config) {
           // Use RenderUtils.drawLine() / drawBox() for wireframes
       }

       @Override
       public void renderLabels(Camera camera, Matrix4f viewMatrix, List<BlockPos> selectedBlocks, RenderConfig config) {
           // Use RenderUtils.renderFloatingText() for dimension labels
       }
   }
   ```

3. **Register** in `MeasurementRenderer` constructor:
   ```java
   renderers.put(ShapeMode.MY_SHAPE, new MyShapeRenderer());
   ```

4. **Add block counting** in `BlockCounter.countBlocksInSelection()` with a case for the new mode.

5. **Add hollow calculation** in `HollowBlockCalculator.calculateHollowBlocks()` for hollow mode support.

6. **Add radial menu action** in `RadialMenuRegistry.initialize()`:
   ```java
   register(new RadialMenuAction() {
       @Override public Component getName() { return Component.translatable("shape.architects_toolbox.my_shape"); }
       @Override public void execute() { SelectionManager.getInstance().setShapeMode(ShapeMode.MY_SHAPE); }
       @Override public boolean isEnabled() { return true; }
   });
   ```

7. **Add translation** in `assets/architects_toolbox/lang/en_us.json`.

## Adding a New Radial Menu Action

Add to `RadialMenuRegistry.initialize()`. Implement `RadialMenuAction` interface - only `getName()`, `execute()`, `isEnabled()` are required. Override `onScroll()` for scroll-wheel adjustable values.

## Adding a Config Option

1. Add field + getter/setter to `ModConfig` (setter must call `save()`)
2. Add field to `ModConfig.ConfigData` inner class
3. Load/save in `ModConfig.load()`/`save()`
4. Add UI control in `ConfigScreen`

## Adding Platform-Specific Code

Prefer Architectury abstractions in common module. If truly platform-specific code is needed:

```java
// In common module - check platform at runtime
if (Platform.isFabric()) { ... }
if (Platform.isNeoForge()) { ... }
```

For more complex cases, use Architectury's `@ExpectPlatform` annotation on static methods, with implementations in each platform module.

## File Checklist for New Features

| Change | Files to modify |
|--------|----------------|
| New shape | ShapeMode, new Renderer, MeasurementRenderer, BlockCounter, HollowBlockCalculator, RadialMenuRegistry, en_us.json |
| New menu action | RadialMenuRegistry |
| New config option | ModConfig, ConfigScreen |
| New overlay | BlockCountOverlay (or new class + register in ArchitectsToolboxClient) |
| New mixin | Create mixin class, add to architects_toolbox.mixins.json |
