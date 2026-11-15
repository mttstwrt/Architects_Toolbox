package com.ArchitectsToolbox.client;

import com.ArchitectsToolbox.clipboard.Clipboard;
import com.ArchitectsToolbox.clipboard.Placement;
import com.ArchitectsToolbox.geometry.BlockBox;
import com.ArchitectsToolbox.geometry.Shape;
import com.ArchitectsToolbox.geometry.ShapeBlocks;
import com.ArchitectsToolbox.measure.Measurement;
import com.ArchitectsToolbox.measure.MeasurementStore;
import com.ArchitectsToolbox.measure.ShapeKind;
import com.ArchitectsToolbox.measure.ViewSettings;
import com.ArchitectsToolbox.render.MeasurementScene;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Every change a player can make to measurements, placements and views. Keys, the wheel and commands all go through
 * these, so they behave alike. Each returns whether anything changed. Also holds the clipboard and the paste preview,
 * which are not part of undo. Client thread.
 */
public final class Operations {
    private final MeasurementStore store;
    private final ViewSettings views;
    private final WorldPlacer placer;
    private final Supplier<ToolboxConfig> config;
    private @Nullable Clipboard clipboard;
    private boolean pasting;
    /** Quarter turns clockwise of the paste preview; back to 0 for each paste (F31). */
    private int rotation;
    /** Where the preview's lowest corner is, following the crosshair; null while it points at nothing. */
    private @Nullable BlockPos pasteOrigin;
    private int layerBoundsVersion = -1;
    private @Nullable BlockBox cachedLayerBounds;

    Operations(MeasurementStore store, ViewSettings views, WorldPlacer placer, Supplier<ToolboxConfig> config) {
        this.store = store;
        this.views = views;
        this.placer = placer;
        this.config = config;
    }

    public MeasurementStore store() {
        return store;
    }

    public ViewSettings views() {
        return views;
    }

    public Measurement active() {
        return store.active();
    }

    public List<Placement> placements() {
        return store.state().placements();
    }

    public boolean addPoint(BlockPos pos) {
        return store.editActive(m -> m.withPoint(pos));
    }

    /**
     * Sets point 1 or 2 ({@code index} 0 or 1), as {@code /atb pos1} and {@code pos2} do: the point is replaced, or
     * added if the list is that short. Point 2 needs a point 1.
     */
    public boolean setPoint(int index, BlockPos pos) {
        List<BlockPos> points = new ArrayList<>(active().points());
        if (index > points.size()) {
            return false;
        }
        if (index == points.size()) {
            points.add(pos);
        } else {
            points.set(index, pos);
        }
        return store.editActive(m -> m.withPoints(points));
    }

    /** Removes the active measurement. Placements have their own clear. */
    public boolean clear() {
        return store.edit(state -> state.removeActive());
    }

    public boolean clearAll() {
        return store.edit(state -> state.clearMeasurements());
    }

    public List<Integer> palette() {
        return config.get().palette();
    }

    public int measurementLimit() {
        return config.get().measurementLimit();
    }

    public boolean canStartMeasurement() {
        return active().isEmpty() || store.state().measurements().size() < config.get().measurementLimit();
    }

    /** Starts a measurement in the current colour; an empty active slot is reused. */
    public boolean newMeasurement() {
        return pickColour(active().colour());
    }

    /** Colour is chosen before measuring: a measurement with points is kept and a new one is started. */
    public boolean pickColour(int colour) {
        if (!canStartMeasurement()) {
            ClientCompat.actionBar(Minecraft.getInstance(), Component.translatable("architects_toolbox.message.measurement_limit",
                    config.get().measurementLimit()));
            return false;
        }
        boolean changed = store.edit(state -> state.start(colour));
        if (changed) {
            ClientCompat.actionBar(Minecraft.getInstance(), Component.translatable("architects_toolbox.message.active_measurement",
                    store.state().active() + 1, MeasurementColours.name(colour)).withStyle(style -> style.withColor(colour & 0xFFFFFF)));
        }
        return changed;
    }

    public boolean nextColour() {
        List<Integer> colours = palette();
        int next = (colours.indexOf(active().colour()) + 1) % colours.size();
        return pickColour(colours.get(next));
    }

    /** Selection is navigation, so it does not consume an undo step. */
    public boolean stepMeasurement(int step) {
        int size = store.state().measurements().size();
        return store.select(Math.floorMod(store.state().active() + step, size));
    }

    public boolean setShape(ShapeKind shape) {
        return store.editActive(m -> m.withShape(shape));
    }

    /** The Shift+scroll setting of a shape: subdivisions, cylinder form, ellipsoid mode or tube radius. */
    public boolean stepShapeOption(ShapeKind shape, int step) {
        return store.editActive(m -> m.withOptions(switch (shape) {
            case RECTANGLE -> m.options().stepSubdivisions(step);
            case CYLINDER -> m.options().stepCylinder(step);
            case ELLIPSOID -> m.options().toggleEllipsoid();
            case LINE, SPLINE -> m.options().stepTube(shape, step);
        }));
    }

    public boolean undo() {
        return store.undo();
    }

    public boolean redo() {
        return store.redo();
    }

    public boolean hasClipboard() {
        return clipboard != null;
    }

