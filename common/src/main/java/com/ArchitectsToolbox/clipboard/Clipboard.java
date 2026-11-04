package com.ArchitectsToolbox.clipboard;

import com.ArchitectsToolbox.geometry.BlockBox;
import com.ArchitectsToolbox.geometry.Shape;
import com.ArchitectsToolbox.geometry.ShapeBlocks;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Blocks copied from the world (F30): the non-air blocks inside a shape, at positions relative to the lowest corner
 * of its box. Block entity contents are not kept. Immutable, and compared by identity, so a scene can tell it is
 * still showing the same clipboard. Its rotations are made on the client thread and kept.
 */
public final class Clipboard {
    /** More blocks than this and nothing is copied. */
    public static final int MAX_BLOCKS = 1_000_000;

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    /** Relative positions, packed by {@link BlockPos#asLong}. */
    private final long[] positions;
    private final BlockState[] states;
    /** Quarter turns clockwise, made when first asked for; [0] is this. */
    private final Clipboard[] rotations = new Clipboard[4];

    private Clipboard(int sizeX, int sizeY, int sizeZ, long[] positions, BlockState[] states) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.positions = positions;
        this.states = states;
        rotations[0] = this;
    }

    /** Copies the shape's blocks from the level; null if there are more than {@link #MAX_BLOCKS}. */
    public static @Nullable Clipboard copy(BlockGetter level, Shape shape) {
        BlockBox box = shape.bounds();
        LongArrayList positions = new LongArrayList();
        List<BlockState> states = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean complete = ShapeBlocks.forEach(shape, (x, y, z) -> {
            BlockState state = level.getBlockState(pos.set(x, y, z));
            if (state.isAir()) {
                return true;
            }
            positions.add(BlockPos.asLong(x - box.minX(), y - box.minY(), z - box.minZ()));
            states.add(state);
            return states.size() <= MAX_BLOCKS;
        });
        if (!complete) {
            return null;
        }
        return new Clipboard(box.sizeX(), box.sizeY(), box.sizeZ(), positions.toLongArray(), states.toArray(BlockState[]::new));
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public int blockCount() {
        return states.length;
    }

    public boolean isEmpty() {
        return states.length == 0;
    }

    /** The relative position of block {@code i}, packed by {@link BlockPos#asLong}. */
    public long position(int i) {
        return positions[i];
    }

    public BlockState state(int i) {
        return states[i];
    }

    /**
     * This clipboard turned {@code quarters} quarter turns clockwise about Y, block states too, and moved back so its
     * lowest corner stays at the origin. Client thread.
     */
    public Clipboard rotated(int quarters) {
        int turn = Math.floorMod(quarters, 4);
        Clipboard rotated = rotations[turn];
        if (rotated == null) {
            rotated = rotate(turn);
            rotations[turn] = rotated;
        }
        return rotated;
    }

    private Clipboard rotate(int turn) {
        Rotation rotation = switch (turn) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            default -> Rotation.COUNTERCLOCKWISE_90;
        };
        long[] turned = new long[positions.length];
        BlockState[] turnedStates = new BlockState[states.length];
        for (int i = 0; i < positions.length; i++) {
            int x = BlockPos.getX(positions[i]);
            int y = BlockPos.getY(positions[i]);
            int z = BlockPos.getZ(positions[i]);
            turned[i] = switch (turn) {
                case 1 -> BlockPos.asLong(sizeZ - 1 - z, y, x);
                case 2 -> BlockPos.asLong(sizeX - 1 - x, y, sizeZ - 1 - z);
                default -> BlockPos.asLong(z, y, sizeX - 1 - x);
            };
            turnedStates[i] = states[i].rotate(rotation);
        }
        boolean sideways = turn % 2 == 1;
        return new Clipboard(sideways ? sizeZ : sizeX, sizeY, sideways ? sizeX : sizeZ, turned, turnedStates);
    }
}
