package com.ArchitectsToolbox.render;

import com.ArchitectsToolbox.SelectionManager;
import com.ArchitectsToolbox.shapes.EllipsoidMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

public class EllipsoidRenderer implements ShapeRenderer {

    private static final RenderBuffer BUFFER = new RenderBuffer(8192);
    private static final int RING_SEGMENTS = 64;
    private static final int LATITUDE_RINGS = 16;

    @Override
    public void render(Camera camera, Matrix4f viewMatrix, List<BlockPos> selection, RenderConfig config) {
        if (selection.size() < 2) return;

        SelectionManager sm = SelectionManager.getInstance();
        EllipsoidMode mode = sm.getEllipsoidMode();

        double cx, cy, cz, rx, ry, rz;

        if (mode == EllipsoidMode.CENTER_RADIUS && selection.size() >= 2) {
            BlockPos center = selection.get(0);
            BlockPos edge = selection.get(1);

            cx = center.getX() + 0.5;
            cy = center.getY() + 0.5;
            cz = center.getZ() + 0.5;

            rx = Math.abs(edge.getX() - center.getX()) + 0.5;
            ry = Math.abs(edge.getY() - center.getY()) + 0.5;
            rz = Math.abs(edge.getZ() - center.getZ()) + 0.5;
        } else {
            BlockPos min = sm.getMinPos();
            BlockPos max = sm.getMaxPos();
            if (min == null || max == null) return;

            cx = (min.getX() + max.getX() + 1) / 2.0;
            cy = (min.getY() + max.getY() + 1) / 2.0;
            cz = (min.getZ() + max.getZ() + 1) / 2.0;

            rx = (max.getX() - min.getX() + 1) / 2.0;
            ry = (max.getY() - min.getY() + 1) / 2.0;
            rz = (max.getZ() - min.getZ() + 1) / 2.0;
        }

        if (rx <= 0 || ry <= 0 || rz <= 0) return;

        Vec3 camPos = camera.position();

        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(viewMatrix);
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        MultiBufferSource.BufferSource source = BUFFER.createSource();
        VertexConsumer consumer = source.getBuffer(RenderTypes.LINES);
        Matrix4f matrix = poseStack.last().pose();

        float r = config.red(), g = config.green(), b = config.blue(), a = config.alpha();

        // Draw equatorial ring (XZ plane)
        drawEllipseXZ(consumer, matrix, cx, cy, cz, rx, rz, r, g, b, a);

        // Draw front ring (XY plane)
        drawEllipseXY(consumer, matrix, cx, cy, cz, rx, ry, r, g, b, a);

        // Draw side ring (YZ plane)
        drawEllipseYZ(consumer, matrix, cx, cy, cz, ry, rz, r, g, b, a);

        // Draw latitude rings
        for (int i = 1; i < LATITUDE_RINGS; i++) {
            double phi = Math.PI * i / LATITUDE_RINGS - Math.PI / 2.0;
            double y = cy + ry * Math.sin(phi);
            double latScale = Math.cos(phi);
            double latRx = rx * latScale;
            double latRz = rz * latScale;
            if (latRx > 0.01 && latRz > 0.01) {
                drawEllipseXZ(consumer, matrix, cx, y, cz, latRx, latRz, r, g, b, a * 0.3f);
            }
        }

        source.endBatch();
    }

    @Override
    public void renderLabels(Camera camera, Matrix4f viewMatrix, List<BlockPos> selection, RenderConfig config) {
        if (!config.showLabels() || selection.size() < 2) return;

        SelectionManager sm = SelectionManager.getInstance();
        EllipsoidMode mode = sm.getEllipsoidMode();

        double cx, cy, cz, rx, ry, rz;

        if (mode == EllipsoidMode.CENTER_RADIUS && selection.size() >= 2) {
            BlockPos center = selection.get(0);
            BlockPos edge = selection.get(1);

            cx = center.getX() + 0.5;
            cy = center.getY() + 0.5;
            cz = center.getZ() + 0.5;

            rx = Math.abs(edge.getX() - center.getX()) + 0.5;
            ry = Math.abs(edge.getY() - center.getY()) + 0.5;
            rz = Math.abs(edge.getZ() - center.getZ()) + 0.5;
        } else {
            BlockPos min = sm.getMinPos();
            BlockPos max = sm.getMaxPos();
            if (min == null || max == null) return;

            cx = (min.getX() + max.getX() + 1) / 2.0;
            cy = (min.getY() + max.getY() + 1) / 2.0;
            cz = (min.getZ() + max.getZ() + 1) / 2.0;

            rx = (max.getX() - min.getX() + 1) / 2.0;
            ry = (max.getY() - min.getY() + 1) / 2.0;
            rz = (max.getZ() - min.getZ() + 1) / 2.0;
        }

        if (rx <= 0 || ry <= 0 || rz <= 0) return;

        // X radius label
        RenderUtils.renderFloatingText(camera, viewMatrix,
                String.format("Rx: %.1f", rx),
                new Vec3(cx + rx + 0.5, cy, cz));

        // Y radius label
        RenderUtils.renderFloatingText(camera, viewMatrix,
                String.format("Ry: %.1f", ry),
                new Vec3(cx, cy + ry + 0.5, cz));

        // Z radius label
        RenderUtils.renderFloatingText(camera, viewMatrix,
                String.format("Rz: %.1f", rz),
                new Vec3(cx, cy, cz + rz + 0.5));
    }

    private void drawEllipseXZ(VertexConsumer consumer, Matrix4f matrix,
                                double cx, double y, double cz,
                                double rx, double rz,
                                float r, float g, float b, float a) {
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double angle1 = 2.0 * Math.PI * i / RING_SEGMENTS;
            double angle2 = 2.0 * Math.PI * (i + 1) / RING_SEGMENTS;

            double x1 = cx + rx * Math.cos(angle1);
            double z1 = cz + rz * Math.sin(angle1);
            double x2 = cx + rx * Math.cos(angle2);
            double z2 = cz + rz * Math.sin(angle2);

            RenderUtils.drawLine(consumer, matrix, x1, y, z1, x2, y, z2, r, g, b, a);
        }
    }

    private void drawEllipseXY(VertexConsumer consumer, Matrix4f matrix,
                                double cx, double cy, double z,
                                double rx, double ry,
                                float r, float g, float b, float a) {
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double angle1 = 2.0 * Math.PI * i / RING_SEGMENTS;
            double angle2 = 2.0 * Math.PI * (i + 1) / RING_SEGMENTS;

            double x1 = cx + rx * Math.cos(angle1);
            double y1 = cy + ry * Math.sin(angle1);
            double x2 = cx + rx * Math.cos(angle2);
            double y2 = cy + ry * Math.sin(angle2);

            RenderUtils.drawLine(consumer, matrix, x1, y1, z, x2, y2, z, r, g, b, a);
        }
    }

    private void drawEllipseYZ(VertexConsumer consumer, Matrix4f matrix,
                                double x, double cy, double cz,
                                double ry, double rz,
                                float r, float g, float b, float a) {
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double angle1 = 2.0 * Math.PI * i / RING_SEGMENTS;
            double angle2 = 2.0 * Math.PI * (i + 1) / RING_SEGMENTS;

            double y1 = cy + ry * Math.cos(angle1);
            double z1 = cz + rz * Math.sin(angle1);
            double y2 = cy + ry * Math.cos(angle2);
            double z2 = cz + rz * Math.sin(angle2);

            RenderUtils.drawLine(consumer, matrix, x, y1, z1, x, y2, z2, r, g, b, a);
        }
    }
}
