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
    private final CachedLineBuffer cachedLineBuffer = new CachedLineBuffer();

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

        if (layerMode) {
            renderImmediate(camera, viewMatrix, blocks, config, sm.getCurrentLayerY());
        } else {
            renderCached(camera, viewMatrix, blocks, config);
        }
    }

    private void renderCached(Camera camera, Matrix4f viewMatrix, List<BlockPos> blocks,
                              ShapeRenderer.RenderConfig config) {
        if (cachedLineBuffer.isDirty()) {
            float r = config.red(), g = config.green(), b = config.blue(), a = config.alpha();
            Set<BlockPos> blockSet = new HashSet<>(blocks);

            cachedLineBuffer.begin();
            int rendered = 0;

            for (BlockPos pos : blocks) {
                float minX = pos.getX();
                float minY = pos.getY();
                float minZ = pos.getZ();
                float maxX = minX + 1;
                float maxY = minY + 1;
                float maxZ = minZ + 1;

                if (!blockSet.contains(pos.below())) {
                    addFaceOutlineCached(minX, minY, minZ, maxX, minY, maxZ, 'y', r, g, b, a);
                }
                if (!blockSet.contains(pos.above())) {
                    addFaceOutlineCached(minX, maxY, minZ, maxX, maxY, maxZ, 'y', r, g, b, a);
                }
                if (!blockSet.contains(pos.west())) {
                    addFaceOutlineCached(minX, minY, minZ, minX, maxY, maxZ, 'x', r, g, b, a);
                }
                if (!blockSet.contains(pos.east())) {
                    addFaceOutlineCached(maxX, minY, minZ, maxX, maxY, maxZ, 'x', r, g, b, a);
                }
                if (!blockSet.contains(pos.north())) {
                    addFaceOutlineCached(minX, minY, minZ, maxX, maxY, minZ, 'z', r, g, b, a);
                }
                if (!blockSet.contains(pos.south())) {
                    addFaceOutlineCached(minX, minY, maxZ, maxX, maxY, maxZ, 'z', r, g, b, a);
                }

                rendered++;
                if (rendered > 50000) break;
            }

            cachedLineBuffer.upload();
        }

        Vec3 camPos = camera.getPosition();
        cachedLineBuffer.draw(viewMatrix, camPos.x, camPos.y, camPos.z);
    }

    private void renderImmediate(Camera camera, Matrix4f viewMatrix, List<BlockPos> blocks,
                                 ShapeRenderer.RenderConfig config, int layerY) {
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
            if (pos.getY() != layerY) continue;

            float minX = pos.getX();
            float minY = pos.getY();
            float minZ = pos.getZ();
            float maxX = minX + 1;
            float maxY = minY + 1;
            float maxZ = minZ + 1;

            if (!blockSet.contains(pos.below())) {
                drawFaceOutline(consumer, matrix, minX, minY, minZ, maxX, minY, maxZ, 'y', r, g, b, a);
            }
            if (!blockSet.contains(pos.above())) {
                drawFaceOutline(consumer, matrix, minX, maxY, minZ, maxX, maxY, maxZ, 'y', r, g, b, a);
            }
            if (!blockSet.contains(pos.west())) {
                drawFaceOutline(consumer, matrix, minX, minY, minZ, minX, maxY, maxZ, 'x', r, g, b, a);
            }
            if (!blockSet.contains(pos.east())) {
                drawFaceOutline(consumer, matrix, maxX, minY, minZ, maxX, maxY, maxZ, 'x', r, g, b, a);
            }
            if (!blockSet.contains(pos.north())) {
                drawFaceOutline(consumer, matrix, minX, minY, minZ, maxX, maxY, minZ, 'z', r, g, b, a);
            }
            if (!blockSet.contains(pos.south())) {
                drawFaceOutline(consumer, matrix, minX, minY, maxZ, maxX, maxY, maxZ, 'z', r, g, b, a);
            }

            rendered++;
            if (rendered > 50000) break;
        }

        source.endBatch();
    }

    private void addFaceOutlineCached(float x1, float y1, float z1,
                                      float x2, float y2, float z2,
                                      char axis,
                                      float r, float g, float b, float a) {
        switch (axis) {
            case 'y' -> {
                cachedLineBuffer.addLine(x1, y1, z1, x2, y1, z1, r, g, b, a);
                cachedLineBuffer.addLine(x2, y1, z1, x2, y1, z2, r, g, b, a);
                cachedLineBuffer.addLine(x2, y1, z2, x1, y1, z2, r, g, b, a);
                cachedLineBuffer.addLine(x1, y1, z2, x1, y1, z1, r, g, b, a);
            }
            case 'x' -> {
                cachedLineBuffer.addLine(x1, y1, z1, x1, y2, z1, r, g, b, a);
                cachedLineBuffer.addLine(x1, y2, z1, x1, y2, z2, r, g, b, a);
                cachedLineBuffer.addLine(x1, y2, z2, x1, y1, z2, r, g, b, a);
                cachedLineBuffer.addLine(x1, y1, z2, x1, y1, z1, r, g, b, a);
            }
            case 'z' -> {
                cachedLineBuffer.addLine(x1, y1, z1, x2, y1, z1, r, g, b, a);
                cachedLineBuffer.addLine(x2, y1, z1, x2, y2, z1, r, g, b, a);
                cachedLineBuffer.addLine(x2, y2, z1, x1, y2, z1, r, g, b, a);
                cachedLineBuffer.addLine(x1, y2, z1, x1, y1, z1, r, g, b, a);
            }
        }
    }

    private void drawFaceOutline(VertexConsumer consumer, Matrix4f matrix,
                                  float x1, float y1, float z1,
                                  float x2, float y2, float z2,
                                  char axis,
                                  float r, float g, float b, float a) {
        switch (axis) {
            case 'y' -> {
                RenderUtils.drawLine(consumer, matrix, x1, y1, z1, x2, y1, z1, r, g, b, a);
                RenderUtils.drawLine(consumer, matrix, x2, y1, z1, x2, y1, z2, r, g, b, a);
                RenderUtils.drawLine(consumer, matrix, x2, y1, z2, x1, y1, z2, r, g, b, a);
                RenderUtils.drawLine(consumer, matrix, x1, y1, z2, x1, y1, z1, r, g, b, a);
            }
            case 'x' -> {
                RenderUtils.drawLine(consumer, matrix, x1, y1, z1, x1, y2, z1, r, g, b, a);
                RenderUtils.drawLine(consumer, matrix, x1, y2, z1, x1, y2, z2, r, g, b, a);
                RenderUtils.drawLine(consumer, matrix, x1, y2, z2, x1, y1, z2, r, g, b, a);
                RenderUtils.drawLine(consumer, matrix, x1, y1, z2, x1, y1, z1, r, g, b, a);
            }
            case 'z' -> {
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
        cachedLineBuffer.markDirty();

        return cachedBlocks;
    }

    public void invalidateCache() {
        cacheValid = false;
        cachedBlocks = null;
        cachedSelection = null;
        cachedLineBuffer.markDirty();
    }
}
