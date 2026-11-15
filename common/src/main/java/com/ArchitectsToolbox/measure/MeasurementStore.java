package com.ArchitectsToolbox.measure;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

/**
 * The client's measurements, placements and undo history. Ordinary measurement edits follow the active ID;
 * creation and clearing follow that identity too, becoming reachable after the entry is removed. Clear all,
 * placements and world writes are global steps. Each applies only its changed IDs and placements, preserving
 * later edits to other measurements. Client thread only.
 */
public final class MeasurementStore {
    private static final int HISTORY = 50;

    /** Blocks written to the world, which can be written back. */
    public interface WorldChange {
        void revert();

        void reapply();
    }

    /** Null scope means clear all, a placement or a world edit, available from any active measurement. */
    private record Step(StoreState before, StoreState after, Set<UUID> changed,
                        @Nullable UUID scope, @Nullable WorldChange world) {
        boolean matches(StoreState current) {
            if (scope == null || scope.equals(current.activeMeasurement().id())) {
                return true;
            }
            // Clearing an entry removes its selection target; undo clear and redo creation must remain reachable.
            // Ordinary edits to a missing measurement stay unavailable until its structural step restores it.
            return !sameIdentities(before, after) && indexOf(current.measurements(), scope) < 0;
        }
    }

    private StoreState state = StoreState.INITIAL;
    private final Deque<Step> undo = new ArrayDeque<>();
    private final Deque<Step> redo = new ArrayDeque<>();
    /** Goes up on every change, so views can tell they are stale. */
    private int version;

    public StoreState state() {
        return state;
    }

    public Measurement active() {
        return state.activeMeasurement();
    }

    public int version() {
        return version;
    }

    /** Applies an edit; returns false, and records nothing, when it changes nothing. */
    public boolean edit(UnaryOperator<StoreState> edit) {
        StoreState next = edit.apply(state);
        if (next.equals(state)) {
            return false;
        }
        Set<UUID> changed = changedMeasurements(state, next);
        UUID scope = null;
        if (state.placements().equals(next.placements())) {
            if (changed.size() == 1 && sameIdentities(state, next)) {
                scope = changed.iterator().next();
            } else {
                Set<UUID> added = new HashSet<>();
                Set<UUID> removed = new HashSet<>();
                for (UUID id : changed) {
                    if (indexOf(state.measurements(), id) < 0) {
                        added.add(id);
                    }
                    if (indexOf(next.measurements(), id) < 0) {
                        removed.add(id);
                    }
                }
                if (added.size() == 1 && removed.isEmpty()) {
                    scope = added.iterator().next();
                } else if (removed.size() == 1 && added.isEmpty()) {
                    scope = removed.iterator().next();
                } else if (state.measurements().size() == 1 && next.measurements().size() == 1) {
                    scope = next.activeMeasurement().id();
                }
            }
        }
        push(new Step(state, next, changed, scope, null));
        set(next);
        return true;
    }

    public boolean editActive(UnaryOperator<Measurement> edit) {
        return edit(current -> current.withActive(edit));
    }

    /** Changes only the active measurement, leaving edit history intact. */
    public boolean select(int index) {
        if (index == state.active()) {
            return false;
        }
        set(state.select(index));
        return true;
    }

    /** Records blocks already written to the world, so undo can write them back. */
    public void record(WorldChange change) {
        push(new Step(state, state, Set.of(), null, change));
        set(state);
    }

    private void push(Step step) {
        undo.push(step);
        if (undo.size() > HISTORY) {
            undo.removeLast();
        }
        redo.clear();
    }

    public boolean canUndo() {
        return find(undo, false) != null;
    }

    public boolean canRedo() {
        return find(redo, false) != null;
    }

    public boolean undo() {
        Step step = find(undo, true);
        if (step == null) {
            return false;
        }
        StoreState previous = state;
        StoreState next = apply(step, step.before());
        if (step.world() != null) {
            step.world().revert();
        }
        // Capture the state actually removed: a structural step can skip newer edits to another measurement.
        // Redo must restore those edits too, rather than the older snapshot from the original creation step.
        redo.push(new Step(next, previous, step.changed(), step.scope(), step.world()));
        set(next);
        return true;
    }

