package com.ArchitectsToolbox.client;

import com.ArchitectsToolbox.geometry.BlockBox;
import com.ArchitectsToolbox.measure.Measurement;
import com.ArchitectsToolbox.measure.MeasurementStore;
import com.ArchitectsToolbox.measure.ShapeKind;
import com.ArchitectsToolbox.measure.ViewSettings;
import com.ArchitectsToolbox.render.MeasurementScene;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * Every change a player can make to measurements and views. Keys, the wheel and commands all go through these, so
 * they behave alike. Each returns whether anything changed. Client thread.
 */
public final class Operations {
    private final MeasurementStore store;
    private final ViewSettings views;

    public Operations(MeasurementStore store, ViewSettings views) {
        this.store = store;
        this.views = views;
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

    /** Removes the active measurement's points; its shape and options stay. */
    public boolean clear() {
        return store.editActive(m -> m.withPoints(List.of()));
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

    public void toggleHollow() {
        views.toggleHollow();
    }

    public void toggleCount() {
        views.toggleCount();
    }

    /** The layer view needs points to slice. */
    public boolean canShowLayer() {
        return !active().isEmpty();
    }

    public boolean toggleLayer() {
        if (!views.layer() && !canShowLayer()) {
            return false;
        }
        views.toggleLayer(canShowLayer() ? MeasurementScene.middleLayer(active()) : 0);
        return true;
    }

    /** Moves the layer up or down, wrapping round at the top and bottom of the measurement. */
    public boolean stepLayer(int step) {
        if (!views.layer() || !canShowLayer()) {
            return false;
        }
        BlockBox box = active().geometry().bounds();
        views.setLayerY(box.minY() + Math.floorMod(views.layerY() - box.minY() + step, box.sizeY()));
        return true;
    }

    /** Keeps the layer inside the measurement after its points or shape changed. */
    void keepLayerInside() {
        if (views.layer() && canShowLayer()) {
            BlockBox box = active().geometry().bounds();
            views.setLayerY(Math.clamp(views.layerY(), box.minY(), box.maxY()));
        }
    }
}
