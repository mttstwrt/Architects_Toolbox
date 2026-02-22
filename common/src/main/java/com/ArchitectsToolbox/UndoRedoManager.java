package com.ArchitectsToolbox;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UndoRedoManager {
    private static final UndoRedoManager INSTANCE = new UndoRedoManager();

    private static final int MAX_HISTORY_SIZE = 50;

    private final List<SelectionState> undoStack = new ArrayList<>();
    private final List<SelectionState> redoStack = new ArrayList<>();

    private UndoRedoManager() {}

    public static UndoRedoManager getInstance() {
        return INSTANCE;
    }

    public void saveState() {
        SelectionManager selection = SelectionManager.getInstance();
        List<BlockPos> currentBlocks = new ArrayList<>(selection.getSelectedBlocks());
        SelectionState state = new SelectionState(currentBlocks);

        undoStack.add(state);
        if (undoStack.size() > MAX_HISTORY_SIZE) {
            undoStack.remove(0);
        }

        redoStack.clear();
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }

    public void undo() {
        if (!canUndo()) return;

        SelectionManager selection = SelectionManager.getInstance();

        List<BlockPos> currentBlocks = new ArrayList<>(selection.getSelectedBlocks());
        redoStack.add(new SelectionState(currentBlocks));
        if (redoStack.size() > MAX_HISTORY_SIZE) {
            redoStack.remove(0);
        }

        SelectionState previousState = undoStack.remove(undoStack.size() - 1);
        selection.setSelectedBlocks(previousState.getBlocks());
    }

    public void redo() {
        if (!canRedo()) return;

        SelectionManager selection = SelectionManager.getInstance();

        List<BlockPos> currentBlocks = new ArrayList<>(selection.getSelectedBlocks());
        undoStack.add(new SelectionState(currentBlocks));
        if (undoStack.size() > MAX_HISTORY_SIZE) {
            undoStack.remove(0);
        }

        SelectionState redoState = redoStack.remove(redoStack.size() - 1);
        selection.setSelectedBlocks(redoState.getBlocks());
    }

    public void clearHistory() {
        undoStack.clear();
        redoStack.clear();
    }

    public int getUndoStackSize() { return undoStack.size(); }
    public int getRedoStackSize() { return redoStack.size(); }

    private static class SelectionState {
        private final List<BlockPos> blocks;

        public SelectionState(List<BlockPos> blocks) {
            this.blocks = new ArrayList<>(blocks);
        }

        public List<BlockPos> getBlocks() {
            return new ArrayList<>(blocks);
        }
    }

    private boolean compoundActive = false;
    private List<BlockPos> compoundStartState = null;

    public void beginCompound() {
        if (!compoundActive) {
            compoundActive = true;
            SelectionManager selection = SelectionManager.getInstance();
            compoundStartState = new ArrayList<>(selection.getSelectedBlocks());
        }
    }

    public void endCompound() {
        if (compoundActive) {
            compoundActive = false;
            if (compoundStartState != null) {
                SelectionManager selection = SelectionManager.getInstance();
                List<BlockPos> currentBlocks = new ArrayList<>(selection.getSelectedBlocks());

                if (!compoundStartState.equals(currentBlocks)) {
                    undoStack.add(new SelectionState(compoundStartState));
                    if (undoStack.size() > MAX_HISTORY_SIZE) {
                        undoStack.remove(0);
                    }
                    redoStack.clear();
                }
            }
            compoundStartState = null;
        }
    }

    public boolean isCompoundActive() { return compoundActive; }

    public void saveStateInternal() {
        if (compoundActive) return;
        saveState();
    }

    public void reset() {
        clearHistory();
        compoundActive = false;
        compoundStartState = null;
    }

    public static class LockedPlacementData {
        public final BlockPos anchorPos;
        public final Map<BlockPos, BlockState> blocks;

        public LockedPlacementData(BlockPos anchorPos, Map<BlockPos, BlockState> blocks) {
            this.anchorPos = anchorPos;
            this.blocks = new HashMap<>(blocks);
        }
    }
}
