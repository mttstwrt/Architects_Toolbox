package com.ArchitectsToolbox.render;

import com.ArchitectsToolbox.SelectionManager;
import com.ArchitectsToolbox.shapes.EllipsoidMode;
import com.ArchitectsToolbox.shapes.ShapeMode;
import com.ArchitectsToolbox.util.SplineMath;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Calculates which blocks form the surface of various shapes.
 * Used by hollow mode to render individual block outlines.
 *
 * Layer filtering is NOT done here - the HollowShapeRenderer handles it at render time
 * so the cached block list always contains ALL surface blocks.
 */
public class HollowBlockCalculator {

    public static final int MAX_HOLLOW_BLOCKS = 50_000;
    public static final long MAX_VOLUME = 10_000_000L;

    private static boolean lastCalculationLimited = false;
    private static String lastLimitReason = null;

    public static boolean wasLastCalculationLimited() {
        return lastCalculationLimited;
    }

    public static String getLastLimitReason() {
        return lastLimitReason;
    }

    public static List<BlockPos> calculate(List<BlockPos> selection, ShapeMode mode,
                                            ShapeRenderer.RenderConfig config) {
        lastCalculationLimited = false;
        lastLimitReason = null;

        SelectionManager manager = SelectionManager.getInstance();
        if (!manager.hasSelection()) {
            return Collections.emptyList();
        }

        return switch (mode) {
            case RECTANGLE -> calculateRectangle(selection);
            case CYLINDER -> calculateCylinder(selection);
            case ELLIPSOID -> calculateEllipsoid(selection);
            case SPLINE -> calculateSpline(selection);
            case LINE -> calculateLine(selection);
        };
    }

