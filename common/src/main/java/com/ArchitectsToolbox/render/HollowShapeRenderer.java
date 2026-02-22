package com.ArchitectsToolbox.render;

import com.ArchitectsToolbox.SelectionManager;
import com.ArchitectsToolbox.shapes.ShapeMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.*;

public class HollowShapeRenderer {

    private static final RenderBuffer BUFFER = new RenderBuffer(65536);

    private List<BlockPos> cachedBlocks = null;
    private List<BlockPos> cachedSelection = null;
    private ShapeMode cachedMode = null;
    private int cachedSubdivisions = -1;
    private boolean cacheValid = false;

    public void render(Camera camera, Matrix4f viewMatrix, List<BlockPos> selection,
                       ShapeRenderer.RenderConfig config, ShapeMode mode) {
        if (selection.isEmpty()) return;

        List<BlockPos> blocks = getHollowBlocks(selection, mode, config);
        if (blocks == null || blocks.isEmpty()) return;

        SelectionManager sm = SelectionManager.getInstance();
        boolean layerMode = sm.isLayerModeEnabled();
        int layerY = layerMode ? sm.getCurrentLayerY() : -1;

        Vec3 camPos = camera.getPosition();

        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(viewMatrix);
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        MultiBufferSource.BufferSource source = BUFFER.createSource();
        VertexConsumer consumer = source.getBuffer(RenderType.lines());
        Matrix4f matrix = poseStack.last().pose();

        float r = config.red(), g = config.green(), b = config.blue(), a = config.alpha();

        Set<BlockPos> blockSet = new HashSet<>(blocks);
        int rendered = 0;

        for (BlockPos pos : blocks) {
            if (layerMode && pos.getY() != layerY) continue;

            // Only draw faces that are on the outside (no neighbor)
            float minX = pos.getX();
            float minY = pos.getY();
            float minZ = pos.getZ();
            float maxX = minX + 1;
            float maxY = minY + 1;
            float maxZ = minZ + 1;

            float blockAlpha = a;

            // Draw individual block outline
            if (!blockSet.contains(pos.below())) {
                drawFaceOutline(consumer, matrix, minX, minY, minZ, maxX, minY, maxZ, 'y', r, g, b, blockAlpha);
            }
            if (!blockSet.contains(pos.above())) {
                drawFaceOutline(consumer, matrix, minX, maxY, minZ, maxX, maxY, maxZ, 'y', r, g, b, blockAlpha);
            }
            if (!blockSet.contains(pos.west())) {
                drawFaceOutline(consumer, matrix, minX, minY, minZ, minX, maxY, maxZ, 'x', r, g, b, blockAlpha);
            }
            if (!blockSet.contains(pos.east())) {
                drawFaceOutline(consumer, matrix, maxX, minY, minZ, maxX, maxY, maxZ, 'x', r, g, b, blockAlpha);
            }
            if (!blockSet.contains(pos.north())) {
                drawFaceOutline(consumer, matrix, minX, minY, minZ, maxX, maxY, minZ, 'z', r, g, b, blockAlpha);
            }
            if (!blockSet.contains(pos.south())) {
                drawFaceOutline(consumer, matrix, minX, minY, maxZ, maxX, maxY, maxZ, 'z', r, g, b, blockAlpha);
            }

            rendered++;
            if (rendered > 50000) break;
        }

        source.endBatch();
    }

    private void drawFaceOutline(VertexConsumer consumer, Matrix4f matrix,
                                  float x1, float y1, float z1,
                                  float x2, float y2, float z2,
                                  char axis,
                                  float r, float g, float b, float a) {
        switch (axis) {
            case 'y' -> {
                // Horizontal face (floor/ceiling)
                RenderUtils.drawLine(consumer, matrix, x1, y1, z1, x2, y1, z1, r, g, b, a);
                RenderUtils.drawLine(consumer, matrix, x2, y1, z1, x2, y1, z2, r, g, b, a);
                RenderUtils.drawLine(consumer, matrix, x2, y1, z2, x1, y1, z2, r, g, b, a);
                RenderUtils.drawLine(consumer, matrix, x1, y1, z2, x1, y1, z1, r, g, b, a);
            }
            case 'x' -> {
                // Vertical face (west/east wall)
                RenderUtils.drawLine(consumer, matrix, x1, y1, z1, x1, y2, z1, r, g, b, a);
                RenderUtils.drawLine(consumer, matrix, x1, y2, z1, x1, y2, z2, r, g, b, a);
                RenderUtils.drawLine(consumer, matrix, x1, y2, z2, x1, y1, z2, r, g, b, a);
                RenderUtils.drawLine(consumer, matrix, x1, y1, z2, x1, y1, z1, r, g, b, a);
            }
            case 'z' -> {
                // Vertical face (north/south wall)
                RenderUtils.drawLine(consumer, matrix, x1, y1, z1, x2, y1, z1, r, g, b, a);
                RenderUtils.drawLine(consumer, matrix, x2, y1, z1, x2, y2, z1, r, g, b, a);
                RenderUtils.drawLine(consumer, matrix, x2, y2, z1, x1, y2, z1, r, g, b, a);
                RenderUtils.drawLine(consumer, matrix, x1, y2, z1, x1, y1, z1, r, g, b, a);
            }
        }
    }

    private List<BlockPos> getHollowBlocks(List<BlockPos> selection, ShapeMode mode,
                                            ShapeRenderer.RenderConfig config) {
        if (cacheValid && Objects.equals(cachedSelection, selection) &&
                cachedMode == mode && cachedSubdivisions == config.subdivisions()) {
            return cachedBlocks;
        }

        cachedBlocks = HollowBlockCalculator.calculate(selection, mode, config);
        cachedSelection = new ArrayList<>(selection);
        cachedMode = mode;
        cachedSubdivisions = config.subdivisions();
        cacheValid = true;

        return cachedBlocks;
    }

    public void invalidateCache() {
        cacheValid = false;
        cachedBlocks = null;
        cachedSelection = null;
    }
}
