package com.ArchitectsToolbox.render;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blocks drawn see-through at one opacity (0 to 1): a paste preview or a placed guide. Immutable; the render layer
 * prepares the block models of a mesh once and keeps them while the same mesh is shown.
 */
public record GhostMesh(List<Ghost> blocks, float opacity) {
    public GhostMesh {
        blocks = List.copyOf(blocks);
    }

    public record Ghost(BlockPos pos, BlockState state) {}
}
