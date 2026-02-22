package com.ArchitectsToolbox.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

public class ChunkBoundaryRenderer implements ShapeRenderer {

    private static final RenderBuffer BUFFER = new RenderBuffer(8192);
    private static final int CHUNK_SIZE = 16;
    private static final int RENDER_RADIUS = 2;
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;

    @Override
    public void render(Camera camera, Matrix4f viewMatrix, List<BlockPos> selection, RenderConfig config) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        Vec3 camPos = camera.getPosition();
        ChunkPos playerChunk = new ChunkPos(client.player.blockPosition());

        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(viewMatrix);

        MultiBufferSource.BufferSource source = BUFFER.createSource();
        VertexConsumer consumer = source.getBuffer(RenderType.lines());
        Matrix4f matrix = poseStack.last().pose();

        // Colors for different boundary types
        float currentR = 1.0f, currentG = 1.0f, currentB = 0.0f; // Yellow for current chunk
        float neighborR = 0.3f, neighborG = 0.7f, neighborB = 1.0f; // Blue for neighbor chunks

        // Render chunk boundaries within radius
        for (int dx = -RENDER_RADIUS; dx <= RENDER_RADIUS; dx++) {
            for (int dz = -RENDER_RADIUS; dz <= RENDER_RADIUS; dz++) {
                int chunkX = playerChunk.x + dx;
                int chunkZ = playerChunk.z + dz;

                boolean isCurrentChunk = (dx == 0 && dz == 0);
                float r = isCurrentChunk ? currentR : neighborR;
                float g = isCurrentChunk ? currentG : neighborG;
                float b = isCurrentChunk ? currentB : neighborB;
                float a = isCurrentChunk ? 1.0f : 0.6f;

                renderChunkBoundary(matrix, consumer, camPos, chunkX, chunkZ, r, g, b, a);
            }
        }

        source.endBatch();

        // Draw chunk coordinate labels
        drawChunkLabels(camera, viewMatrix, playerChunk, camPos);
    }

    private void renderChunkBoundary(Matrix4f matrix, VertexConsumer consumer, Vec3 camPos,
                                      int chunkX, int chunkZ, float r, float g, float b, float a) {
        float x1 = (float) (chunkX * CHUNK_SIZE - camPos.x);
        float x2 = (float) ((chunkX + 1) * CHUNK_SIZE - camPos.x);
        float z1 = (float) (chunkZ * CHUNK_SIZE - camPos.z);
        float z2 = (float) ((chunkZ + 1) * CHUNK_SIZE - camPos.z);

        // Render Y range based on camera position to avoid rendering too many lines
        int viewMinY = Math.max(MIN_Y, (int) camPos.y - 64);
        int viewMaxY = Math.min(MAX_Y, (int) camPos.y + 64);

        float y1 = (float) (viewMinY - camPos.y);
        float y2 = (float) (viewMaxY - camPos.y);

        // Draw the four vertical edges of the chunk
        RenderUtils.drawLine(consumer, matrix, x1, y1, z1, x1, y2, z1, r, g, b, a);
        RenderUtils.drawLine(consumer, matrix, x2, y1, z1, x2, y2, z1, r, g, b, a);
        RenderUtils.drawLine(consumer, matrix, x1, y1, z2, x1, y2, z2, r, g, b, a);
        RenderUtils.drawLine(consumer, matrix, x2, y1, z2, x2, y2, z2, r, g, b, a);

        // Draw horizontal lines at regular Y intervals
        int yStep = 16;
        for (int y = ((viewMinY / yStep) * yStep); y <= viewMaxY; y += yStep) {
            float yPos = (float) (y - camPos.y);

            // Bottom edges
            RenderUtils.drawLine(consumer, matrix, x1, yPos, z1, x2, yPos, z1, r, g, b, a);
            RenderUtils.drawLine(consumer, matrix, x1, yPos, z2, x2, yPos, z2, r, g, b, a);
            RenderUtils.drawLine(consumer, matrix, x1, yPos, z1, x1, yPos, z2, r, g, b, a);
            RenderUtils.drawLine(consumer, matrix, x2, yPos, z1, x2, yPos, z2, r, g, b, a);
        }
    }

    private void drawChunkLabels(Camera camera, Matrix4f viewMatrix, ChunkPos playerChunk, Vec3 camPos) {
        // Draw label for current chunk at a visible position
        double labelX = (playerChunk.x * CHUNK_SIZE) + CHUNK_SIZE / 2.0;
        double labelY = camPos.y + 2;
        double labelZ = (playerChunk.z * CHUNK_SIZE) + CHUNK_SIZE / 2.0;

        String chunkLabel = "Chunk [" + playerChunk.x + ", " + playerChunk.z + "]";
        RenderUtils.renderFloatingText(camera, viewMatrix, chunkLabel, new Vec3(labelX, labelY, labelZ));
    }
}
