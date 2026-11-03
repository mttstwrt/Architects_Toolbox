package com.ArchitectsToolbox.geometry;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * The blocks along a polyline (line and spline): with radius 0 the blocks the path passes through, otherwise every
 * block whose centre is within the radius of the path. The blocks are found once, on first use, from the segments
 * alone, so a long thin path costs what it covers and not its whole bounding box.
 */
public final class PathShape implements Shape {
    private final List<Vec3> path;
    private final int radius;
    private final BlockBox bounds;
    private volatile LongOpenHashSet blocks;

    public PathShape(List<Vec3> path, int radius) {
        this.path = List.copyOf(path);
        this.radius = radius;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (Vec3 p : path) {
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            minZ = Math.min(minZ, p.z);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
            maxZ = Math.max(maxZ, p.z);
        }
        this.bounds = new BlockBox(floor(minX - radius), floor(minY - radius), floor(minZ - radius),
                floor(maxX + radius), floor(maxY + radius), floor(maxZ + radius));
    }

    public List<Vec3> path() {
        return path;
    }

    public int radius() {
        return radius;
    }

    @Override
    public BlockBox bounds() {
        return bounds;
    }

    @Override
    public boolean contains(int x, int y, int z) {
        return blocks().contains(BlockPos.asLong(x, y, z));
    }

    /** About how many blocks finding the shape's blocks visits; callers keep it under their limit. */
    public long scanCost() {
        long cost = 0;
        for (int i = 0; i + 1 < path.size(); i++) {
            Vec3 a = path.get(i);
            Vec3 b = path.get(i + 1);
            if (radius == 0) {
                cost += (long) (Math.abs(b.x - a.x) + Math.abs(b.y - a.y) + Math.abs(b.z - a.z)) + 1;
            } else {
                cost += (long) (Math.abs(b.x - a.x) + 2 * radius + 1) * (long) (Math.abs(b.y - a.y) + 2 * radius + 1)
                        * (long) (Math.abs(b.z - a.z) + 2 * radius + 1);
            }
        }
        return Math.max(cost, 1);
    }

    /** The shape's blocks as {@link BlockPos#asLong} values. */
    public LongOpenHashSet blocks() {
        LongOpenHashSet found = blocks;
        if (found == null) {
            synchronized (this) {
                found = blocks;
                if (found == null) {
                    found = find();
                    blocks = found;
                }
            }
        }
        return found;
    }

    private LongOpenHashSet find() {
        LongOpenHashSet found = new LongOpenHashSet();
        if (path.size() == 1) {
            Vec3 p = path.getFirst();
            addAround(found, p, p);
            return found;
        }
        for (int i = 0; i + 1 < path.size(); i++) {
            if (radius == 0) {
                traverse(found, path.get(i), path.get(i + 1));
            } else {
                addAround(found, path.get(i), path.get(i + 1));
            }
        }
        return found;
    }

    /** Adds every block the segment passes through (Amanatides and Woo's grid walk). */
    private static void traverse(LongOpenHashSet found, Vec3 from, Vec3 to) {
        int x = floor(from.x), y = floor(from.y), z = floor(from.z);
        int endX = floor(to.x), endY = floor(to.y), endZ = floor(to.z);
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        int stepX = (int) Math.signum(dx), stepY = (int) Math.signum(dy), stepZ = (int) Math.signum(dz);
        double deltaX = stepX == 0 ? Double.MAX_VALUE : Math.abs(1 / dx);
        double deltaY = stepY == 0 ? Double.MAX_VALUE : Math.abs(1 / dy);
        double deltaZ = stepZ == 0 ? Double.MAX_VALUE : Math.abs(1 / dz);
        double maxX = stepX == 0 ? Double.MAX_VALUE : deltaX * (stepX > 0 ? x + 1 - from.x : from.x - x);
        double maxY = stepY == 0 ? Double.MAX_VALUE : deltaY * (stepY > 0 ? y + 1 - from.y : from.y - y);
        double maxZ = stepZ == 0 ? Double.MAX_VALUE : deltaZ * (stepZ > 0 ? z + 1 - from.z : from.z - z);
        found.add(BlockPos.asLong(x, y, z));
        int steps = Math.abs(endX - x) + Math.abs(endY - y) + Math.abs(endZ - z);
        for (int i = 0; i < steps; i++) {
            if (maxX < maxY && maxX < maxZ) {
                x += stepX;
                maxX += deltaX;
            } else if (maxY < maxZ) {
                y += stepY;
                maxY += deltaY;
            } else {
                z += stepZ;
                maxZ += deltaZ;
            }
            found.add(BlockPos.asLong(x, y, z));
        }
    }

    /** Adds every block whose centre is within the radius of the segment. */
    private void addAround(LongOpenHashSet found, Vec3 a, Vec3 b) {
        int minX = floor(Math.min(a.x, b.x) - radius), maxX = floor(Math.max(a.x, b.x) + radius);
        int minY = floor(Math.min(a.y, b.y) - radius), maxY = floor(Math.max(a.y, b.y) + radius);
        int minZ = floor(Math.min(a.z, b.z) - radius), maxZ = floor(Math.max(a.z, b.z) + radius);
        Vec3 ab = b.subtract(a);
        double lengthSq = ab.lengthSqr();
        double radiusSq = (double) radius * radius;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    double px = x + 0.5 - a.x, py = y + 0.5 - a.y, pz = z + 0.5 - a.z;
                    double t = lengthSq == 0 ? 0 : Math.clamp((px * ab.x + py * ab.y + pz * ab.z) / lengthSq, 0.0, 1.0);
                    double ox = px - ab.x * t, oy = py - ab.y * t, oz = pz - ab.z * t;
                    if (ox * ox + oy * oy + oz * oz <= radiusSq) {
                        found.add(BlockPos.asLong(x, y, z));
                    }
                }
            }
        }
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }
}
