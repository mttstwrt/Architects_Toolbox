package com.ArchitectsToolbox.geometry;

/**
 * The blocks a measurement covers. Every view of a measurement asks this one test (outline, hollow, layer, count
 * and, from ATB-014, copy), so they cannot disagree. Immutable and safe to use from any thread.
 */
public interface Shape {
    /** A box holding every block of the shape. */
    BlockBox bounds();

    /** Whether the block is part of the filled shape. Only called for blocks inside {@link #bounds()}. */
    boolean contains(int x, int y, int z);

    /** Whether the block is part of the shape and touches, by a face, a block that is not. */
    default boolean isSurface(int x, int y, int z) {
        return contains(x, y, z) && !(inside(x + 1, y, z) && inside(x - 1, y, z) && inside(x, y + 1, z)
                && inside(x, y - 1, z) && inside(x, y, z + 1) && inside(x, y, z - 1));
    }

    /** {@link #contains} that is also safe outside the bounds. */
    default boolean inside(int x, int y, int z) {
        return bounds().contains(x, y, z) && contains(x, y, z);
    }
}
