package com.ArchitectsToolbox.render;

import com.ArchitectsToolbox.ClipboardManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.*;

public class GhostBlockRenderer {

    private static final RenderBuffer BUFFER = new RenderBuffer(262144);
    private static final RenderBuffer LINE_BUFFER = new RenderBuffer(8192);

    // Cache for locked placements
    private Map<BlockPos, BlockState> cachedGhostBlocks = null;
    private boolean cacheValid = false;

    public void render(Camera camera, Matrix4f viewMatrix, List<BlockPos> selection,
                       ShapeRenderer.RenderConfig config) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        ClipboardManager clipboard = ClipboardManager.getInstance();

        // Render preview placement (current paste preview)
        if (clipboard.isPastePreviewActive()) {
            BlockPos anchor = clipboard.getPreviewAnchorPos();
            if (anchor != null) {
                Map<BlockPos, BlockState> previewBlocks = clipboard.getPreviewBlocks();
                if (previewBlocks != null && !previewBlocks.isEmpty()) {
                    // Convert relative positions to world positions using anchor
                    Map<BlockPos, BlockState> worldPreviewBlocks = new LinkedHashMap<>();
                    for (Map.Entry<BlockPos, BlockState> entry : previewBlocks.entrySet()) {
                        BlockPos relPos = entry.getKey();
                        BlockPos worldPos = anchor.offset(relPos.getX(), relPos.getY(), relPos.getZ());
                        worldPreviewBlocks.put(worldPos, entry.getValue());
                    }
                    renderGhostBlocks(camera, viewMatrix, worldPreviewBlocks, client, 0.5f, true);
                }
            }
        }

        // Render locked placements
        if (clipboard.hasLockedPlacements()) {
            Map<BlockPos, BlockState> lockedBlocks = getLockedBlocks(clipboard);
            if (lockedBlocks != null && !lockedBlocks.isEmpty()) {
                boolean layerMode = clipboard.isLayerViewEnabled();
                int layerY = layerMode ? clipboard.getCurrentViewLayerY() : -1;

                if (layerMode) {
                    Map<BlockPos, BlockState> layerBlocks = new LinkedHashMap<>();
                    for (Map.Entry<BlockPos, BlockState> entry : lockedBlocks.entrySet()) {
                        if (entry.getKey().getY() == layerY) {
                            layerBlocks.put(entry.getKey(), entry.getValue());
                        }
                    }
                    renderGhostBlocks(camera, viewMatrix, layerBlocks, client, 0.8f, false);
                } else {
                    renderGhostBlocks(camera, viewMatrix, lockedBlocks, client, 0.8f, false);
                }
            }
        }
    }

    private void renderGhostBlocks(Camera camera, Matrix4f viewMatrix,
                                    Map<BlockPos, BlockState> blocks, Minecraft client,
                                    float alpha, boolean isPreview) {
        if (blocks.isEmpty()) return;

        Vec3 camPos = camera.position();
        Level level = client.level;
        BlockRenderDispatcher blockRenderDispatcher = client.getBlockRenderer();

        // Sort blocks by distance for proper transparency
        List<Map.Entry<BlockPos, BlockState>> sortedBlocks = new ArrayList<>(blocks.entrySet());
        sortedBlocks.sort((a, b) -> {
            double distA = a.getKey().distSqr(BlockPos.containing(camPos));
            double distB = b.getKey().distSqr(BlockPos.containing(camPos));
            return Double.compare(distB, distA);
        });

        MultiBufferSource.BufferSource source = BUFFER.createSource();

        for (Map.Entry<BlockPos, BlockState> entry : sortedBlocks) {
            BlockPos pos = entry.getKey();
            BlockState state = entry.getValue();

            if (state.getRenderShape() == RenderShape.INVISIBLE) continue;

            // Distance culling
            double distSq = pos.distSqr(BlockPos.containing(camPos));
            if (distSq > 256 * 256) continue;

            PoseStack poseStack = new PoseStack();
            poseStack.mulPose(viewMatrix);
            poseStack.translate(
                    pos.getX() - camPos.x,
                    pos.getY() - camPos.y,
                    pos.getZ() - camPos.z
            );

            int light = LightTexture.pack(15, 15);
            int overlay = OverlayTexture.NO_OVERLAY;

            try {
                blockRenderDispatcher.renderSingleBlock(state, poseStack, source, light, overlay);
            } catch (Exception e) {
                // Skip blocks that fail to render
            }
        }

        source.endBatch();

        // Draw outlines around ghost blocks
        if (isPreview) {
            renderGhostBlockOutlines(camera, viewMatrix, blocks, 0.3f, 0.8f, 1.0f, 0.6f);
        } else {
            renderGhostBlockOutlines(camera, viewMatrix, blocks, 0.2f, 1.0f, 0.2f, 0.4f);
        }
    }

    private void renderGhostBlockOutlines(Camera camera, Matrix4f viewMatrix,
                                           Map<BlockPos, BlockState> blocks,
                                           float r, float g, float b, float a) {
        Vec3 camPos = camera.position();

        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(viewMatrix);
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        MultiBufferSource.BufferSource source = LINE_BUFFER.createSource();
        VertexConsumer consumer = source.getBuffer(RenderTypes.LINES);
        Matrix4f matrix = poseStack.last().pose();

        Set<BlockPos> blockSet = blocks.keySet();
        int rendered = 0;

        for (BlockPos pos : blockSet) {
            float minX = pos.getX();
            float minY = pos.getY();
            float minZ = pos.getZ();
            float maxX = minX + 1;
            float maxY = minY + 1;
            float maxZ = minZ + 1;

            // Only draw exposed faces
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

    private Map<BlockPos, BlockState> getLockedBlocks(ClipboardManager clipboard) {
        if (cacheValid && cachedGhostBlocks != null) {
            return cachedGhostBlocks;
        }

        cachedGhostBlocks = clipboard.getAllLockedBlocks();
        cacheValid = true;
        return cachedGhostBlocks;
    }

    public void invalidateCache() {
        cacheValid = false;
        cachedGhostBlocks = null;
    }

    /**
     * Gets the map color for a block state at a position, returning its integer color value.
     * Uses Mojmap: state.getMapColor(level, pos).col
     */
    @SuppressWarnings("unused")
    private int getMapColor(BlockState state, Level level, BlockPos pos) {
        return state.getMapColor(level, pos).col;
    }
}