    /** Copies the blocks inside the active measurement's shape from the client's world (F30). */
    public boolean copy() {
        Minecraft minecraft = Minecraft.getInstance();
        if (active().isEmpty() || minecraft.level == null) {
            return false;
        }
        Shape shape = active().geometry();
        Clipboard copied = ShapeBlocks.scanCost(shape) > MeasurementScene.SCAN_LIMIT ? null : Clipboard.copy(minecraft.level, shape);
        if (copied == null) {
            ClientCompat.actionBar(minecraft, Component.translatable("architects_toolbox.message.copy_too_large", Clipboard.MAX_BLOCKS));
            return false;
        }
        if (copied.isEmpty()) {
            ClientCompat.actionBar(minecraft, Component.translatable("architects_toolbox.message.copy_empty"));
            return false;
        }
        clipboard = copied;
        ClientCompat.actionBar(minecraft, Component.translatable("architects_toolbox.message.copied", copied.blockCount()));
        return true;
    }

    public boolean pasting() {
        return pasting;
    }

    /** Opens the paste preview, unturned, or closes it. */
    public boolean togglePaste() {
        if (!pasting && clipboard == null) {
            return false;
        }
        pasting = !pasting;
        rotation = 0;
        pasteOrigin = null;
        return true;
    }

    /** Turns the preview a quarter turn clockwise ({@code step} 1) or anticlockwise (-1). */
    public boolean rotatePaste(int step) {
        if (!pasting) {
            return false;
        }
        rotation = Math.floorMod(rotation + step, 4);
        return true;
    }

    public int rotationDegrees() {
        return rotation * 90;
    }

    void setPasteOrigin(@Nullable BlockPos origin) {
        pasteOrigin = origin;
    }

    /** The preview where it is now, or null if it is closed or points at nothing. */
    public @Nullable Placement pastePreview() {
        return pasting && clipboard != null && pasteOrigin != null ? new Placement(clipboard.rotated(rotation), pasteOrigin) : null;
    }

    /**
     * Confirms the preview and closes it: in creative the blocks are placed in the world where the player may place
     * them (F38), otherwise the preview is locked as a ghost guide (F32). Both are undoable.
     */
    public boolean confirmPaste() {
        Placement placement = pastePreview();
        if (placement == null) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        togglePaste();
        if (minecraft.player != null && minecraft.player.isCreative()) {
            if (WorldPlacer.route(minecraft) != WorldPlacer.Route.NONE) {
                WorldPlacer.Change change = placer.place(minecraft, placement, config.get().placeReplaces());
                if (change == null) {
                    ClientCompat.actionBar(minecraft, Component.translatable("architects_toolbox.message.nothing_placed"));
                    return false;
                }
                store.record(change);
                ClientCompat.actionBar(minecraft, Component.translatable("architects_toolbox.message.placed", change.size()));
                return true;
            }
            ClientCompat.actionBar(minecraft, Component.translatable("architects_toolbox.message.no_permission"));
        } else {
            ClientCompat.actionBar(minecraft, Component.translatable("architects_toolbox.message.ghosts_placed"));
        }
        List<Placement> next = new ArrayList<>(placements());
        next.add(placement);
        return store.edit(state -> state.withPlacements(next));
    }

    /** Removes every locked placement (F33). */
    public boolean clearPlacements() {
        return store.edit(state -> state.withPlacements(List.of()));
    }

    public void toggleHollow() {
        views.toggleHollow();
    }

    public void toggleCount() {
        views.toggleCount();
    }

    public void togglePreview() {
        views.togglePreview();
    }

    public void toggleChunks() {
        views.toggleChunks();
    }

    public void toggleBiomes() {
        views.toggleBiomes();
    }

    public void toggleStructures() {
        views.toggleStructures();
    }

    /** Shows or hides the boxes of structure pieces (F42). */
    public void toggleStructurePieces() {
        views.toggleStructurePieces();
    }

    /** Structures are only known in singleplayer, where the client can read the integrated server's world. */
    public boolean canShowStructures() {
        return Minecraft.getInstance().getSingleplayerServer() != null;
    }

    /** What the layer view slices: the active measurement and the locked placements, or null if there are none. */
    public @Nullable BlockBox layerBounds() {
        if (layerBoundsVersion == store.version()) {
            return cachedLayerBounds;
        }
        BlockBox bounds = active().isEmpty() ? null : active().geometry().bounds();
        for (Placement placement : placements()) {
            bounds = bounds == null ? placement.bounds() : bounds.union(placement.bounds());
        }
        cachedLayerBounds = bounds;
        layerBoundsVersion = store.version();
        return bounds;
    }

    /** The layer view needs something to slice. */
    public boolean canShowLayer() {
        return layerBounds() != null;
    }

    /** Turns the layer view on at the middle of what it slices, or off. */
    public boolean toggleLayer() {
        BlockBox bounds = layerBounds();
        if (!views.layer() && bounds == null) {
            return false;
        }
        views.toggleLayer(bounds != null ? (bounds.minY() + bounds.maxY()) / 2 : 0);
        return true;
    }

    /** Moves the layer up or down, wrapping round at the top and bottom. */
    public boolean stepLayer(int step) {
        BlockBox bounds = layerBounds();
        if (!views.layer() || bounds == null) {
            return false;
        }
        views.setLayerY(bounds.minY() + Math.floorMod(views.layerY() - bounds.minY() + step, bounds.sizeY()));
        return true;
    }

    /** Keeps the layer inside what it slices after points, shape or placements changed. */
    void keepLayerInside() {
        if (!views.layer()) {
            return;
        }
        BlockBox bounds = layerBounds();
        if (views.layer() && bounds != null) {
            views.setLayerY(Math.clamp(views.layerY(), bounds.minY(), bounds.maxY()));
        }
    }
}
