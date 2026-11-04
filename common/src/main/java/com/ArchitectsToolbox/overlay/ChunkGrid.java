package com.ArchitectsToolbox.overlay;

import com.ArchitectsToolbox.render.LineMesh;
import com.ArchitectsToolbox.render.WorldLabel;
import com.ArchitectsToolbox.render.WorldScene;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * The chunk boundaries overlay (F40): the player's chunk in yellow and those within two chunks in blue, as vertical
 * edges with a ring every 16 blocks of height, within 64 blocks above and below the player and inside the level's
 * height. A label names the player's chunk. Small enough to build on the client thread whenever the player moves to
 * another chunk or block height.
 */
final class ChunkGrid {
    private static final int RADIUS = 2;
    /** How far above and below the player the grid reaches. */
    private static final int VIEW_RANGE = 64;
    private static final int CURRENT = 0xFFFFFF00;
    private static final int NEAR = 0x994DB3FF;

    private ChunkGrid() {
    }

    /**
     * @param minY the level's lowest block Y
     * @param topY the Y just above the level's highest block
     */
    static WorldScene build(int chunkX, int chunkZ, int playerY, int minY, int topY) {
        int fromY = Math.max(minY, playerY - VIEW_RANGE);
        int toY = Math.min(topY, playerY + VIEW_RANGE);
        LineMesh.Builder lines = LineMesh.builder(chunkX << 4, fromY, chunkZ << 4);
        // Neighbours first, so the player's chunk is drawn over the edges it shares with them.
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (dx != 0 || dz != 0) {
                    addChunk(lines, chunkX + dx, chunkZ + dz, fromY, toY, NEAR);
                }
            }
        }
        addChunk(lines, chunkX, chunkZ, fromY, toY, CURRENT);
        Vec3 labelPos = new Vec3((chunkX << 4) + 8.0, playerY + 2.0, (chunkZ << 4) + 8.0);
        WorldLabel label = new WorldLabel(labelPos, Component.translatable("architects_toolbox.overlay.chunk", chunkX, chunkZ), CURRENT);
        return new WorldScene(List.of(lines.build()), List.of(), List.of(label));
    }

    private static void addChunk(LineMesh.Builder lines, int chunkX, int chunkZ, int fromY, int toY, int argb) {
        int x1 = chunkX << 4;
        int z1 = chunkZ << 4;
        int x2 = x1 + 16;
        int z2 = z1 + 16;
        lines.line(x1, fromY, z1, x1, toY, z1, argb);
        lines.line(x2, fromY, z1, x2, toY, z1, argb);
        lines.line(x1, fromY, z2, x1, toY, z2, argb);
        lines.line(x2, fromY, z2, x2, toY, z2, argb);
        for (int y = Math.ceilDiv(fromY, 16) * 16; y <= toY; y += 16) {
            lines.line(x1, y, z1, x2, y, z1, argb);
            lines.line(x2, y, z1, x2, y, z2, argb);
            lines.line(x2, y, z2, x1, y, z2, argb);
            lines.line(x1, y, z2, x1, y, z1, argb);
        }
    }
}
