# GUI Components

## RadialMenuScreen

Full-screen radial menu opened by holding the measure key (G) for 200ms. Renders as a circle with pie-slice segments for each action.

**Behavior**: Mouse position selects segment. Scroll wheel on a selected segment triggers `action.onScroll()`. Release key executes selected action and closes menu.

**Rendering**: Custom `renderWidget` draws colored segments with vertex building, action names as labels.

## RadialMenuAction (interface)

```java
public interface RadialMenuAction {
    Component getName();
    void execute();
    boolean isEnabled();
    default int getColor() { return 0xFFFFFF; }
    default boolean onScroll(double amount) { return false; }
}
```

## RadialMenuRegistry

Static registry populated in `initialize()`. Registers all available actions:

| Action | Function | Scroll |
|--------|----------|--------|
| Rectangle / Cylinder / Ellipsoid / Spline / Line | Set shape mode | - |
| Clear | Clear selection | - |
| Copy | Copy selection to clipboard | - |
| Paste | Activate paste preview | - |
| Undo / Redo | Undo/redo selection | - |
| Hollow | Toggle hollow mode | - |
| Layers | Toggle layer view | - |
| Chunks | Toggle chunk boundaries | - |
| Count | Toggle block counting HUD | - |
| Subdivisions | Cycle subdivision count | Step up/down |
| Radius (cylinder) | - | Adjust radius offset |
| Tube (spline/line) | - | Adjust tube radius |
| Mirror X / Mirror Z | Mirror clipboard | - |
| Stack | Stack locked placements | - |
| Remove Last | Remove last locked placement | - |
| Clear Ghosts | Clear all locked placements | - |

## ConfigScreen

Settings screen accessible via ModMenu (Fabric) or future NeoForge config integration. Extends `Screen`.

**Options**:
- Ellipsoid mode toggle: FIT_TO_BOX / CENTERED
- Ghost block render mode: WIREFRAME / SOLID
- Ghost block opacity slider: 0.0-1.0

## BlockCountOverlay

HUD overlay rendered via `ClientGuiEvent.RENDER_HUD`. Shows block counts per material when `SelectionManager.isBlockCountingEnabled()` is true.

Rendered in top-left corner with block icons and count numbers. Only visible when there's an active selection with counting enabled.
