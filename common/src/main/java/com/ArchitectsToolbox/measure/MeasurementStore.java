package com.ArchitectsToolbox.measure;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.UnaryOperator;

/**
 * The client's measurements and their undo history. Every edit replaces the whole {@link StoreState} and keeps the
 * one before it, so undo covers every kind of edit alike. Client thread only.
 */
public final class MeasurementStore {
    private static final int HISTORY = 50;

    private StoreState state = StoreState.INITIAL;
    private final Deque<StoreState> undo = new ArrayDeque<>();
    private final Deque<StoreState> redo = new ArrayDeque<>();
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
        undo.push(state);
        if (undo.size() > HISTORY) {
            undo.removeLast();
        }
        redo.clear();
        set(next);
        return true;
    }

    public boolean editActive(UnaryOperator<Measurement> edit) {
        return edit(current -> current.withActive(edit));
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
        redo.push(state);
        set(undo.pop());
        return true;
    }

    public boolean redo() {
        if (redo.isEmpty()) {
            return false;
        }
        undo.push(state);
        set(redo.pop());
        return true;
    }

    private void set(StoreState next) {
        state = next;
        version++;
    }
}
