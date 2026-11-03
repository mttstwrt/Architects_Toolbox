package com.ArchitectsToolbox.render;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;

/**
 * Line segments in the world, built once when what they show changes and drawn every frame from then on. Ends are
 * floats relative to a block origin, so they keep their precision far from the world's origin. Immutable.
 */
public final class LineMesh {
    private final int originX;
    private final int originY;
    private final int originZ;
    /** x1 y1 z1 x2 y2 z2 for each line, relative to the origin. */
    private final float[] ends;
    /** ARGB for each line. */
    private final int[] colors;

    private LineMesh(int originX, int originY, int originZ, float[] ends, int[] colors) {
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.ends = ends;
        this.colors = colors;
    }

    public static Builder builder(int originX, int originY, int originZ) {
        return new Builder(originX, originY, originZ);
    }

    int originX() {
        return originX;
    }

    int originY() {
        return originY;
    }

    int originZ() {
        return originZ;
    }

    int lineCount() {
        return colors.length;
    }

    /** The first of the line's six floats in {@link #ends()}. */
    static int endsOffset(int line) {
        return line * 6;
    }

    float[] ends() {
        return ends;
    }

    int color(int line) {
        return colors[line];
    }

    /** Collects lines in world coordinates. Not thread-safe; used by one builder of a mesh. */
    public static final class Builder {
        private final int originX;
        private final int originY;
        private final int originZ;
        private final FloatArrayList ends = new FloatArrayList();
        private final IntArrayList colors = new IntArrayList();

        private Builder(int originX, int originY, int originZ) {
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
        }

        public Builder line(double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
            ends.add((float) (x1 - originX));
            ends.add((float) (y1 - originY));
            ends.add((float) (z1 - originZ));
            ends.add((float) (x2 - originX));
            ends.add((float) (y2 - originY));
            ends.add((float) (z2 - originZ));
            colors.add(argb);
            return this;
        }

        /** The twelve edges of a box given by its corners. */
        public Builder box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int argb) {
            line(minX, minY, minZ, maxX, minY, minZ, argb);
            line(maxX, minY, minZ, maxX, minY, maxZ, argb);
            line(maxX, minY, maxZ, minX, minY, maxZ, argb);
            line(minX, minY, maxZ, minX, minY, minZ, argb);
            line(minX, maxY, minZ, maxX, maxY, minZ, argb);
            line(maxX, maxY, minZ, maxX, maxY, maxZ, argb);
            line(maxX, maxY, maxZ, minX, maxY, maxZ, argb);
            line(minX, maxY, maxZ, minX, maxY, minZ, argb);
            line(minX, minY, minZ, minX, maxY, minZ, argb);
            line(maxX, minY, minZ, maxX, maxY, minZ, argb);
            line(maxX, minY, maxZ, maxX, maxY, maxZ, argb);
            line(minX, minY, maxZ, minX, maxY, maxZ, argb);
            return this;
        }

        public LineMesh build() {
            return new LineMesh(originX, originY, originZ, ends.toFloatArray(), colors.toIntArray());
        }
    }
}
