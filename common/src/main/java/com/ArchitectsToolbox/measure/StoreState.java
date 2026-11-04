package com.ArchitectsToolbox.measure;

import com.ArchitectsToolbox.clipboard.Placement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Everything undo puts back: the measurements, which one is active, and the placements locked as ghost guides.
 * Exactly one measurement until ATB-016 lets there be several. Immutable.
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

    public StoreState withPlacements(List<Placement> next) {
        return new StoreState(measurements, active, next);
    }
}
