package com.ArchitectsToolbox.geometry;

/** An ellipsoid; a block is inside when its centre is. */
public record EllipsoidShape(BlockBox bounds, double centreX, double centreY, double centreZ, double radiusX, double radiusY, double radiusZ) implements Shape {
    /** Inscribed in the box. */
    public static EllipsoidShape fitting(BlockBox box) {
        return new EllipsoidShape(box, (box.minX() + box.maxX() + 1) / 2.0, (box.minY() + box.maxY() + 1) / 2.0, (box.minZ() + box.maxZ() + 1) / 2.0,
                box.sizeX() / 2.0, box.sizeY() / 2.0, box.sizeZ() / 2.0);
    }

    /** Centred on a block; each radius reaches the far side of the block that many blocks away on that axis. */
    public static EllipsoidShape centred(int x, int y, int z, int dx, int dy, int dz) {
        double rx = Math.abs(dx) + 0.5, ry = Math.abs(dy) + 0.5, rz = Math.abs(dz) + 0.5;
        return new EllipsoidShape(new BlockBox(x - Math.abs(dx), y - Math.abs(dy), z - Math.abs(dz), x + Math.abs(dx), y + Math.abs(dy), z + Math.abs(dz)),
                x + 0.5, y + 0.5, z + 0.5, rx, ry, rz);
    }

    @Override
    public boolean contains(int x, int y, int z) {
        double dx = (x + 0.5 - centreX) / radiusX;
        double dy = (y + 0.5 - centreY) / radiusY;
        double dz = (z + 0.5 - centreZ) / radiusZ;
        return dx * dx + dy * dy + dz * dz <= 1.0;
    }
}
