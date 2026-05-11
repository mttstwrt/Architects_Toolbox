package com.ArchitectsToolbox.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BiomeBorderRenderer implements ShapeRenderer {

    private static final RenderBuffer BUFFER = new RenderBuffer(32768);
    private static final int SCAN_RADIUS = 64;
    private static final int SAMPLE_INTERVAL = 4;
    private static final double CACHE_MOVE_THRESHOLD = 4.0;

    // Cyan wireframe
    private static final float R = 0.0f, G = 0.8f, B = 1.0f, A = 0.7f;

    // Cache
    private List<BorderSegment> cachedBorders = null;
    private Map<ResourceLocation, Vec3> cachedBiomeLabelPositions = null;
    private Vec3 cacheCenter = null;
    private int cachePlayerY = 0;

    private record BorderSegment(float x1, float y1, float z1, float x2, float y2, float z2) {}

    @Override
    public void render(Camera camera, Matrix4f viewMatrix, List<BlockPos> selection, RenderConfig config) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Update cache if player moved significantly
        Vec3 playerPos = mc.player.position();
        int playerY = (int) mc.player.getY();
        if (cachedBorders == null
                || cacheCenter == null
                || cacheCenter.distanceTo(playerPos) > CACHE_MOVE_THRESHOLD
                || Math.abs(playerY - cachePlayerY) > 8) {
            scanBiomeBorders(mc, playerPos, playerY);
            cacheCenter = playerPos;
            cachePlayerY = playerY;
        }

        if (cachedBorders == null || cachedBorders.isEmpty()) return;

        // Render border lines
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(viewMatrix);

        MultiBufferSource.BufferSource source = BUFFER.createSource();
        VertexConsumer consumer = source.getBuffer(RenderType.lines());
        Matrix4f matrix = poseStack.last().pose();

        for (BorderSegment seg : cachedBorders) {
            float x1 = (float) (seg.x1 - camPos.x);
            float y1 = (float) (seg.y1 - camPos.y);
            float z1 = (float) (seg.z1 - camPos.z);
            float x2 = (float) (seg.x2 - camPos.x);
            float y2 = (float) (seg.y2 - camPos.y);
            float z2 = (float) (seg.z2 - camPos.z);

            RenderUtils.drawLine(consumer, matrix, x1, y1, z1, x2, y2, z2, R, G, B, A);
        }

        source.endBatch();

        // Render biome name labels
        if (cachedBiomeLabelPositions != null) {
            for (var entry : cachedBiomeLabelPositions.entrySet()) {
                double dist = camPos.distanceTo(entry.getValue());
                if (dist < 128.0) {
                    RenderUtils.renderFloatingText(camera, viewMatrix,
                            entry.getKey().toString(), entry.getValue(), R, G, B);
                }
            }
        }
    }

    private void scanBiomeBorders(Minecraft mc, Vec3 playerPos, int playerY) {
        var level = mc.level;

        int centerX = (int) Math.floor(playerPos.x);
        int centerZ = (int) Math.floor(playerPos.z);

        // Y range: player ± 32, clamped to world bounds
        int minY = Math.max(level.getMinY(), playerY - 32);
        int maxY = Math.min(level.getMaxY(), playerY + 32);

        int scanMinX = centerX - SCAN_RADIUS;
        int scanMaxX = centerX + SCAN_RADIUS;
        int scanMinZ = centerZ - SCAN_RADIUS;
        int scanMaxZ = centerZ + SCAN_RADIUS;

        List<BorderSegment> borders = new ArrayList<>();
        Map<ResourceLocation, Vec3> biomeLabels = new HashMap<>();

        // Sample at biome resolution intervals
        for (int x = scanMinX; x <= scanMaxX; x += SAMPLE_INTERVAL) {
            for (int z = scanMinZ; z <= scanMaxZ; z += SAMPLE_INTERVAL) {
                var currentBiome = level.getBiome(new BlockPos(x, playerY, z));

                // Check east neighbor (+4, 0, 0)
                var eastBiome = level.getBiome(new BlockPos(x + SAMPLE_INTERVAL, playerY, z));
                if (!currentBiome.equals(eastBiome)) {
                    // Border on the east edge of this cell
                    float bx = x + SAMPLE_INTERVAL;
                    // Vertical lines at the four-block boundary
                    borders.add(new BorderSegment(bx, minY, z, bx, maxY, z));
                    borders.add(new BorderSegment(bx, minY, z + SAMPLE_INTERVAL, bx, maxY, z + SAMPLE_INTERVAL));
                    // Horizontal connecting lines at top and bottom
                    borders.add(new BorderSegment(bx, minY, z, bx, minY, z + SAMPLE_INTERVAL));
                    borders.add(new BorderSegment(bx, maxY, z, bx, maxY, z + SAMPLE_INTERVAL));
                }

                // Check south neighbor (0, 0, +4)
                var southBiome = level.getBiome(new BlockPos(x, playerY, z + SAMPLE_INTERVAL));
                if (!currentBiome.equals(southBiome)) {
                    // Border on the south edge of this cell
                    float bz = z + SAMPLE_INTERVAL;
                    // Vertical lines at the four-block boundary
                    borders.add(new BorderSegment(x, minY, bz, x, maxY, bz));
                    borders.add(new BorderSegment(x + SAMPLE_INTERVAL, minY, bz, x + SAMPLE_INTERVAL, maxY, bz));
                    // Horizontal connecting lines at top and bottom
                    borders.add(new BorderSegment(x, minY, bz, x + SAMPLE_INTERVAL, minY, bz));
                    borders.add(new BorderSegment(x, maxY, bz, x + SAMPLE_INTERVAL, maxY, bz));
                }

                // Track biome label positions (use closest sample to player for each biome)
                final int sampleX = x;
                final int sampleZ = z;
                currentBiome.unwrapKey().ifPresent(key -> {
                    ResourceLocation id = key.location();
                    Vec3 samplePos = new Vec3(sampleX + 2, playerY + 3, sampleZ + 2);
                    Vec3 existing = biomeLabels.get(id);
                    if (existing == null || samplePos.distanceTo(playerPos) < existing.distanceTo(playerPos)) {
                        biomeLabels.put(id, samplePos);
                    }
                });
            }
        }

        cachedBorders = borders;
        cachedBiomeLabelPositions = biomeLabels;
    }
}
