package com.ArchitectsToolbox.measure;

import com.ArchitectsToolbox.clipboard.Placement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Everything undo puts back: the measurements, which one is active, and the placements locked as ghost guides.
 * At least one measurement exists, including an empty active one when the store is cleared. Immutable.
 */
public record StoreState(List<Measurement> measurements, int active, List<Placement> placements) {
    public static final StoreState INITIAL = new StoreState(List.of(Measurement.EMPTY), 0, List.of());

    public StoreState {
        measurements = List.copyOf(measurements);
        placements = List.copyOf(placements);
        if (active < 0 || active >= measurements.size()) {
            throw new IllegalArgumentException("no measurement " + active);
        }
    }

    public Measurement activeMeasurement() {
        return measurements.get(active);
    }

    public StoreState withActive(UnaryOperator<Measurement> edit) {
        List<Measurement> next = new ArrayList<>(measurements);
        next.set(active, edit.apply(measurements.get(active)));
        return new StoreState(next, active, placements);
    }

    /** Starts a fresh measurement, reusing an empty active slot rather than accumulating empty entries. */
    public StoreState start(int colour) {
        if (activeMeasurement().isEmpty()) {
            return withActive(m -> m.withColour(colour));
        }
        List<Measurement> next = new ArrayList<>(measurements);
        next.add(Measurement.empty(colour));
        return new StoreState(next, next.size() - 1, placements);
    }

    /** Clear removes the active entry; the next remaining entry, or the last one, becomes active. */
    public StoreState removeActive() {
        if (measurements.size() == 1) {
            return activeMeasurement().isEmpty() ? this : clearMeasurements();
        }
        List<Measurement> next = new ArrayList<>(measurements);
        next.remove(active);
        return new StoreState(next, Math.min(active, next.size() - 1), placements);
    }

    public StoreState clearMeasurements() {
        if (measurements.size() == 1 && activeMeasurement().isEmpty()) {
            return this;
        }
        return new StoreState(List.of(Measurement.empty(activeMeasurement().colour())), 0, placements);
    }

    public StoreState select(int index) {
        return new StoreState(measurements, index, placements);
    }

    public StoreState withPlacements(List<Placement> next) {
        return new StoreState(measurements, active, next);
    }
}
