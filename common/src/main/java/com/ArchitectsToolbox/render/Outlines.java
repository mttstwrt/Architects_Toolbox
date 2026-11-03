package com.ArchitectsToolbox.render;

import com.ArchitectsToolbox.geometry.BlockBox;
import com.ArchitectsToolbox.geometry.CylinderShape;
import com.ArchitectsToolbox.geometry.EllipsoidShape;
import com.ArchitectsToolbox.geometry.PathShape;
import com.ArchitectsToolbox.geometry.Shape;
import com.ArchitectsToolbox.geometry.Spline;
import com.ArchitectsToolbox.measure.Measurement;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Each shape's own outline and labels, as 1.0.0 drew them: box edges, cylinder ends and sides, ellipsoid rings,
 * path segments with markers and angles, tubes, rectangle subdivisions. Stateless; safe on any thread.
 */
final class Outlines {
    static final int SUBDIVISION_BLUE = 0xFF4D80FF;
    static final int POINT_YELLOW = 0xFFFFFF00;
    static final int ANGLE_CYAN = 0xFF00FFFF;
    static final int TOTAL_GREEN = 0xFF4DFF4D;
    static final int DELTA_GREY = 0xFFB3B3B3;
    static final int WHITE = 0xFFFFFFFF;
    private static final int RING_SEGMENTS = 64;
    private static final int LATITUDE_RINGS = 16;
    private static final int TUBE_SEGMENTS = 16;
    private static final int TUBE_SIDES = 8;
    private static final double MARKER = 0.25;

    /**
     * Where an outline goes.
     *
     * @param lines   the shape's lines, or null to leave them out (the hollow and layer views draw their own)
     * @param labels  the labels, or null for none
     * @param colour  the colour of the shape's lines
     */
    record Sink(LineMesh.Builder lines, List<WorldLabel> labels, int colour) {
        void line(Vec3 a, Vec3 b, int argb) {
            if (lines != null) {
                lines.line(a.x, a.y, a.z, b.x, b.y, b.z, argb);
            }
        }

        void label(Vec3 pos, Component text, int argb) {
            if (labels != null) {
                labels.add(new WorldLabel(pos, text, argb));
            }
        }
    }

    private Outlines() {
    }

    /** Adds the measurement's outline; {@code subdivisions} is false where another view replaces them. */
    static void add(Measurement measurement, Shape shape, Sink sink, boolean subdivisions) {
        switch (measurement.shape()) {
            case RECTANGLE -> rectangle(shape.bounds(), subdivisions ? measurement.options().subdivisions() : 0, sink);
            case CYLINDER -> cylinder((CylinderShape) shape, sink);
            case ELLIPSOID -> ellipsoid((EllipsoidShape) shape, sink);
            case LINE -> line(measurement.points(), (PathShape) shape, sink);
            case SPLINE -> spline(measurement.centres(), (PathShape) shape, sink);
        }
    }

    private static void rectangle(BlockBox box, int parts, Sink sink) {
        double x0 = box.minX(), y0 = box.minY(), z0 = box.minZ();
        double x1 = box.maxX() + 1, y1 = box.maxY() + 1, z1 = box.maxZ() + 1;
        if (sink.lines() != null) {
            sink.lines().box(x0, y0, z0, x1, y1, z1, sink.colour());
        }
        sink.label(new Vec3((x0 + x1) / 2, y1 + 0.3, z0), Component.translatable("architects_toolbox.label.size_x", box.sizeX()), WHITE);
        sink.label(new Vec3(x0 - 0.3, (y0 + y1) / 2, z0 - 0.3), Component.translatable("architects_toolbox.label.size_y", box.sizeY()), WHITE);
        sink.label(new Vec3(x0, y1 + 0.3, (z0 + z1) / 2), Component.translatable("architects_toolbox.label.size_z", box.sizeZ()), WHITE);
        if (parts < 2) {
            return;
        }
        for (int k = 1; k < parts; k++) {
            double x = x0 + (x1 - x0) * k / parts;
            double y = y0 + (y1 - y0) * k / parts;
            double z = z0 + (z1 - z0) * k / parts;
            ring(sink, new Vec3(x, y0, z0), new Vec3(x, y1, z0), new Vec3(x, y1, z1), new Vec3(x, y0, z1));
            ring(sink, new Vec3(x0, y, z0), new Vec3(x1, y, z0), new Vec3(x1, y, z1), new Vec3(x0, y, z1));
            ring(sink, new Vec3(x0, y0, z), new Vec3(x1, y0, z), new Vec3(x1, y1, z), new Vec3(x0, y1, z));
        }
        sink.label(new Vec3(x0 + (x1 - x0) / parts / 2, y0 - 0.3, z0), Component.literal(number((x1 - x0) / parts)), SUBDIVISION_BLUE);
        sink.label(new Vec3(x1 + 0.3, y0 + (y1 - y0) / parts / 2, z0), Component.literal(number((y1 - y0) / parts)), SUBDIVISION_BLUE);
        sink.label(new Vec3(x1, y0 - 0.3, z0 + (z1 - z0) / parts / 2), Component.literal(number((z1 - z0) / parts)), SUBDIVISION_BLUE);
    }

