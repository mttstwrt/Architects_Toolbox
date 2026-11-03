package com.ArchitectsToolbox.render;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Throwaway check of the render layer for ATB-012: a box, three ghost blocks and a label beside where the player
 * first stands in each level. Removed once the owner has seen it on 1.21.1 and on 26.3 with OpenGL and Vulkan.
 */
public final class RenderProbe {
    private final float ghostOpacity;
    private @Nullable ClientLevel level;
    private WorldScene scene = WorldScene.EMPTY;

    public RenderProbe(float ghostOpacity) {
        this.ghostOpacity = ghostOpacity;
    }

    /** The probe's scene, built again only when the player is in a different level. Client thread. */
    public WorldScene update(Minecraft minecraft) {
        if (minecraft.level != level) {
            level = minecraft.level;
            scene = level == null || minecraft.player == null ? WorldScene.EMPTY : build(minecraft.player.blockPosition().offset(3, 0, 0));
        }
        return scene;
    }

    private WorldScene build(BlockPos anchor) {
        LineMesh box = LineMesh.builder(anchor.getX(), anchor.getY(), anchor.getZ())
                .box(anchor.getX(), anchor.getY(), anchor.getZ(), anchor.getX() + 3, anchor.getY() + 3, anchor.getZ() + 3, 0xFFFF3030)
                .build();
        BlockPos ghosts = anchor.offset(5, 0, 1);
        GhostMesh ghostMesh = new GhostMesh(List.of(
                new GhostMesh.Ghost(ghosts, Blocks.OAK_PLANKS.defaultBlockState()),
                new GhostMesh.Ghost(ghosts.above(), Blocks.STONE_BRICKS.defaultBlockState()),
                new GhostMesh.Ghost(ghosts.above(2), Blocks.GLASS.defaultBlockState())), ghostOpacity);
        WorldLabel label = new WorldLabel(new Vec3(anchor.getX() + 1.5, anchor.getY() + 4, anchor.getZ() + 1.5),
                Component.literal("Architect's Toolbox render probe"), 0xFFFFFFFF);
        return new WorldScene(List.of(box), List.of(ghostMesh), List.of(label));
    }
}