    public boolean redo() {
        Step step = find(redo, true);
        if (step == null) {
            return false;
        }
        StoreState previous = state;
        StoreState next = apply(step, step.after());
        if (step.world() != null) {
            step.world().reapply();
        }
        undo.push(new Step(previous, next, step.changed(), step.scope(), step.world()));
        if (undo.size() > HISTORY) {
            undo.removeLast();
        }
        set(next);
        return true;
    }

    /** At most 50 cold-path history entries; unmatched measurements retain their order and history. */
    private @Nullable Step find(Deque<Step> history, boolean remove) {
        Iterator<Step> iterator = history.iterator();
        while (iterator.hasNext()) {
            Step step = iterator.next();
            if (step.matches(state)) {
                if (remove) {
                    iterator.remove();
                }
                return step;
            }
        }
        return null;
    }

    private StoreState apply(Step step, StoreState target) {
        List<Measurement> next = new ArrayList<>(state.measurements());
        // Existing entries stay in place: an older snapshot may predate unrelated measurements added between them.
        for (int i = next.size() - 1; i >= 0; i--) {
            Measurement measurement = next.get(i);
            if (!step.changed().contains(measurement.id())) {
                continue;
            }
            int destination = indexOf(target.measurements(), measurement.id());
            if (destination < 0) {
                next.remove(i);
            } else {
                next.set(i, target.measurements().get(destination));
            }
        }
        for (int i = 0; i < target.measurements().size(); i++) {
            Measurement measurement = target.measurements().get(i);
            if (step.changed().contains(measurement.id()) && indexOf(next, measurement.id()) < 0) {
                next.add(insertionIndex(next, target.measurements(), i), measurement);
            }
        }
        UUID selected = state.activeMeasurement().id();
        boolean structural = !sameIdentities(step.before(), step.after());
        if (structural) {
            selected = target.activeMeasurement().id();
        }
        int active = indexOf(next, selected);
        if (active < 0) {
            active = Math.max(0, next.size() - 1);
        }
        boolean placementsChanged = !step.before().placements().equals(step.after().placements());
        return new StoreState(next, active, placementsChanged ? target.placements() : state.placements());
    }

    /** Restored entries anchor to surviving neighbours in their snapshot, without reordering unrelated entries. */
    private static int insertionIndex(List<Measurement> current, List<Measurement> target, int at) {
        for (int i = at - 1; i >= 0; i--) {
            int previous = indexOf(current, target.get(i).id());
            if (previous >= 0) {
                return previous + 1;
            }
        }
        for (int i = at + 1; i < target.size(); i++) {
            int following = indexOf(current, target.get(i).id());
            if (following >= 0) {
                return following;
            }
        }
        return Math.min(at, current.size());
    }

    private static int indexOf(List<Measurement> measurements, UUID id) {
        for (int i = 0; i < measurements.size(); i++) {
            if (measurements.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static Set<UUID> changedMeasurements(StoreState before, StoreState after) {
        Set<UUID> changed = new HashSet<>();
        for (Measurement measurement : before.measurements()) {
            int index = indexOf(after.measurements(), measurement.id());
            if (index < 0 || !measurement.equals(after.measurements().get(index))) {
                changed.add(measurement.id());
            }
        }
        for (Measurement measurement : after.measurements()) {
            if (indexOf(before.measurements(), measurement.id()) < 0) {
                changed.add(measurement.id());
            }
        }
        return Set.copyOf(changed);
    }

    private static boolean sameIdentities(StoreState before, StoreState after) {
        if (before.measurements().size() != after.measurements().size()) {
            return false;
        }
        for (int i = 0; i < before.measurements().size(); i++) {
            if (!before.measurements().get(i).id().equals(after.measurements().get(i).id())) {
                return false;
            }
        }
        return true;
    }

    private void set(StoreState next) {
        state = next;
        version++;
    }
}
