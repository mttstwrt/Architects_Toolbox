package com.ArchitectsToolbox.render;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
//? if <26.1 {
/*import net.minecraft.world.level.EmptyBlockGetter;
*///?}

/**
 * Blocks to draw as ghosts, at positions relative to whatever origin a {@link GhostMesh} gives them. A face covered
 * by a full, opaque neighbour among the same blocks is marked hidden, and a block with every face hidden is left out,
 * so a large paste draws little more than its outside. Immutable and compared by identity: the render layer prepares
 * the models of one set once and keeps them wherever its meshes move.
 */
public final class GhostBlocks {
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int ALL_HIDDEN = (1 << DIRECTIONS.length) - 1;

    private final long[] positions;
    private final BlockState[] states;
    /** Per block, bit {@code Direction.ordinal()} set when that face is hidden. */
    private final byte[] hidden;

    private GhostBlocks(long[] positions, BlockState[] states, byte[] hidden) {
        this.positions = positions;
        this.states = states;
        this.hidden = hidden;
    }

    /** Blocks {@code 0} to {@code count - 1}; positions are packed by {@link BlockPos#asLong}. */
    static GhostBlocks of(int count, IntToLong position, IntToState state) {
        LongOpenHashSet solid = new LongOpenHashSet();
        for (int i = 0; i < count; i++) {
            if (fullAndOpaque(state.apply(i))) {
                solid.add(position.apply(i));
            }
        }
        LongArrayList keptPositions = new LongArrayList();
        List<BlockState> keptStates = new ArrayList<>();
        List<Byte> keptHidden = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long pos = position.apply(i);
            int mask = 0;
            for (Direction face : DIRECTIONS) {
                if (solid.contains(BlockPos.offset(pos, face))) {
                    mask |= 1 << face.ordinal();
                }
            }
            if (mask != ALL_HIDDEN) {
                keptPositions.add(pos);
                keptStates.add(state.apply(i));
                keptHidden.add((byte) mask);
            }
        }
        byte[] hidden = new byte[keptHidden.size()];
        for (int i = 0; i < hidden.length; i++) {
            hidden[i] = keptHidden.get(i);
        }
        return new GhostBlocks(keptPositions.toLongArray(), keptStates.toArray(BlockState[]::new), hidden);
    }

    private static boolean fullAndOpaque(BlockState state) {
        //? if >=26.1 {
        return state.isSolidRender();
        //?} else {
        /*return state.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        *///?}
    }

    public int size() {
        return states.length;
    }

    /** The relative position of block {@code i}, packed by {@link BlockPos#asLong}. */
    public long position(int i) {
        return positions[i];
    }

    public BlockState state(int i) {
        return states[i];
    }

    public boolean faceHidden(int i, Direction face) {
        return (hidden[i] & 1 << face.ordinal()) != 0;
    }

    @FunctionalInterface
    interface IntToLong {
        long apply(int i);
    }

    @FunctionalInterface
    interface IntToState {
        BlockState apply(int i);
    }
}
