package com.ArchitectsToolbox.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

public class SubdivisionRenderer implements ShapeRenderer {

    private static final RenderBuffer BUFFER = new RenderBuffer(4096);

    @Override
    public void render(Camera camera, Matrix4f viewMatrix, List<BlockPos> selection, RenderConfig config) {
        if (selection.size() < 2 || config.subdivisions() <= 0) return;

        AABB box = getBoundingBox(selection);
        Vec3 camPos = camera.position();

        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(viewMatrix);
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        MultiBufferSource.BufferSource source = BUFFER.createSource();
        VertexConsumer consumer = source.getBuffer(RenderTypes.LINES);
        Matrix4f matrix = poseStack.last().pose();

        int subdivisions = config.subdivisions();
        float r = 0.3f, g = 0.3f, b = 1.0f, a = config.alpha() * 0.7f;

        double sizeX = box.maxX - box.minX;
        double sizeY = box.maxY - box.minY;
        double sizeZ = box.maxZ - box.minZ;

        // X-axis subdivisions
        for (int i = 1; i < subdivisions; i++) {
            double x = box.minX + sizeX * i / subdivisions;
            RenderUtils.drawLine(consumer, matrix, x, box.minY, box.minZ, x, box.maxY, box.minZ, r, g, b, a);
            RenderUtils.drawLine(consumer, matrix, x, box.minY, box.maxZ, x, box.maxY, box.maxZ, r, g, b, a);
            RenderUtils.drawLine(consumer, matrix, x, box.minY, box.minZ, x, box.minY, box.maxZ, r, g, b, a);
            RenderUtils.drawLine(consumer, matrix, x, box.maxY, box.minZ, x, box.maxY, box.maxZ, r, g, b, a);
        }

        // Y-axis subdivisions
        for (int i = 1; i < subdivisions; i++) {
            double y = box.minY + sizeY * i / subdivisions;
            RenderUtils.drawLine(consumer, matrix, box.minX, y, box.minZ, box.maxX, y, box.minZ, r, g, b, a);
            RenderUtils.drawLine(consumer, matrix, box.minX, y, box.maxZ, box.maxX, y, box.maxZ, r, g, b, a);
            RenderUtils.drawLine(consumer, matrix, box.minX, y, box.minZ, box.minX, y, box.maxZ, r, g, b, a);
            RenderUtils.drawLine(consumer, matrix, box.maxX, y, box.minZ, box.maxX, y, box.maxZ, r, g, b, a);
        }

        // Z-axis subdivisions
        for (int i = 1; i < subdivisions; i++) {
            double z = box.minZ + sizeZ * i / subdivisions;
            RenderUtils.drawLine(consumer, matrix, box.minX, box.minY, z, box.maxX, box.minY, z, r, g, b, a);
            RenderUtils.drawLine(consumer, matrix, box.minX, box.maxY, z, box.maxX, box.maxY, z, r, g, b, a);
            RenderUtils.drawLine(consumer, matrix, box.minX, box.minY, z, box.minX, box.maxY, z, r, g, b, a);
            RenderUtils.drawLine(consumer, matrix, box.maxX, box.minY, z, box.maxX, box.maxY, z, r, g, b, a);
        }

        source.endBatch();
    }

    @Override
    public void renderLabels(Camera camera, Matrix4f viewMatrix, List<BlockPos> selection, RenderConfig config) {
        if (!config.showLabels() || selection.size() < 2 || config.subdivisions() <= 0) return;

        AABB box = getBoundingBox(selection);
        int subdivisions = config.subdivisions();

        double sizeX = box.maxX - box.minX;
        double sizeY = box.maxY - box.minY;
        double sizeZ = box.maxZ - box.minZ;

        // X subdivision labels
        for (int i = 1; i < subdivisions; i++) {
            double x = box.minX + sizeX * i / subdivisions;
            double segWidth = sizeX / subdivisions;
            RenderUtils.renderFloatingText(camera, viewMatrix,
                    String.format("%.1f", segWidth),
                    new Vec3(x, box.minY - 0.5, box.minZ - 0.5),
                    0.3f, 0.3f, 1.0f);
        }

        // Y subdivision labels
        for (int i = 1; i < subdivisions; i++) {
            double y = box.minY + sizeY * i / subdivisions;
            double segHeight = sizeY / subdivisions;
            RenderUtils.renderFloatingText(camera, viewMatrix,
                    String.format("%.1f", segHeight),
                    new Vec3(box.minX - 0.5, y, box.minZ - 0.5),
                    0.3f, 0.3f, 1.0f);
        }

        // Z subdivision labels
        for (int i = 1; i < subdivisions; i++) {
            double z = box.minZ + sizeZ * i / subdivisions;
            double segDepth = sizeZ / subdivisions;
            RenderUtils.renderFloatingText(camera, viewMatrix,
                    String.format("%.1f", segDepth),
                    new Vec3(box.minX - 0.5, box.minY - 0.5, z),
                    0.3f, 0.3f, 1.0f);
        }
    }

    private AABB getBoundingBox(List<BlockPos> selection) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : selection) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }
}
