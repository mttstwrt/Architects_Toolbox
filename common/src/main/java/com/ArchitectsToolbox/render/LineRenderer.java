package com.ArchitectsToolbox.render;

import com.ArchitectsToolbox.SelectionManager;
import com.ArchitectsToolbox.util.SplineMath;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

public class LineRenderer implements ShapeRenderer {

    private static final ByteBufferBuilder BUFFER = new ByteBufferBuilder(8192);
    private static final int TUBE_CIRCLE_SEGMENTS = 16;

    @Override
    public void render(Camera camera, Matrix4f viewMatrix, List<BlockPos> selection, RenderConfig config) {
        if (selection.size() < 2) return;

        Vec3 camPos = camera.getPosition();

        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(viewMatrix);
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        MultiBufferSource.BufferSource source = MultiBufferSource.immediate(BUFFER);
        VertexConsumer consumer = source.getBuffer(RenderType.lines());
        Matrix4f matrix = poseStack.last().pose();

        float r = config.red(), g = config.green(), b = config.blue(), a = config.alpha();

        int tubeRadius = SelectionManager.getInstance().getSplineRadius();

        // Draw lines between consecutive points
        for (int i = 0; i < selection.size() - 1; i++) {
            BlockPos pos1 = selection.get(i);
            BlockPos pos2 = selection.get(i + 1);
            Vec3 from = new Vec3(pos1.getX() + 0.5, pos1.getY() + 0.5, pos1.getZ() + 0.5);
            Vec3 to = new Vec3(pos2.getX() + 0.5, pos2.getY() + 0.5, pos2.getZ() + 0.5);

            RenderUtils.drawLine(consumer, matrix, from.x, from.y, from.z, to.x, to.y, to.z, r, g, b, a);

            // Draw tube if radius > 0
            if (tubeRadius > 0) {
                drawTubeForSegment(consumer, matrix, from, to, tubeRadius, r, g, b, a);
            }
        }

        // Draw point markers
        for (BlockPos pos : selection) {
            float px = pos.getX() + 0.5f;
            float py = pos.getY() + 0.5f;
            float pz = pos.getZ() + 0.5f;
            float s = 0.15f;
            RenderUtils.drawLine(consumer, matrix, px - s, py, pz, px + s, py, pz, r, g, b, a);
            RenderUtils.drawLine(consumer, matrix, px, py - s, pz, px, py + s, pz, r, g, b, a);
            RenderUtils.drawLine(consumer, matrix, px, py, pz - s, px, py, pz + s, r, g, b, a);
        }

        // Draw angle arcs between consecutive segments
        if (selection.size() >= 3) {
            for (int i = 1; i < selection.size() - 1; i++) {
                drawAngleArc(consumer, matrix, selection.get(i - 1), selection.get(i), selection.get(i + 1),
                        0.0f, 1.0f, 1.0f, a);
            }
        }

        source.endBatch();
    }

