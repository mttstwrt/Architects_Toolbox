package com.ArchitectsToolbox.measure;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * How measurements are shown (hollow, a single layer, the block count, the next-point preview) and which world
 * overlays are on (chunk boundaries, biome borders, structure bounds). Views are not edits, so undo leaves them
 * alone, and none is saved. Client thread only.
 */
public final class ViewSettings {
    private final Supplier<Measurement> active;
    // Undo snapshots keep identities alive; views can disappear once neither the store nor history refers to them.
    private final Map<UUID, MeasurementView> measurements = new WeakHashMap<>();

    private boolean preview = true;
    private boolean chunks;
    private boolean biomes;
    private boolean structures;
    /** Structure bounds also show each piece's box (F42). */
    private boolean structurePieces;
    private int version;


    /** Session-local view options for one measurement, deliberately outside edit history. */
    public static final class MeasurementView {
        private boolean hollow;
        private boolean layer;
        private boolean count;
        private int layerY;

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
    }

    public ViewSettings(Supplier<Measurement> active) {
        this.active = active;
    }

    public MeasurementView forMeasurement(Measurement measurement) {
        return measurements.computeIfAbsent(measurement.id(), id -> new MeasurementView());
    }

    private MeasurementView current() {
        return forMeasurement(active.get());
    }

    public boolean hollow() {
        return current().hollow;
    }

    public boolean layer() {
        return current().layer;
    }

    public boolean count() {
        return current().count;
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
        return current().layerY;
    }

    public int version() {
        return version;
    }

    public void toggleHollow() {
        current().hollow = !current().hollow;
        version++;
    }

    /** Turns the layer view on at {@code startY}, or off. */
    public void toggleLayer(int startY) {
        current().layer = !current().layer;
        current().layerY = startY;
        version++;
    }

    public void toggleCount() {
        current().count = !current().count;
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
        if (y != current().layerY) {
            current().layerY = y;
            version++;
        }
    }
}
