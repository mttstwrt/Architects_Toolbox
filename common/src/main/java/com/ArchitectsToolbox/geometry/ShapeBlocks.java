package com.ArchitectsToolbox.geometry;

import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.core.BlockPos;

/** Walks the blocks of a shape, and says what that costs before anyone starts. */
public final class ShapeBlocks {
    private ShapeBlocks() {
    }

    @FunctionalInterface
    public interface Visitor {
        /** Returns false to stop the walk. */
        boolean visit(int x, int y, int z);
    }

    /** About how many blocks a walk of the shape tests. */
    public static long scanCost(Shape shape) {
        return shape instanceof PathShape path ? path.scanCost() : shape.bounds().volume();
    }

    /** Visits every block of the filled shape; returns false if the visitor stopped it. */
    public static boolean forEach(Shape shape, Visitor visitor) {
        if (shape instanceof PathShape path) {
            for (LongIterator it = path.blocks().iterator(); it.hasNext(); ) {
                long pos = it.nextLong();
                if (!visitor.visit(BlockPos.getX(pos), BlockPos.getY(pos), BlockPos.getZ(pos))) {
                    return false;
                }
            }
            return true;
        }
        BlockBox box = shape.bounds();
        for (int y = box.minY(); y <= box.maxY(); y++) {
            if (!forEachInLayer(shape, y, visitor)) {
                return false;
            }
        }
        return true;
    }

    /** Visits the blocks of the filled shape at one Y. */
    public static boolean forEachInLayer(Shape shape, int y, Visitor visitor) {
        BlockBox box = shape.bounds();
        if (y < box.minY() || y > box.maxY()) {
            return true;
        }
        if (shape instanceof PathShape path) {
            for (LongIterator it = path.blocks().iterator(); it.hasNext(); ) {
                long pos = it.nextLong();
                if (BlockPos.getY(pos) == y && !visitor.visit(BlockPos.getX(pos), y, BlockPos.getZ(pos))) {
                    return false;
                }
            }
            return true;
        }
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                if (shape.contains(x, y, z) && !visitor.visit(x, y, z)) {
                    return false;
                }
            }
        }
        return true;
    }
}