    @Override
    public void renderLabels(Camera camera, Matrix4f viewMatrix, List<BlockPos> selection, RenderConfig config) {
        if (!config.showLabels() || selection.size() < 2) return;

        double totalLength = 0;

        // Segment labels
        for (int i = 0; i < selection.size() - 1; i++) {
            BlockPos pos1 = selection.get(i);
            BlockPos pos2 = selection.get(i + 1);
            Vec3 v1 = new Vec3(pos1.getX() + 0.5, pos1.getY() + 0.5, pos1.getZ() + 0.5);
            Vec3 v2 = new Vec3(pos2.getX() + 0.5, pos2.getY() + 0.5, pos2.getZ() + 0.5);
            double dist = v1.distanceTo(v2);
            totalLength += dist;

            // Segment distance label at midpoint
            Vec3 mid = v1.add(v2).scale(0.5);
            RenderUtils.renderFloatingText(camera, viewMatrix,
                    String.format("%.1f", dist),
                    mid.add(0, 0.3, 0));

            // Component distance labels (only if more than one segment)
            if (config.showLabels() && selection.size() > 2) {
                int dx = Math.abs(pos2.getX() - pos1.getX());
                int dy = Math.abs(pos2.getY() - pos1.getY());
                int dz = Math.abs(pos2.getZ() - pos1.getZ());
                if (dx > 0 || dy > 0 || dz > 0) {
                    StringBuilder components = new StringBuilder();
                    if (dx > 0) components.append("X:").append(dx).append(" ");
                    if (dy > 0) components.append("Y:").append(dy).append(" ");
                    if (dz > 0) components.append("Z:").append(dz);
                    RenderUtils.renderFloatingText(camera, viewMatrix,
                            components.toString().trim(),
                            mid.add(0, 1.0, 0),
                            0.7f, 0.7f, 0.7f);
                }
            }
        }

        // Point labels
        for (int i = 0; i < selection.size(); i++) {
            BlockPos pos = selection.get(i);
            RenderUtils.renderFloatingText(camera, viewMatrix,
                    "P" + (i + 1),
                    new Vec3(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5),
                    1.0f, 1.0f, 0.0f);
        }

        // Angle labels at vertices
        if (selection.size() >= 3) {
            for (int i = 1; i < selection.size() - 1; i++) {
                BlockPos prev = selection.get(i - 1);
                BlockPos curr = selection.get(i);
                BlockPos next = selection.get(i + 1);

                Vec3 vPrev = new Vec3(prev.getX() + 0.5, prev.getY() + 0.5, prev.getZ() + 0.5);
                Vec3 vCurr = new Vec3(curr.getX() + 0.5, curr.getY() + 0.5, curr.getZ() + 0.5);
                Vec3 vNext = new Vec3(next.getX() + 0.5, next.getY() + 0.5, next.getZ() + 0.5);

                Vec3 d1 = vPrev.subtract(vCurr).normalize();
                Vec3 d2 = vNext.subtract(vCurr).normalize();

                double dotProduct = d1.dot(d2);
                dotProduct = Math.max(-1.0, Math.min(1.0, dotProduct));
                double angle = Math.toDegrees(Math.acos(dotProduct));

                RenderUtils.renderFloatingText(camera, viewMatrix,
                        String.format("%.1f\u00b0", angle),
                        vCurr.add(0, -0.5, 0),
                        0.0f, 1.0f, 1.0f);
            }
        }

        // Total length label (only if multiple segments)
        if (selection.size() > 2) {
            BlockPos lastPos = selection.get(selection.size() - 1);
            RenderUtils.renderFloatingText(camera, viewMatrix,
                    String.format("Total: %.1f", totalLength),
                    new Vec3(lastPos.getX() + 0.5, lastPos.getY() + 2.0, lastPos.getZ() + 0.5),
                    0.3f, 1.0f, 0.3f);
        }
    }

    private void drawTubeForSegment(VertexConsumer consumer, Matrix4f matrix, Vec3 from, Vec3 to,
                                     int radius, float r, float g, float b, float a) {
        Vec3 direction = to.subtract(from).normalize();
        double length = from.distanceTo(to);

        // Draw circles at regular intervals along the line
        int circleCount = Math.max(2, (int) Math.ceil(length / 2.0));
        for (int i = 0; i <= circleCount; i++) {
            double t = (double) i / circleCount;
            Vec3 center = from.add(to.subtract(from).scale(t));
            drawTubeCircle(consumer, matrix, center, direction, radius, r, g, b, a);
        }

        // Draw longitudinal lines connecting the circles
        drawTubeLongitudinalLines(consumer, matrix, from, to, direction, radius, r, g, b, a);
    }

