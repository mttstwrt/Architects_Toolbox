package com.ArchitectsToolbox.render;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.Direction;

/**
 * Collects the unit edges of block faces, each once however many faces share it, and turns them into as few lines
 * as possible by joining edges that continue each other. Used for the hollow and layer views. Coordinates are kept
 * relative to an origin near the blocks, which keeps them within the packed key's range. Not thread-safe.
 */
final class BlockEdges {
    private final int originX;
    private final int originY;
    private final int originZ;
    /** Edge keys per axis the edge runs along; see {@link #key}. */
    private final LongOpenHashSet[] edges = {new LongOpenHashSet(), new LongOpenHashSet(), new LongOpenHashSet()};

    BlockEdges(int originX, int originY, int originZ) {
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
    }

    /** Adds the four edges of a block's face. */
    void addFace(int worldX, int worldY, int worldZ, Direction face) {
        int x = worldX - originX;
        int y = worldY - originY;
        int z = worldZ - originZ;
        int fx = face == Direction.EAST ? x + 1 : x;
        int fy = face == Direction.UP ? y + 1 : y;
        int fz = face == Direction.SOUTH ? z + 1 : z;
        switch (face.getAxis()) {
            case X -> {
                addEdge(Direction.Axis.Y, fx, fy, fz);
                addEdge(Direction.Axis.Y, fx, fy, fz + 1);
                addEdge(Direction.Axis.Z, fx, fy, fz);
                addEdge(Direction.Axis.Z, fx, fy + 1, fz);
            }
            case Y -> {
                addEdge(Direction.Axis.X, fx, fy, fz);
                addEdge(Direction.Axis.X, fx, fy, fz + 1);
                addEdge(Direction.Axis.Z, fx, fy, fz);
                addEdge(Direction.Axis.Z, fx + 1, fy, fz);
            }
            case Z -> {
                addEdge(Direction.Axis.X, fx, fy, fz);
                addEdge(Direction.Axis.X, fx, fy + 1, fz);
                addEdge(Direction.Axis.Y, fx, fy, fz);
                addEdge(Direction.Axis.Y, fx + 1, fy, fz);
            }
        }
    }

    /** The unit edge from corner (x, y, z) one block along {@code along}. */
    private void addEdge(Direction.Axis along, int x, int y, int z) {
        edges[along.ordinal()].add(switch (along) {
            case X -> key(y, z, x);
            case Y -> key(x, z, y);
            case Z -> key(x, y, z);
        });
    }

    /**
     * Packs an edge so that sorting puts the edges of one line next to each other: the two fixed coordinates first,
     * the coordinate along the line last. 21 bits each, offset so negative coordinates sort below positive ones.
     */
    private static long key(int fixedA, int fixedB, int along) {
        return ((long) (fixedA + OFFSET) << 42) | ((long) (fixedB + OFFSET) << 21) | (along + OFFSET);
    }

    private static final int OFFSET = 1 << 20;
    private static final long MASK = (1L << 21) - 1;

    /** Adds the joined lines to the mesh. */
    void addTo(LineMesh.Builder mesh, int argb) {
        for (Direction.Axis along : Direction.Axis.values()) {
            LongArrayList sorted = new LongArrayList(edges[along.ordinal()]);
            sorted.sort(null);
            int i = 0;
            while (i < sorted.size()) {
                long start = sorted.getLong(i);
                int j = i;
                while (j + 1 < sorted.size() && sorted.getLong(j + 1) == sorted.getLong(j) + 1) {
                    j++;
                }
                int a = (int) ((start >>> 42) & MASK) - OFFSET;
                int b = (int) ((start >>> 21) & MASK) - OFFSET;
                int from = (int) (start & MASK) - OFFSET;
                int to = from + (j - i) + 1;
                switch (along) {
                    case X -> mesh.line(originX + from, originY + a, originZ + b, originX + to, originY + a, originZ + b, argb);
                    case Y -> mesh.line(originX + a, originY + from, originZ + b, originX + a, originY + to, originZ + b, argb);
                    case Z -> mesh.line(originX + a, originY + b, originZ + from, originX + a, originY + b, originZ + to, argb);
                }
                i = j + 1;
            }
        }
    }
}
