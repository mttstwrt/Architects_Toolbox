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

public class RectangleRenderer implements ShapeRenderer {

    private static final RenderBuffer BUFFER = new RenderBuffer(1536);

    @Override
    public void render(Camera camera, Matrix4f viewMatrix, List<BlockPos> selection, RenderConfig config) {
        if (selection.isEmpty()) return;

        AABB box = getBoundingBox(selection);
        Vec3 camPos = camera.position();

        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(viewMatrix);
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        MultiBufferSource.BufferSource source = BUFFER.createSource();
        VertexConsumer consumer = source.getBuffer(RenderTypes.LINES);
        Matrix4f matrix = poseStack.last().pose();

        RenderUtils.drawBox(consumer, matrix,
                (float) box.minX, (float) box.minY, (float) box.minZ,
                (float) box.maxX, (float) box.maxY, (float) box.maxZ,
                config.red(), config.green(), config.blue(), config.alpha());

        source.endBatch();
    }

    @Override
    public void renderLabels(Camera camera, Matrix4f viewMatrix, List<BlockPos> selection, RenderConfig config) {
        if (!config.showLabels() || selection.size() < 2) return;

        AABB box = getBoundingBox(selection);

        int dx = (int) (box.maxX - box.minX);
        int dy = (int) (box.maxY - box.minY);
        int dz = (int) (box.maxZ - box.minZ);

        double midX = (box.minX + box.maxX) / 2.0;
        double midY = (box.minY + box.maxY) / 2.0;
        double midZ = (box.minZ + box.maxZ) / 2.0;

        // X dimension label
        RenderUtils.renderFloatingText(camera, viewMatrix,
                String.valueOf(dx),
                new Vec3(midX, box.maxY + 0.3, box.maxZ + 0.3));

        // Y dimension label
        RenderUtils.renderFloatingText(camera, viewMatrix,
                String.valueOf(dy),
                new Vec3(box.maxX + 0.3, midY, box.maxZ + 0.3));

        // Z dimension label
        RenderUtils.renderFloatingText(camera, viewMatrix,
                String.valueOf(dz),
                new Vec3(box.maxX + 0.3, box.maxY + 0.3, midZ));
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
