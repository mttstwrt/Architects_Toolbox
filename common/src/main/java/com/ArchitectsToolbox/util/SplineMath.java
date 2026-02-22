package com.ArchitectsToolbox.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class SplineMath {

    private SplineMath() {}

    public static Vec3 catmullRom(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
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

    public static Vec3 catmullRomDerivative(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        double t2 = t * t;

        double dx = 0.5 * ((-p0.x + p2.x) +
                          2 * (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t +
                          3 * (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t2);

        double dy = 0.5 * ((-p0.y + p2.y) +
                          2 * (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t +
                          3 * (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t2);

        double dz = 0.5 * ((-p0.z + p2.z) +
                          2 * (2 * p0.z - 5 * p1.z + 4 * p2.z - p3.z) * t +
                          3 * (-p0.z + 3 * p1.z - 3 * p2.z + p3.z) * t2);

        return new Vec3(dx, dy, dz);
    }

    public static Vec3 extrapolateStart(Vec3 p0, Vec3 p1) {
        return new Vec3(
            2 * p0.x - p1.x,
            2 * p0.y - p1.y,
            2 * p0.z - p1.z
        );
    }

    public static Vec3 extrapolateEnd(Vec3 pn1, Vec3 pn) {
        return new Vec3(
            2 * pn.x - pn1.x,
            2 * pn.y - pn1.y,
            2 * pn.z - pn1.z
        );
    }

    public static Vec3 getSplinePoint(Vec3[] points, double t) {
        if (points.length < 2) return points[0];
        if (points.length == 2) {
            return points[0].lerp(points[1], t);
        }

        int n = points.length;
        double scaledT = t * (n - 1);
        int i = (int) Math.floor(scaledT);
        if (i >= n - 1) i = n - 2;
        double localT = scaledT - i;

        Vec3 p0 = (i == 0) ? extrapolateStart(points[0], points[1]) : points[i - 1];
        Vec3 p1 = points[i];
        Vec3 p2 = points[i + 1];
        Vec3 p3 = (i == n - 2) ? extrapolateEnd(points[n - 2], points[n - 1]) : points[i + 2];

        return catmullRom(p0, p1, p2, p3, localT);
    }

    public static double getMinDistanceToSpline(Vec3 point, Vec3[] controlPoints, int samplesPerSegment) {
        double minDist = Double.MAX_VALUE;
        int n = controlPoints.length;

        for (int i = 0; i < n - 1; i++) {
            Vec3 p0 = (i == 0) ? extrapolateStart(controlPoints[0], controlPoints[1]) : controlPoints[i - 1];
            Vec3 p1 = controlPoints[i];
            Vec3 p2 = controlPoints[i + 1];
            Vec3 p3 = (i == n - 2) ? extrapolateEnd(controlPoints[n - 2], controlPoints[n - 1]) : controlPoints[i + 2];

            for (int seg = 0; seg <= samplesPerSegment; seg++) {
                double t = (double) seg / samplesPerSegment;
                Vec3 splinePoint = catmullRom(p0, p1, p2, p3, t);
                double dist = point.distanceTo(splinePoint);
                if (dist < minDist) {
                    minDist = dist;
                }
            }
        }

        return minDist;
    }

    public static Vec3[] blockPosListToVec3(List<BlockPos> positions) {
        Vec3[] points = new Vec3[positions.size()];
        for (int i = 0; i < positions.size(); i++) {
            BlockPos pos = positions.get(i);
            points[i] = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        }
        return points;
    }

    public static double calculateSplineLength(Vec3[] points, int segmentsPerSpan) {
        if (points.length < 2) return 0;

        double totalLength = 0;
        int totalSegments = (points.length - 1) * segmentsPerSpan;
        Vec3 prevPoint = points[0];

        for (int seg = 1; seg <= totalSegments; seg++) {
            double t = (double) seg / totalSegments;
            Vec3 currPoint = getSplinePoint(points, t);
            totalLength += prevPoint.distanceTo(currPoint);
            prevPoint = currPoint;
        }

        return totalLength;
    }

    public static Vec3 findPerpendicular(Vec3 direction) {
        Vec3 up = new Vec3(0, 1, 0);
        if (Math.abs(direction.dot(up)) > 0.99) {
            up = new Vec3(1, 0, 0);
        }
        return direction.cross(up).normalize();
    }
}
