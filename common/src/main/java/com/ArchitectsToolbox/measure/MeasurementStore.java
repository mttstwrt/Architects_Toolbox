package com.ArchitectsToolbox.measure;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

/**
 * The client's measurements and placements, and their undo history. Every edit replaces the whole
 * {@link StoreState} and keeps the one before it, so undo covers every kind of edit alike. Blocks placed in the world
 * join the same history as a {@link WorldChange}, which undo and redo write back. Client thread only.
 */
public final class MeasurementStore {
    private static final int HISTORY = 50;

    /** Blocks written to the world, which can be written back. */
    public interface WorldChange {
        void revert();

        void reapply();
    }

    /** A state to return to, and the world change made on leaving it, if any. */
    private record Step(StoreState state, @Nullable WorldChange change) {}

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
        push(new Step(state, null));
        set(next);
        return true;
    }

    public boolean editActive(UnaryOperator<Measurement> edit) {
        return edit(current -> current.withActive(edit));
    }

    /** Records blocks already written to the world, so undo can write them back. */
    public void record(WorldChange change) {
        push(new Step(state, change));
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
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public boolean undo() {
        if (undo.isEmpty()) {
            return false;
        }
        Step step = undo.pop();
        redo.push(new Step(state, step.change()));
        if (step.change() != null) {
            step.change().revert();
        }
        set(step.state());
        return true;
    }

    public boolean redo() {
        if (redo.isEmpty()) {
            return false;
        }
        Step step = redo.pop();
        undo.push(new Step(state, step.change()));
        if (step.change() != null) {
            step.change().reapply();
        }
        set(step.state());
        return true;
    }

    private void set(StoreState next) {
        state = next;
        version++;
    }
}
