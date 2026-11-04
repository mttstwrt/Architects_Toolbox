package com.ArchitectsToolbox.measure;

/**
 * How measurements are shown (hollow, a single layer, the block count, the next-point preview) and which world
 * overlays are on (chunk boundaries, biome borders, structure bounds). Views are not edits, so undo leaves them
 * alone, and none is saved. Client thread only.
 */
public final class ViewSettings {
    private boolean hollow;
    private boolean layer;
    private boolean count;
    private boolean preview = true;
    private boolean chunks;
    private boolean biomes;
    private boolean structures;
    /** Structure bounds also show each piece's box (F42). */
    private boolean structurePieces;
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

    public boolean preview() {
        return preview;
    }

    public boolean chunks() {
        return chunks;
    }

    public boolean biomes() {
        return biomes;
    }

    public boolean structures() {
        return structures;
    }

    public boolean structurePieces() {
        return structurePieces;
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

    public void togglePreview() {
        preview = !preview;
        version++;
    }

    public void toggleChunks() {
        chunks = !chunks;
        version++;
    }

    public void toggleBiomes() {
        biomes = !biomes;
        version++;
    }

    public void toggleStructures() {
        structures = !structures;
        version++;
    }

    public void toggleStructurePieces() {
        structurePieces = !structurePieces;
        version++;
    }

    public void setLayerY(int y) {
        if (y != layerY) {
            layerY = y;
            version++;
        }
    }
}
