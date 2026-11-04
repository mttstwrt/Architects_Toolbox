package com.ArchitectsToolbox.overlay;

import com.ArchitectsToolbox.client.ClientCompat;
import com.ArchitectsToolbox.render.LineMesh;
import com.ArchitectsToolbox.render.WorldLabel;
import com.ArchitectsToolbox.render.WorldScene;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The biome borders overlay (F41): the biome is sampled at the player's height in 4-block cells, as biomes are
 * stored, within 64 blocks; where two neighbouring cells differ a wall of lines 32 blocks above and below the player
 * marks the border, and each biome is named at its cell nearest the player. Cells in chunks the client has not loaded
 * are left out, since the client reports a default biome for them. Built on the client thread from the client's world.
 */
final class BiomeBorders {
    private static final int RANGE = 64;
    private static final int CELL = 4;
    /** How far above and below the player the walls reach. */
    private static final int WALL = 32;
    private static final int COLOUR = 0xB300CCFF;

    /** The overlay, and whether every cell was in a loaded chunk; if not, it is worth building again as chunks load. */
    record Result(WorldScene scene, boolean complete) {}

    private BiomeBorders() {
    }

    /**
     * @param minY the level's lowest block Y
     * @param topY the Y just above the level's highest block
     */
    static Result build(ClientLevel level, BlockPos player, int minY, int topY) {
        int cells = 2 * RANGE / CELL + 1;
        int originX = Math.floorDiv(player.getX() - RANGE, CELL) * CELL;
        int originZ = Math.floorDiv(player.getZ() - RANGE, CELL) * CELL;
        int y = player.getY();
        @SuppressWarnings("unchecked")
        Holder<Biome>[] biomes = new Holder[cells * cells];
        boolean complete = true;
        BlockPos.MutableBlockPos sample = new BlockPos.MutableBlockPos();
        for (int i = 0; i < cells; i++) {
            for (int j = 0; j < cells; j++) {
                int x = originX + i * CELL;
                int z = originZ + j * CELL;
                if (level.hasChunk(x >> 4, z >> 4)) {
                    biomes[i * cells + j] = level.getBiome(sample.set(x + CELL / 2, y, z + CELL / 2));
                } else {
                    complete = false;
                }
            }
        }

        int fromY = Math.max(minY, y - WALL);
        int toY = Math.min(topY, y + WALL);
        LineMesh.Builder lines = LineMesh.builder(originX, fromY, originZ);
        // Corners where walls meet are shared; each gets one vertical line.
        LongOpenHashSet corners = new LongOpenHashSet();
        Map<String, BlockPos> nearest = new HashMap<>();
        for (int i = 0; i < cells; i++) {
            for (int j = 0; j < cells; j++) {
                Holder<Biome> biome = biomes[i * cells + j];
                if (biome == null) {
                    continue;
                }
                int x = originX + i * CELL;
                int z = originZ + j * CELL;
                if (i + 1 < cells && differs(biome, biomes[(i + 1) * cells + j])) {
                    wall(lines, corners, x + CELL, z, x + CELL, z + CELL, fromY, toY);
                }
                if (j + 1 < cells && differs(biome, biomes[i * cells + j + 1])) {
                    wall(lines, corners, x, z + CELL, x + CELL, z + CELL, fromY, toY);
                }
                biome.unwrapKey().ifPresent(key -> {
                    BlockPos centre = new BlockPos(x + CELL / 2, y, z + CELL / 2);
                    nearest.merge(ClientCompat.id(key), centre, (a, b) -> a.distSqr(player) <= b.distSqr(player) ? a : b);
                });
            }
        }
        List<WorldLabel> labels = new ArrayList<>();
        nearest.forEach((id, pos) -> labels.add(new WorldLabel(new Vec3(pos.getX() + 0.5, pos.getY() + 3.5, pos.getZ() + 0.5), Component.literal(id), COLOUR | 0xFF000000)));
        return new Result(new WorldScene(List.of(lines.build()), List.of(), labels), complete);
    }

    /** Only a known biome next to a different known biome is a border. */
    private static boolean differs(Holder<Biome> biome, @Nullable Holder<Biome> neighbour) {
        return neighbour != null && !biome.equals(neighbour);
    }

    /** A border along one cell side, from (x1, z1) to (x2, z2): its top and bottom, and its ends once each. */
    private static void wall(LineMesh.Builder lines, LongOpenHashSet corners, int x1, int z1, int x2, int z2, int fromY, int toY) {
        lines.line(x1, fromY, z1, x2, fromY, z2, COLOUR);
        lines.line(x1, toY, z1, x2, toY, z2, COLOUR);
        if (corners.add(BlockPos.asLong(x1, 0, z1))) {
            lines.line(x1, fromY, z1, x1, toY, z1, COLOUR);
        }
        if (corners.add(BlockPos.asLong(x2, 0, z2))) {
            lines.line(x2, fromY, z2, x2, toY, z2, COLOUR);
        }
    }
}
