package com.ArchitectsToolbox.clipboard;

import com.ArchitectsToolbox.geometry.BlockBox;
import net.minecraft.core.BlockPos;

/**
 * A clipboard, already rotated, with its lowest corner at {@code origin}: a paste preview, or a placement locked as
 * a ghost guide (F32). Immutable.
 */
public record Placement(Clipboard blocks, BlockPos origin) {
    public BlockBox bounds() {
        return new BlockBox(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + blocks.sizeX() - 1, origin.getY() + blocks.sizeY() - 1, origin.getZ() + blocks.sizeZ() - 1);
    }

    /** The world position of block {@code i}. */
    public BlockPos pos(int i) {
        return BlockPos.of(blocks.position(i)).offset(origin);
    }
}
