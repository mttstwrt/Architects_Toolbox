package com.ArchitectsToolbox.measure;

/**
 * How measurements are shown: hollow, a single layer, the block count. Views are not edits, so undo leaves them
 * alone. Client thread only.
 */
public final class ViewSettings {
    private boolean hollow;
    private boolean layer;
    private boolean count;
    /** The layer shown, as a world Y; kept inside the measurement by whoever shows it. */
    private int layerY;
    private int version;

    public boolean hollow() {
        return hollow;
    }

    public boolean layer() {
        return layer;
    }

    public boolean count() {
        return count;
    }

    public int layerY() {
        return layerY;
    }

    public int version() {
        return version;
    }

    public void toggleHollow() {
        hollow = !hollow;
        version++;
    }

    /** Turns the layer view on at {@code startY}, or off. */
    public void toggleLayer(int startY) {
        layer = !layer;
        layerY = startY;
        version++;
    }

    public void toggleCount() {
        count = !count;
        version++;
    }

    public void setLayerY(int y) {
        if (y != layerY) {
            layerY = y;
            version++;
        }
    }
}
