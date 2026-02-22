package com.ArchitectsToolbox.render;

import com.ArchitectsToolbox.SelectionManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

public class CylinderRenderer implements ShapeRenderer {

    private static final RenderBuffer BUFFER = new RenderBuffer(4096);
    private static final int CIRCLE_SEGMENTS = 64;

    @Override
    public void render(Camera camera, Matrix4f viewMatrix, List<BlockPos> selection, RenderConfig config) {
        if (selection.isEmpty()) return;

        SelectionManager sm = SelectionManager.getInstance();
        BlockPos center = sm.getCenterBlock();
        if (center == null) return;

        double baseRadius = sm.getMaxRadiusXZ() + sm.getCylinderRadiusOffsetBlocks();
        if (baseRadius <= 0 && selection.size() >= 2) {
            baseRadius = 1.0;
        }
        if (baseRadius <= 0) return;

        int minY = sm.getMinY();
        int maxY = sm.getMaxY();
        double height = maxY - minY + 1;

        double cx = center.getX() + 0.5;
        double cz = center.getZ() + 0.5;

        Vec3 camPos = camera.getPosition();

        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(viewMatrix);
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        MultiBufferSource.BufferSource source = BUFFER.createSource();
        VertexConsumer consumer = source.getBuffer(RenderType.lines());
        Matrix4f matrix = poseStack.last().pose();

        float r = config.red(), g = config.green(), b = config.blue(), a = config.alpha();

        // Draw top and bottom circles
        for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
            double angle1 = 2.0 * Math.PI * i / CIRCLE_SEGMENTS;
            double angle2 = 2.0 * Math.PI * (i + 1) / CIRCLE_SEGMENTS;

            double x1 = cx + baseRadius * Math.cos(angle1);
            double z1 = cz + baseRadius * Math.sin(angle1);
            double x2 = cx + baseRadius * Math.cos(angle2);
            double z2 = cz + baseRadius * Math.sin(angle2);

            // Bottom circle
            RenderUtils.drawLine(consumer, matrix, x1, minY, z1, x2, minY, z2, r, g, b, a);
            // Top circle
            RenderUtils.drawLine(consumer, matrix, x1, minY + height, z1, x2, minY + height, z2, r, g, b, a);
        }

        // Draw vertical lines at cardinal directions
        int verticalLines = 8;
        for (int i = 0; i < verticalLines; i++) {
            double angle = 2.0 * Math.PI * i / verticalLines;
            double x = cx + baseRadius * Math.cos(angle);
            double z = cz + baseRadius * Math.sin(angle);
            RenderUtils.drawLine(consumer, matrix, x, minY, z, x, minY + height, z, r, g, b, a);
        }

        source.endBatch();
    }

    @Override
    public void renderLabels(Camera camera, Matrix4f viewMatrix, List<BlockPos> selection, RenderConfig config) {
        if (!config.showLabels() || selection.isEmpty()) return;

        SelectionManager sm = SelectionManager.getInstance();
        BlockPos center = sm.getCenterBlock();
        if (center == null) return;

        double radius = sm.getMaxRadiusXZ() + sm.getCylinderRadiusOffsetBlocks();
        if (radius <= 0 && selection.size() >= 2) {
            radius = 1.0;
        }
        if (radius <= 0) return;

        int minY = sm.getMinY();
        int maxY = sm.getMaxY();
        double height = maxY - minY + 1;

        double cx = center.getX() + 0.5;
        double cz = center.getZ() + 0.5;

        // Radius label
        RenderUtils.renderFloatingText(camera, viewMatrix,
                String.format("R: %.1f", radius),
                new Vec3(cx + radius / 2.0, minY + height / 2.0, cz));

        // Height label
        RenderUtils.renderFloatingText(camera, viewMatrix,
                String.format("H: %.0f", height),
                new Vec3(cx + radius + 0.5, minY + height / 2.0, cz));

        // Diameter label
        RenderUtils.renderFloatingText(camera, viewMatrix,
                String.format("D: %.1f", radius * 2),
                new Vec3(cx, minY - 0.5, cz));
    }
}
