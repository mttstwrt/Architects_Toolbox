package com.ArchitectsToolbox.client;

import com.ArchitectsToolbox.measure.Measurement;
import com.ArchitectsToolbox.measure.ViewSettings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Counts every measurement whose Count view is on. Stable IDs retain unchanged counters across edits to other
 * measurements; all counters share one scan budget, so adding measurements cannot multiply per-tick world reads.
 * Client thread only.
 */
final class MeasurementCounts {
    private static final int PER_TICK = 250_000;

    /** The store's one-based measurement number and its counter, in store order. */
    record Tracked(int number, Measurement measurement, BlockCounter counter) {}

    private final Map<UUID, BlockCounter> counters = new HashMap<>();
    private List<Tracked> tracked = List.of();
    private List<Measurement> measurements = List.of();
    private int viewVersion = -1;
    private int first;

    void track(List<Measurement> next, ViewSettings views) {
        if (next.equals(measurements) && viewVersion == views.version()) {
            return;
        }
        measurements = next;
        viewVersion = views.version();
        List<Tracked> enabled = new ArrayList<>();
        Map<UUID, BlockCounter> retained = new HashMap<>();
        for (int i = 0; i < next.size(); i++) {
            Measurement measurement = next.get(i);
            if (measurement.isEmpty() || !views.forMeasurement(measurement).count()) {
                continue;
            }
            BlockCounter counter = counters.get(measurement.id());
            if (counter == null) {
                counter = new BlockCounter();
            }
            counter.track(measurement);
            retained.put(measurement.id(), counter);
            enabled.add(new Tracked(i + 1, measurement, counter));
        }
        counters.clear();
        counters.putAll(retained);
        tracked = List.copyOf(enabled);
        first = tracked.isEmpty() ? 0 : first % tracked.size();
    }

    void blockChanged(BlockPos pos) {
        for (Tracked entry : tracked) {
            entry.counter().blockChanged(pos);
        }
    }

    void tick(@Nullable Level level) {
        int size = tracked.size();
        int remaining = PER_TICK;
        // Equal initial shares avoid starvation; unused shares go to the later counters. Rotate the first counter
        // each tick so a short count completing early does not permanently favour one measurement.
        for (int i = 0; i < size; i++) {
            BlockCounter counter = tracked.get((first + i) % size).counter();
            int budget = Math.max(1, remaining / (size - i));
            remaining -= counter.tick(level, budget);
        }
        if (size != 0) {
            first = (first + 1) % size;
        }
    }

    BlockCounter.@Nullable Count count(Measurement measurement) {
        BlockCounter counter = counters.get(measurement.id());
        return counter == null ? null : counter.count();
    }

    List<Tracked> tracked() {
        return tracked;
    }

    /** A different world must never reuse counts from the previous world's blocks. */
    void reset() {
        counters.clear();
        tracked = List.of();
        measurements = List.of();
        viewVersion = -1;
        first = 0;
    }
}
