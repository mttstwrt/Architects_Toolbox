package com.ArchitectsToolbox.render;

import com.ArchitectsToolbox.SelectionManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class SplineRenderer implements ShapeRenderer {

    private static final RenderBuffer BUFFER = new RenderBuffer(8192);
    private static final int SPLINE_SEGMENTS_PER_SECTION = 32;

    @Override
    public void render(Camera camera, Matrix4f viewMatrix, List<BlockPos> selection, RenderConfig config) {
        if (selection.size() < 2) return;

        Vec3 camPos = camera.position();

        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(viewMatrix);
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        MultiBufferSource.BufferSource source = BUFFER.createSource();
        VertexConsumer consumer = source.getBuffer(RenderTypes.LINES);
        Matrix4f matrix = poseStack.last().pose();

        float r = config.red(), g = config.green(), b = config.blue(), a = config.alpha();

        List<Vec3> controlPoints = new ArrayList<>();
        for (BlockPos pos : selection) {
            controlPoints.add(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        }

        // Draw spline curve
        List<Vec3> splinePoints = calculateCatmullRomSpline(controlPoints);
        for (int i = 0; i < splinePoints.size() - 1; i++) {
            Vec3 p1 = splinePoints.get(i);
            Vec3 p2 = splinePoints.get(i + 1);
            RenderUtils.drawLine(consumer, matrix, p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, r, g, b, a);
        }

        // Draw control point markers
        for (BlockPos pos : selection) {
            float px = pos.getX() + 0.5f;
            float py = pos.getY() + 0.5f;
            float pz = pos.getZ() + 0.5f;
            float s = 0.15f;
            RenderUtils.drawBox(consumer, matrix, px - s, py - s, pz - s, px + s, py + s, pz + s,
                    1.0f, 1.0f, 0.0f, 1.0f);
        }

        // Draw tube if radius > 0
        int radius = SelectionManager.getInstance().getSplineRadius();
        if (radius > 0) {
            drawSplineTube(consumer, matrix, splinePoints, radius, r, g, b, a * 0.5f);
        }

        source.endBatch();
    }

    @Override
    public void renderLabels(Camera camera, Matrix4f viewMatrix, List<BlockPos> selection, RenderConfig config) {
        if (!config.showLabels() || selection.size() < 2) return;

        List<Vec3> controlPoints = new ArrayList<>();
        for (BlockPos pos : selection) {
            controlPoints.add(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        }

        List<Vec3> splinePoints = calculateCatmullRomSpline(controlPoints);
        double totalLength = 0;
        for (int i = 0; i < splinePoints.size() - 1; i++) {
            totalLength += splinePoints.get(i).distanceTo(splinePoints.get(i + 1));
        }

        // Control point labels
        for (int i = 0; i < selection.size(); i++) {
            BlockPos pos = selection.get(i);
            RenderUtils.renderFloatingText(camera, viewMatrix,
                    "P" + (i + 1),
                    new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5),
                    1.0f, 1.0f, 0.0f);
        }

        // Total length label at midpoint
        if (!splinePoints.isEmpty()) {
            Vec3 midPoint = splinePoints.get(splinePoints.size() / 2);
            RenderUtils.renderFloatingText(camera, viewMatrix,
                    String.format("Length: %.1f", totalLength),
                    midPoint.add(0, 1.0, 0),
                    0.3f, 1.0f, 0.3f);
        }

        // Segment length labels
        for (int i = 0; i < selection.size() - 1; i++) {
            Vec3 start = controlPoints.get(i);
            Vec3 end = controlPoints.get(i + 1);
            Vec3 mid = start.add(end).scale(0.5);
            double segDist = start.distanceTo(end);
            RenderUtils.renderFloatingText(camera, viewMatrix,
                    String.format("%.1f", segDist),
                    mid.add(0, 0.5, 0));
        }
    }

    private List<Vec3> calculateCatmullRomSpline(List<Vec3> controlPoints) {
        List<Vec3> result = new ArrayList<>();
        if (controlPoints.size() < 2) return result;

        for (int i = 0; i < controlPoints.size() - 1; i++) {
            Vec3 p0 = controlPoints.get(Math.max(0, i - 1));
            Vec3 p1 = controlPoints.get(i);
            Vec3 p2 = controlPoints.get(i + 1);
            Vec3 p3 = controlPoints.get(Math.min(controlPoints.size() - 1, i + 2));

            for (int j = 0; j <= SPLINE_SEGMENTS_PER_SECTION; j++) {
                double t = (double) j / SPLINE_SEGMENTS_PER_SECTION;
                Vec3 point = catmullRom(p0, p1, p2, p3, t);
                result.add(point);
            }
        }

        return result;
    }

    private Vec3 catmullRom(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;

        double x = 0.5 * ((2 * p1.x) +
                (-p0.x + p2.x) * t +
                (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 +
                (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3);

        double y = 0.5 * ((2 * p1.y) +
                (-p0.y + p2.y) * t +
                (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 +
                (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3);

        double z = 0.5 * ((2 * p1.z) +
                (-p0.z + p2.z) * t +
                (2 * p0.z - 5 * p1.z + 4 * p2.z - p3.z) * t2 +
                (-p0.z + 3 * p1.z - 3 * p2.z + p3.z) * t3);

        return new Vec3(x, y, z);
    }

    private void drawSplineTube(VertexConsumer consumer, Matrix4f matrix,
                                 List<Vec3> splinePoints, int radius,
                                 float r, float g, float b, float a) {
        if (splinePoints.size() < 2) return;

        int circleSegments = 16;
        double tubeRadius = radius;

        List<Vec3[]> rings = new ArrayList<>();

        for (int i = 0; i < splinePoints.size(); i++) {
            Vec3 point = splinePoints.get(i);
            Vec3 tangent;

            if (i == 0) {
                tangent = splinePoints.get(1).subtract(splinePoints.get(0)).normalize();
            } else if (i == splinePoints.size() - 1) {
                tangent = splinePoints.get(i).subtract(splinePoints.get(i - 1)).normalize();
            } else {
                tangent = splinePoints.get(i + 1).subtract(splinePoints.get(i - 1)).normalize();
            }

            Vec3 up = Math.abs(tangent.dot(new Vec3(0, 1, 0))) > 0.99
                    ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 right = tangent.cross(up).normalize();
            Vec3 actualUp = right.cross(tangent).normalize();

            Vec3[] ring = new Vec3[circleSegments];
            for (int j = 0; j < circleSegments; j++) {
                double angle = 2.0 * Math.PI * j / circleSegments;
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);
                ring[j] = point.add(right.scale(cos * tubeRadius)).add(actualUp.scale(sin * tubeRadius));
            }
            rings.add(ring);
        }

        // Draw ring outlines every N points
        int ringInterval = Math.max(1, splinePoints.size() / 20);
        for (int i = 0; i < rings.size(); i += ringInterval) {
            Vec3[] ring = rings.get(i);
            for (int j = 0; j < circleSegments; j++) {
                Vec3 v1 = ring[j];
                Vec3 v2 = ring[(j + 1) % circleSegments];
                RenderUtils.drawLine(consumer, matrix, v1.x, v1.y, v1.z, v2.x, v2.y, v2.z, r, g, b, a);
            }
        }

        // Draw longitudinal lines
        int longiLines = 4;
        for (int j = 0; j < circleSegments; j += circleSegments / longiLines) {
            for (int i = 0; i < rings.size() - 1; i++) {
                Vec3 v1 = rings.get(i)[j];
                Vec3 v2 = rings.get(i + 1)[j];
                RenderUtils.drawLine(consumer, matrix, v1.x, v1.y, v1.z, v2.x, v2.y, v2.z, r, g, b, a);
            }
        }
    }
}