    private static List<BlockPos> calculateRectangle(List<BlockPos> selection) {
        if (selection.size() < 2) return Collections.emptyList();

        BlockPos min = getMin(selection);
        BlockPos max = getMax(selection);

        int sizeX = max.getX() - min.getX() + 1;
        int sizeY = max.getY() - min.getY() + 1;
        int sizeZ = max.getZ() - min.getZ() + 1;

        long estimatedSurface = 2L * ((long) sizeX * sizeY + (long) sizeX * sizeZ + (long) sizeY * sizeZ);
        if (estimatedSurface > MAX_HOLLOW_BLOCKS) {
            lastCalculationLimited = true;
            lastLimitReason = String.format("Selection too large (~%,d surface blocks, max %,d)",
                estimatedSurface, MAX_HOLLOW_BLOCKS);
            return Collections.emptyList();
        }

        List<BlockPos> surface = new ArrayList<>();

        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    boolean onSurface = x == min.getX() || x == max.getX() ||
                                        y == min.getY() || y == max.getY() ||
                                        z == min.getZ() || z == max.getZ();
                    if (onSurface) {
                        surface.add(new BlockPos(x, y, z));
                        if (surface.size() >= MAX_HOLLOW_BLOCKS) {
                            lastCalculationLimited = true;
                            lastLimitReason = String.format("Block limit reached (%,d blocks)", MAX_HOLLOW_BLOCKS);
                            return surface;
                        }
                    }
                }
            }
        }

        return surface;
    }

    private static List<BlockPos> calculateCylinder(List<BlockPos> selection) {
        if (selection.isEmpty()) return Collections.emptyList();

        SelectionManager sm = SelectionManager.getInstance();
        BlockPos center = sm.getCenterBlock();
        if (center == null) return Collections.emptyList();

        double radius = sm.getMaxRadiusXZ() + sm.getCylinderRadiusOffsetBlocks();
        if (radius < 0.5) radius = 0.5;

        int minY = sm.getMinY();
        int maxY = sm.getMaxY();
        int height = maxY - minY + 1;

        long estimatedSurface = (long) (2 * Math.PI * radius * radius + 2 * Math.PI * radius * height);
        if (estimatedSurface > MAX_HOLLOW_BLOCKS) {
            lastCalculationLimited = true;
            lastLimitReason = String.format("Selection too large (~%,d surface blocks, max %,d)",
                estimatedSurface, MAX_HOLLOW_BLOCKS);
            return Collections.emptyList();
        }

        int centerX = center.getX();
        int centerZ = center.getZ();
        int intRadius = (int) Math.ceil(radius);

        List<BlockPos> surface = new ArrayList<>();

        for (int y = minY; y <= maxY; y++) {
            boolean isCapLayer = (y == minY || y == maxY);

            for (int dx = -intRadius; dx <= intRadius; dx++) {
                for (int dz = -intRadius; dz <= intRadius; dz++) {
                    double dist = Math.sqrt(dx * dx + dz * dz);

                    if (isCapLayer) {
                        if (dist <= radius + 0.5) {
                            surface.add(new BlockPos(centerX + dx, y, centerZ + dz));
                            if (surface.size() >= MAX_HOLLOW_BLOCKS) {
                                lastCalculationLimited = true;
                                lastLimitReason = String.format("Block limit reached (%,d blocks)", MAX_HOLLOW_BLOCKS);
                                return surface;
                            }
                        }
                    } else {
                        if (dist >= radius - 0.5 && dist <= radius + 0.5) {
                            surface.add(new BlockPos(centerX + dx, y, centerZ + dz));
                            if (surface.size() >= MAX_HOLLOW_BLOCKS) {
                                lastCalculationLimited = true;
                                lastLimitReason = String.format("Block limit reached (%,d blocks)", MAX_HOLLOW_BLOCKS);
                                return surface;
                            }
                        }
                    }
                }
            }
        }

        return surface;
    }

    private static List<BlockPos> calculateEllipsoid(List<BlockPos> selection) {
        if (selection.size() < 2) return Collections.emptyList();

        SelectionManager sm = SelectionManager.getInstance();
        EllipsoidMode mode = sm.getEllipsoidMode();

        double cx, cy, cz, rx, ry, rz;

        if (mode == EllipsoidMode.FIT_TO_BOX) {
            BlockPos min = getMin(selection);
            BlockPos max = getMax(selection);

            cx = (min.getX() + max.getX() + 1) / 2.0;
            cy = (min.getY() + max.getY() + 1) / 2.0;
            cz = (min.getZ() + max.getZ() + 1) / 2.0;

            rx = (max.getX() - min.getX() + 1) / 2.0;
            ry = (max.getY() - min.getY() + 1) / 2.0;
            rz = (max.getZ() - min.getZ() + 1) / 2.0;
        } else {
            BlockPos center = sm.getCenterBlock();
            if (center == null) return Collections.emptyList();

            cx = center.getX() + 0.5;
            cz = center.getZ() + 0.5;

            double radiusXZ = sm.getMaxRadiusXZ();
            if (radiusXZ < 0.5) radiusXZ = 0.5;
            rx = radiusXZ;
            rz = radiusXZ;

            int minY = sm.getMinY();
            int maxY = sm.getMaxY();
            ry = (maxY - minY + 1) / 2.0;
            cy = (minY + maxY + 1) / 2.0;
        }

        if (rx < 0.5) rx = 0.5;
        if (ry < 0.5) ry = 0.5;
        if (rz < 0.5) rz = 0.5;

        double p = 1.6075;
        double ap = Math.pow(rx, p);
        double bp = Math.pow(ry, p);
        double cp = Math.pow(rz, p);
        long estimatedSurface = (long) (4 * Math.PI * Math.pow((ap * bp + ap * cp + bp * cp) / 3, 1 / p));
        if (estimatedSurface > MAX_HOLLOW_BLOCKS) {
            lastCalculationLimited = true;
            lastLimitReason = String.format("Selection too large (~%,d surface blocks, max %,d)",
                estimatedSurface, MAX_HOLLOW_BLOCKS);
            return Collections.emptyList();
        }

        int maxRadX = (int) Math.ceil(rx) + 1;
        int maxRadY = (int) Math.ceil(ry) + 1;
        int maxRadZ = (int) Math.ceil(rz) + 1;

        List<BlockPos> surface = new ArrayList<>();

        for (int y = -maxRadY; y <= maxRadY; y++) {
            int worldY = (int) Math.floor(cy) + y;

            for (int x = -maxRadX; x <= maxRadX; x++) {
                for (int z = -maxRadZ; z <= maxRadZ; z++) {
                    int worldX = (int) Math.floor(cx) + x;
                    int worldZ = (int) Math.floor(cz) + z;

                    double blockCenterX = worldX + 0.5;
                    double blockCenterY = worldY + 0.5;
                    double blockCenterZ = worldZ + 0.5;

                    double nx = (blockCenterX - cx) / rx;
                    double ny = (blockCenterY - cy) / ry;
                    double nz = (blockCenterZ - cz) / rz;
                    double dist = nx * nx + ny * ny + nz * nz;

                    double threshold = 0.5 / Math.min(Math.min(rx, ry), rz);
                    threshold = Math.max(0.15, Math.min(0.5, threshold));

                    if (Math.abs(dist - 1.0) <= threshold) {
                        surface.add(new BlockPos(worldX, worldY, worldZ));
                        if (surface.size() >= MAX_HOLLOW_BLOCKS) {
                            lastCalculationLimited = true;
                            lastLimitReason = String.format("Block limit reached (%,d blocks)", MAX_HOLLOW_BLOCKS);
                            return surface;
                        }
                    }
                }
            }
        }

        return surface;
    }

    private static final int LINE_SAMPLES_PER_BLOCK = 2;

    private static List<BlockPos> calculateLine(List<BlockPos> selection) {
        if (selection.size() < 2) return Collections.emptyList();

        SelectionManager sm = SelectionManager.getInstance();
        int tubeRadius = sm.getSplineRadius();

        if (tubeRadius == 0) {
            Set<BlockPos> blocks = new LinkedHashSet<>();
            for (int i = 0; i < selection.size() - 1; i++) {
                BlockPos from = selection.get(i);
                BlockPos to = selection.get(i + 1);
                traceLineBlocks(from, to, blocks, MAX_HOLLOW_BLOCKS);
                if (blocks.size() >= MAX_HOLLOW_BLOCKS) {
                    lastCalculationLimited = true;
                    lastLimitReason = String.format("Block limit reached (%,d blocks)", MAX_HOLLOW_BLOCKS);
                    return new ArrayList<>(blocks);
                }
            }
            return new ArrayList<>(blocks);
        }

        double totalLength = 0;
        for (int i = 0; i < selection.size() - 1; i++) {
            BlockPos from = selection.get(i);
            BlockPos to = selection.get(i + 1);
            totalLength += Math.sqrt(from.distSqr(to));
        }

        long estimatedSurface = (long) (2 * Math.PI * tubeRadius * totalLength + 2 * Math.PI * tubeRadius * tubeRadius);
        if (estimatedSurface > MAX_HOLLOW_BLOCKS) {
            lastCalculationLimited = true;
            lastLimitReason = String.format("Selection too large (~%,d surface blocks, max %,d)",
                estimatedSurface, MAX_HOLLOW_BLOCKS);
            return Collections.emptyList();
        }

        Set<BlockPos> allTubeBlocks = new HashSet<>();

        for (int i = 0; i < selection.size() - 1; i++) {
            BlockPos from = selection.get(i);
            BlockPos to = selection.get(i + 1);

            Vec3 start = new Vec3(from.getX() + 0.5, from.getY() + 0.5, from.getZ() + 0.5);
            Vec3 end = new Vec3(to.getX() + 0.5, to.getY() + 0.5, to.getZ() + 0.5);
            Vec3 direction = end.subtract(start).normalize();
            double segmentLength = start.distanceTo(end);

            int samples = Math.max(1, (int) Math.ceil(segmentLength * LINE_SAMPLES_PER_BLOCK));

            for (int s = 0; s <= samples; s++) {
                double t = (double) s / samples;
                Vec3 center = start.add(end.subtract(start).scale(t));

                for (int dx = -tubeRadius; dx <= tubeRadius; dx++) {
                    for (int dy = -tubeRadius; dy <= tubeRadius; dy++) {
                        for (int dz = -tubeRadius; dz <= tubeRadius; dz++) {
                            int blockX = (int) Math.floor(center.x) + dx;
                            int blockY = (int) Math.floor(center.y) + dy;
                            int blockZ = (int) Math.floor(center.z) + dz;

                            Vec3 blockCenter = new Vec3(blockX + 0.5, blockY + 0.5, blockZ + 0.5);
                            Vec3 toBlock = blockCenter.subtract(center);

                            double alongDir = toBlock.dot(direction);
                            Vec3 perpComponent = toBlock.subtract(direction.scale(alongDir));
                            double perpDist = perpComponent.length();

                            if (perpDist <= tubeRadius + 0.5) {
                                allTubeBlocks.add(new BlockPos(blockX, blockY, blockZ));
                            }
                        }
                    }
                }
            }

            if (allTubeBlocks.size() > MAX_VOLUME) {
                lastCalculationLimited = true;
                lastLimitReason = String.format("Volume too large (>%,d blocks)", MAX_VOLUME);
                return Collections.emptyList();
            }
        }

        List<BlockPos> surface = new ArrayList<>();
        for (BlockPos pos : allTubeBlocks) {
            Vec3 blockCenter = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            double minDist = getMinDistanceToLineSegments(blockCenter, selection);

            if (minDist >= tubeRadius - 0.5) {
                surface.add(pos);
                if (surface.size() >= MAX_HOLLOW_BLOCKS) {
                    lastCalculationLimited = true;
                    lastLimitReason = String.format("Block limit reached (%,d blocks)", MAX_HOLLOW_BLOCKS);
                    return surface;
                }
            }
        }

        return surface;
    }

    private static final int SPLINE_SAMPLES_PER_SEGMENT = 32;
    private static final int DISTANCE_SAMPLES_PER_SEGMENT = 16;

    private static List<BlockPos> calculateSpline(List<BlockPos> selection) {
        if (selection.size() < 2) return Collections.emptyList();

        SelectionManager sm = SelectionManager.getInstance();
        int tubeRadius = sm.getSplineRadius();
        Vec3[] points = SplineMath.blockPosListToVec3(selection);
        int n = points.length;

        double estimatedLength = 0;
        for (int i = 0; i < n - 1; i++) {
            estimatedLength += points[i].distanceTo(points[i + 1]);
        }
        estimatedLength *= 1.2;

        if (tubeRadius > 0) {
            long estimatedSurface = (long) (2 * Math.PI * tubeRadius * estimatedLength + 2 * Math.PI * tubeRadius * tubeRadius);
            if (estimatedSurface > MAX_HOLLOW_BLOCKS) {
                lastCalculationLimited = true;
                lastLimitReason = String.format("Selection too large (~%,d surface blocks, max %,d)",
                    estimatedSurface, MAX_HOLLOW_BLOCKS);
                return Collections.emptyList();
            }
        }

        if (tubeRadius == 0) {
            Set<BlockPos> blocks = new LinkedHashSet<>();
            for (int i = 0; i < n - 1; i++) {
                Vec3 p0 = (i == 0) ? SplineMath.extrapolateStart(points[0], points[1]) : points[i - 1];
                Vec3 p1 = points[i];
                Vec3 p2 = points[i + 1];
                Vec3 p3 = (i == n - 2) ? SplineMath.extrapolateEnd(points[n - 2], points[n - 1]) : points[i + 2];

                for (int seg = 0; seg <= SPLINE_SAMPLES_PER_SEGMENT; seg++) {
                    double t = (double) seg / SPLINE_SAMPLES_PER_SEGMENT;
                    Vec3 point = SplineMath.catmullRom(p0, p1, p2, p3, t);

                    int blockX = (int) Math.floor(point.x);
                    int blockY = (int) Math.floor(point.y);
                    int blockZ = (int) Math.floor(point.z);

                    blocks.add(new BlockPos(blockX, blockY, blockZ));
                    if (blocks.size() >= MAX_HOLLOW_BLOCKS) {
                        lastCalculationLimited = true;
                        lastLimitReason = String.format("Block limit reached (%,d blocks)", MAX_HOLLOW_BLOCKS);
                        return new ArrayList<>(blocks);
                    }
                }
            }
            return new ArrayList<>(blocks);
        }

        Set<BlockPos> allTubeBlocks = new HashSet<>();

        for (int i = 0; i < n - 1; i++) {
            Vec3 p0 = (i == 0) ? SplineMath.extrapolateStart(points[0], points[1]) : points[i - 1];
            Vec3 p1 = points[i];
            Vec3 p2 = points[i + 1];
            Vec3 p3 = (i == n - 2) ? SplineMath.extrapolateEnd(points[n - 2], points[n - 1]) : points[i + 2];

            for (int seg = 0; seg <= SPLINE_SAMPLES_PER_SEGMENT; seg++) {
                double t = (double) seg / SPLINE_SAMPLES_PER_SEGMENT;
                Vec3 center = SplineMath.catmullRom(p0, p1, p2, p3, t);
                Vec3 tangent = SplineMath.catmullRomDerivative(p0, p1, p2, p3, t).normalize();

                for (int dx = -tubeRadius; dx <= tubeRadius; dx++) {
                    for (int dy = -tubeRadius; dy <= tubeRadius; dy++) {
                        for (int dz = -tubeRadius; dz <= tubeRadius; dz++) {
                            int blockX = (int) Math.floor(center.x) + dx;
                            int blockY = (int) Math.floor(center.y) + dy;
                            int blockZ = (int) Math.floor(center.z) + dz;

                            Vec3 blockCenter = new Vec3(blockX + 0.5, blockY + 0.5, blockZ + 0.5);
                            Vec3 toBlock = blockCenter.subtract(center);

                            double alongTangent = toBlock.dot(tangent);
                            Vec3 perpComponent = toBlock.subtract(tangent.scale(alongTangent));
                            double perpDist = perpComponent.length();

                            if (perpDist <= tubeRadius + 0.5) {
                                allTubeBlocks.add(new BlockPos(blockX, blockY, blockZ));
                            }
                        }
                    }
                }
            }

            if (allTubeBlocks.size() > MAX_VOLUME) {
                lastCalculationLimited = true;
                lastLimitReason = String.format("Volume too large (>%,d blocks)", MAX_VOLUME);
                return Collections.emptyList();
            }
        }

        List<BlockPos> surface = new ArrayList<>();
        for (BlockPos pos : allTubeBlocks) {
            Vec3 blockCenter = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            double minDist = SplineMath.getMinDistanceToSpline(blockCenter, points, DISTANCE_SAMPLES_PER_SEGMENT);

            if (minDist >= tubeRadius - 0.5) {
                surface.add(pos);
                if (surface.size() >= MAX_HOLLOW_BLOCKS) {
                    lastCalculationLimited = true;
                    lastLimitReason = String.format("Block limit reached (%,d blocks)", MAX_HOLLOW_BLOCKS);
                    return surface;
                }
            }
        }

        return surface;
    }

    private static double getMinDistanceToLineSegments(Vec3 point, List<BlockPos> selection) {
        double minDist = Double.MAX_VALUE;

        for (int i = 0; i < selection.size() - 1; i++) {
            BlockPos from = selection.get(i);
            BlockPos to = selection.get(i + 1);

            Vec3 start = new Vec3(from.getX() + 0.5, from.getY() + 0.5, from.getZ() + 0.5);
            Vec3 end = new Vec3(to.getX() + 0.5, to.getY() + 0.5, to.getZ() + 0.5);

            double dist = pointToSegmentDistance(point, start, end);
            if (dist < minDist) {
                minDist = dist;
            }
        }

        return minDist;
    }

    private static double pointToSegmentDistance(Vec3 point, Vec3 segStart, Vec3 segEnd) {
        Vec3 segment = segEnd.subtract(segStart);
        Vec3 toPoint = point.subtract(segStart);

        double segLengthSq = segment.lengthSqr();
        if (segLengthSq < 0.0001) {
            return point.distanceTo(segStart);
        }

        double t = Math.max(0, Math.min(1, toPoint.dot(segment) / segLengthSq));
        Vec3 projection = segStart.add(segment.scale(t));
        return point.distanceTo(projection);
    }

    private static void traceLineBlocks(BlockPos from, BlockPos to, Set<BlockPos> blocks, int limit) {
        Vec3 start = new Vec3(from.getX() + 0.5, from.getY() + 0.5, from.getZ() + 0.5);
        Vec3 end = new Vec3(to.getX() + 0.5, to.getY() + 0.5, to.getZ() + 0.5);

        double distance = start.distanceTo(end);
        int samples = Math.max(1, (int) Math.ceil(distance * LINE_SAMPLES_PER_BLOCK));

        for (int s = 0; s <= samples; s++) {
            double t = (double) s / samples;
            double x = start.x + (end.x - start.x) * t;
            double y = start.y + (end.y - start.y) * t;
            double z = start.z + (end.z - start.z) * t;

            blocks.add(new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
            if (limit > 0 && blocks.size() >= limit) {
                return;
            }
        }
    }

    private static BlockPos getMin(List<BlockPos> selection) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (BlockPos pos : selection) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
        }
        return new BlockPos(minX, minY, minZ);
    }

    private static BlockPos getMax(List<BlockPos> selection) {
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : selection) {
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new BlockPos(maxX, maxY, maxZ);
    }
}