    private static void ring(Sink sink, Vec3 a, Vec3 b, Vec3 c, Vec3 d) {
        sink.line(a, b, SUBDIVISION_BLUE);
        sink.line(b, c, SUBDIVISION_BLUE);
        sink.line(c, d, SUBDIVISION_BLUE);
        sink.line(d, a, SUBDIVISION_BLUE);
    }

    private static void cylinder(CylinderShape cylinder, Sink sink) {
        BlockBox box = cylinder.bounds();
        Direction.Axis axis = cylinder.axis();
        double start = box.min().get(axis);
        double end = box.max().get(axis) + 1;
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double a1 = 2 * Math.PI * i / RING_SEGMENTS;
            double a2 = 2 * Math.PI * (i + 1) / RING_SEGMENTS;
            sink.line(onCylinder(cylinder, a1, start), onCylinder(cylinder, a2, start), sink.colour());
            sink.line(onCylinder(cylinder, a1, end), onCylinder(cylinder, a2, end), sink.colour());
        }
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * i / 4;
            sink.line(onCylinder(cylinder, angle, start), onCylinder(cylinder, angle, end), sink.colour());
        }
        double du = cylinder.radiusU() * 2;
        double dv = cylinder.radiusV() * 2;
        Component diameter = du == dv
                ? Component.translatable("architects_toolbox.label.diameter", number(du), number(du / 2))
                : Component.translatable("architects_toolbox.label.diameters", number(du), number(dv), number(du / 2), number(dv / 2));
        sink.label(onCylinder(cylinder, 0, end).add(axisVector(axis, 0.5)), diameter, WHITE);
        sink.label(onCylinder(cylinder, Math.PI, (start + end) / 2), Component.translatable("architects_toolbox.label.length", number(end - start)), WHITE);
    }

    /** The point at an angle round the cylinder's rim, at a distance along its axis. */
    private static Vec3 onCylinder(CylinderShape cylinder, double angle, double along) {
        double u = cylinder.centreU() + Math.cos(angle) * cylinder.radiusU();
        double v = cylinder.centreV() + Math.sin(angle) * cylinder.radiusV();
        return switch (cylinder.axis()) {
            case X -> new Vec3(along, u, v);
            case Y -> new Vec3(u, along, v);
            case Z -> new Vec3(u, v, along);
        };
    }

    private static Vec3 axisVector(Direction.Axis axis, double length) {
        return switch (axis) {
            case X -> new Vec3(length, 0, 0);
            case Y -> new Vec3(0, length, 0);
            case Z -> new Vec3(0, 0, length);
        };
    }

    private static void ellipsoid(EllipsoidShape e, Sink sink) {
        Vec3 centre = new Vec3(e.centreX(), e.centreY(), e.centreZ());
        for (int ring = 1; ring < LATITUDE_RINGS; ring++) {
            double phi = Math.PI * ring / LATITUDE_RINGS - Math.PI / 2;
            double y = centre.y + Math.sin(phi) * e.radiusY();
            double scale = Math.cos(phi);
            for (int i = 0; i < RING_SEGMENTS; i++) {
                double a1 = 2 * Math.PI * i / RING_SEGMENTS;
                double a2 = 2 * Math.PI * (i + 1) / RING_SEGMENTS;
                sink.line(new Vec3(centre.x + Math.cos(a1) * e.radiusX() * scale, y, centre.z + Math.sin(a1) * e.radiusZ() * scale),
                        new Vec3(centre.x + Math.cos(a2) * e.radiusX() * scale, y, centre.z + Math.sin(a2) * e.radiusZ() * scale), sink.colour());
            }
        }
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double a1 = 2 * Math.PI * i / RING_SEGMENTS;
            double a2 = 2 * Math.PI * (i + 1) / RING_SEGMENTS;
            sink.line(new Vec3(centre.x + Math.cos(a1) * e.radiusX(), centre.y + Math.sin(a1) * e.radiusY(), centre.z),
                    new Vec3(centre.x + Math.cos(a2) * e.radiusX(), centre.y + Math.sin(a2) * e.radiusY(), centre.z), sink.colour());
            sink.line(new Vec3(centre.x, centre.y + Math.sin(a1) * e.radiusY(), centre.z + Math.cos(a1) * e.radiusZ()),
                    new Vec3(centre.x, centre.y + Math.sin(a2) * e.radiusY(), centre.z + Math.cos(a2) * e.radiusZ()), sink.colour());
        }
        sink.label(centre.add(e.radiusX() + 0.3, 0, 0), Component.translatable("architects_toolbox.label.radius_x", number(e.radiusX())), WHITE);
        sink.label(centre.add(0, e.radiusY() + 0.3, 0), Component.translatable("architects_toolbox.label.radius_y", number(e.radiusY())), WHITE);
        sink.label(centre.add(0, 0, e.radiusZ() + 0.3), Component.translatable("architects_toolbox.label.radius_z", number(e.radiusZ())), WHITE);
    }

    private static void line(List<BlockPos> points, PathShape path, Sink sink) {
        List<Vec3> centres = path.path();
        markers(centres, sink);
        double total = 0;
        for (int i = 0; i + 1 < centres.size(); i++) {
            Vec3 a = centres.get(i);
            Vec3 b = centres.get(i + 1);
            sink.line(a, b, sink.colour());
            double length = a.distanceTo(b);
            total += length;
            Vec3 middle = a.add(b).scale(0.5);
            sink.label(middle.add(0, 0.3, 0), Component.literal(number(length)), WHITE);
            if (points.size() > 2) {
                sink.label(middle.add(0, 1.0, 0), Component.literal(deltas(points.get(i), points.get(i + 1))), DELTA_GREY);
            }
        }
        for (int i = 1; i + 1 < centres.size(); i++) {
            angle(centres.get(i - 1), centres.get(i), centres.get(i + 1), sink);
        }
        if (points.size() > 2) {
            sink.label(centres.getLast().add(0, 1.5, 0), Component.translatable("architects_toolbox.label.total", number(total)), TOTAL_GREEN);
        }
        tube(centres, path.radius(), 1, sink);
    }

    private static void spline(List<Vec3> controls, PathShape path, Sink sink) {
        List<Vec3> samples = path.path();
        markers(controls, sink);
        double total = 0;
        double section = 0;
        for (int i = 0; i + 1 < samples.size(); i++) {
            sink.line(samples.get(i), samples.get(i + 1), sink.colour());
            double length = samples.get(i).distanceTo(samples.get(i + 1));
            total += length;
            section += length;
            boolean sectionEnds = controls.size() < 3 || (i + 1) % Spline.SAMPLES_PER_SECTION == 0;
            if (sectionEnds) {
                int middle = controls.size() < 3 ? 0 : i + 1 - Spline.SAMPLES_PER_SECTION / 2;
                Vec3 at = controls.size() < 3 ? samples.get(i).add(samples.get(i + 1)).scale(0.5) : samples.get(middle);
                sink.label(at.add(0, 0.3, 0), Component.literal(number(section)), WHITE);
                section = 0;
            }
        }
        if (controls.size() > 1) {
            sink.label(controls.getLast().add(0, 1.5, 0), Component.translatable("architects_toolbox.label.spline_length", number(total)), TOTAL_GREEN);
        }
        tube(samples, path.radius(), Spline.SAMPLES_PER_SECTION / 4, sink);
    }

    /** A small cross and a P1, P2, ... label at each point. */
    private static void markers(List<Vec3> points, Sink sink) {
        for (int i = 0; i < points.size(); i++) {
            Vec3 p = points.get(i);
            sink.line(p.add(-MARKER, 0, 0), p.add(MARKER, 0, 0), sink.colour());
            sink.line(p.add(0, -MARKER, 0), p.add(0, MARKER, 0), sink.colour());
            sink.line(p.add(0, 0, -MARKER), p.add(0, 0, MARKER), sink.colour());
            sink.label(p.add(0, 1.0, 0), Component.translatable("architects_toolbox.label.point", i + 1), POINT_YELLOW);
        }
    }

    /** The angle between the two segments meeting at a point: an arc and its size in degrees. */
    private static void angle(Vec3 previous, Vec3 at, Vec3 next, Sink sink) {
        Vec3 d1 = previous.subtract(at).normalize();
        Vec3 d2 = next.subtract(at).normalize();
        double degrees = Math.toDegrees(Math.acos(Math.clamp(d1.dot(d2), -1.0, 1.0)));
        Vec3 last = at.add(d1.scale(0.5));
        for (int i = 1; i <= 8; i++) {
            Vec3 direction = d1.lerp(d2, i / 8.0);
            if (direction.lengthSqr() < 1.0e-6) {
                break;
            }
            Vec3 point = at.add(direction.normalize().scale(0.5));
            sink.line(last, point, ANGLE_CYAN);
            last = point;
        }
        sink.label(at.add(0, -0.5, 0), Component.literal(String.format(Locale.ROOT, "%.1f°", degrees)), ANGLE_CYAN);
    }

    /** Rings round the path every {@code ringEvery} points and lines along it, at the tube radius. */
    private static void tube(List<Vec3> path, int radius, int ringEvery, Sink sink) {
        if (radius <= 0 || path.size() < 2) {
            return;
        }
        Vec3[] previous = null;
        for (int i = 0; i < path.size(); i++) {
            Vec3 direction = (i + 1 < path.size() ? path.get(i + 1).subtract(path.get(i)) : path.get(i).subtract(path.get(i - 1))).normalize();
            Vec3 up = Math.abs(direction.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 e1 = direction.cross(up).normalize();
            Vec3 e2 = direction.cross(e1);
            Vec3[] ring = new Vec3[TUBE_SEGMENTS];
            for (int s = 0; s < TUBE_SEGMENTS; s++) {
                double a = 2 * Math.PI * s / TUBE_SEGMENTS;
                ring[s] = path.get(i).add(e1.scale(Math.cos(a) * radius)).add(e2.scale(Math.sin(a) * radius));
            }
            if (i % ringEvery == 0 || i == path.size() - 1) {
                for (int s = 0; s < TUBE_SEGMENTS; s++) {
                    sink.line(ring[s], ring[(s + 1) % TUBE_SEGMENTS], sink.colour());
                }
            }
            if (previous != null) {
                for (int s = 0; s < TUBE_SEGMENTS; s += TUBE_SEGMENTS / TUBE_SIDES) {
                    sink.line(previous[s], ring[s], sink.colour());
                }
            }
            previous = ring;
        }
    }

    private static String deltas(BlockPos a, BlockPos b) {
        StringBuilder text = new StringBuilder();
        int dx = Math.abs(b.getX() - a.getX()), dy = Math.abs(b.getY() - a.getY()), dz = Math.abs(b.getZ() - a.getZ());
        if (dx > 0) {
            text.append("X:").append(dx).append(' ');
        }
        if (dy > 0) {
            text.append("Y:").append(dy).append(' ');
        }
        if (dz > 0) {
            text.append("Z:").append(dz);
        }
        return text.toString().trim();
    }

    /** A whole number as it is, anything else with one decimal. */
    static String number(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }
}
