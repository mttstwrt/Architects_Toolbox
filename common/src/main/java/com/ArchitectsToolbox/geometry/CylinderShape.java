package com.ArchitectsToolbox.geometry;

import net.minecraft.core.Direction;

/**
 * A cylinder, elliptic when its two radii differ, standing along one axis. A block is inside when its centre is
 * within the ellipse of the cross-section. {@code u} and {@code v} are the two cross axes in the order x, y, z.
 */
public record CylinderShape(BlockBox bounds, Direction.Axis axis, double centreU, double centreV, double radiusU, double radiusV) implements Shape {
    /** Fills the box: its cross-section sides are the diameters, its third side the length. */
    public static CylinderShape fitting(BlockBox box, Direction.Axis axis) {
        return switch (axis) {
            case X -> new CylinderShape(box, axis, centre(box.minY(), box.maxY()), centre(box.minZ(), box.maxZ()), box.sizeY() / 2.0, box.sizeZ() / 2.0);
            case Y -> new CylinderShape(box, axis, centre(box.minX(), box.maxX()), centre(box.minZ(), box.maxZ()), box.sizeX() / 2.0, box.sizeZ() / 2.0);
            case Z -> new CylinderShape(box, axis, centre(box.minX(), box.maxX()), centre(box.minY(), box.maxY()), box.sizeX() / 2.0, box.sizeY() / 2.0);
        };
    }

    /** Upright, round, centred on a block: the radius reaches the far side of the rim block; the points' Y span is the height. */
    public static CylinderShape centreAndRim(int centreX, int centreZ, double radius, int minY, int maxY) {
        double cx = centreX + 0.5;
        double cz = centreZ + 0.5;
        BlockBox box = new BlockBox((int) Math.floor(cx - radius), minY, (int) Math.floor(cz - radius),
                (int) Math.ceil(cx + radius) - 1, maxY, (int) Math.ceil(cz + radius) - 1);
        return new CylinderShape(box, Direction.Axis.Y, cx, cz, radius, radius);
    }

    private static double centre(int min, int max) {
        return (min + max + 1) / 2.0;
    }

    @Override
    public boolean contains(int x, int y, int z) {
        double u = switch (axis) {
            case X -> y;
            case Y, Z -> x;
        } + 0.5;
        double v = switch (axis) {
            case X, Y -> z;
            case Z -> y;
        } + 0.5;
        double du = (u - centreU) / radiusU;
        double dv = (v - centreV) / radiusV;
        return du * du + dv * dv <= 1.0;
    }
}