    private void drawTubeCircle(VertexConsumer consumer, Matrix4f matrix, Vec3 center, Vec3 direction,
                                 int radius, float r, float g, float b, float a) {
        Vec3 perp1 = SplineMath.findPerpendicular(direction);
        Vec3 perp2 = direction.cross(perp1).normalize();

        Vec3[] circlePoints = new Vec3[TUBE_CIRCLE_SEGMENTS];
        for (int i = 0; i < TUBE_CIRCLE_SEGMENTS; i++) {
            double angle = 2 * Math.PI * i / TUBE_CIRCLE_SEGMENTS;
            double cos = Math.cos(angle) * radius;
            double sin = Math.sin(angle) * radius;
            circlePoints[i] = center.add(perp1.scale(cos)).add(perp2.scale(sin));
        }

        for (int i = 0; i < TUBE_CIRCLE_SEGMENTS; i++) {
            Vec3 fromPt = circlePoints[i];
            Vec3 toPt = circlePoints[(i + 1) % TUBE_CIRCLE_SEGMENTS];
            RenderUtils.drawLine(consumer, matrix,
                    fromPt.x, fromPt.y, fromPt.z,
                    toPt.x, toPt.y, toPt.z,
                    r, g, b, a);
        }
    }

    private void drawTubeLongitudinalLines(VertexConsumer consumer, Matrix4f matrix,
                                            Vec3 from, Vec3 to, Vec3 direction, int radius,
                                            float r, float g, float b, float a) {
        Vec3 perp1 = SplineMath.findPerpendicular(direction);
        Vec3 perp2 = direction.cross(perp1).normalize();

        int longitudinalCount = 8;
        for (int i = 0; i < longitudinalCount; i++) {
            double angle = 2 * Math.PI * i / longitudinalCount;
            double cos = Math.cos(angle) * radius;
            double sin = Math.sin(angle) * radius;

            Vec3 offset = perp1.scale(cos).add(perp2.scale(sin));
            Vec3 lineStart = from.add(offset);
            Vec3 lineEnd = to.add(offset);

            RenderUtils.drawLine(consumer, matrix,
                    lineStart.x, lineStart.y, lineStart.z,
                    lineEnd.x, lineEnd.y, lineEnd.z,
                    r, g, b, a);
        }
    }

    private void drawAngleArc(VertexConsumer consumer, Matrix4f matrix,
                               BlockPos prevPos, BlockPos currPos, BlockPos nextPos,
                               float r, float g, float b, float a) {
        Vec3 prev = new Vec3(prevPos.getX() + 0.5, prevPos.getY() + 0.5, prevPos.getZ() + 0.5);
        Vec3 curr = new Vec3(currPos.getX() + 0.5, currPos.getY() + 0.5, currPos.getZ() + 0.5);
        Vec3 next = new Vec3(nextPos.getX() + 0.5, nextPos.getY() + 0.5, nextPos.getZ() + 0.5);

        Vec3 d1 = prev.subtract(curr).normalize();
        Vec3 d2 = next.subtract(curr).normalize();

        double dot = d1.dot(d2);
        dot = Math.max(-1.0, Math.min(1.0, dot));
        double angle = Math.acos(dot);

        if (angle < 0.01 || angle > Math.PI - 0.01) return;

        Vec3 normal = d1.cross(d2);
        if (normal.lengthSqr() < 1e-8) return;
        normal = normal.normalize();

        double arcRadius = 0.5;
        int arcSegments = 16;

        Vec3 startDir = d1.scale(arcRadius);
        for (int i = 0; i < arcSegments; i++) {
            double t1 = angle * i / arcSegments;
            double t2 = angle * (i + 1) / arcSegments;

            Vec3 p1 = rotateAround(startDir, normal, t1).add(curr);
            Vec3 p2 = rotateAround(startDir, normal, t2).add(curr);

            RenderUtils.drawLine(consumer, matrix, p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, r, g, b, a);
        }
    }

    private Vec3 rotateAround(Vec3 vec, Vec3 axis, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double dot = vec.dot(axis);

        double x = vec.x * cos + axis.cross(vec).x * sin + axis.x * dot * (1 - cos);
        double y = vec.y * cos + axis.cross(vec).y * sin + axis.y * dot * (1 - cos);
        double z = vec.z * cos + axis.cross(vec).z * sin + axis.z * dot * (1 - cos);

        return new Vec3(x, y, z);
    }
}
