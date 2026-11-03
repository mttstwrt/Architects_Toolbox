package com.ArchitectsToolbox.geometry;

/** Every block of the box: the rectangle shape. */
public record BoxShape(BlockBox bounds) implements Shape {
    @Override
    public boolean contains(int x, int y, int z) {
        return true;
    }
}
