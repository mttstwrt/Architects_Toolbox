package com.ArchitectsToolbox.measure;

import com.ArchitectsToolbox.geometry.BlockBox;
import com.ArchitectsToolbox.geometry.BoxShape;
import com.ArchitectsToolbox.geometry.CylinderShape;
import com.ArchitectsToolbox.geometry.EllipsoidShape;
import com.ArchitectsToolbox.geometry.PathShape;
import com.ArchitectsToolbox.geometry.Shape;
import com.ArchitectsToolbox.geometry.Spline;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * One measurement: its points in the order they were added, its shape and every shape's options, and its colour
 * (ARGB). Its stable identity keeps views and cached scenes attached across edits and undo. Immutable; an edit makes a new one.
 */
public record Measurement(UUID id, List<BlockPos> points, ShapeKind shape, ShapeOptions options, int colour) {
    /** 1.0.0's measurement red. */
    public static final int RED = 0xFFFF4D4D;
    public static final Measurement EMPTY = empty(RED);

    /** A new measurement starts with the default shape and its own identity for views and caches. */
    public static Measurement empty(int colour) {
        return new Measurement(UUID.randomUUID(), List.of(), ShapeKind.RECTANGLE, ShapeOptions.DEFAULT, colour);
    }

    public Measurement {
        points = List.copyOf(points);
    }

    public boolean isEmpty() {
        return points.isEmpty();
    }

    /** The same measurement with one more point; a point already in the list is not added again. */
    public Measurement withPoint(BlockPos point) {
        if (points.contains(point)) {
            return this;
        }
        List<BlockPos> next = new ArrayList<>(points);
        next.add(point);
        return withPoints(next);
    }

    public Measurement withPoints(List<BlockPos> next) {
        return new Measurement(id, next, shape, options, colour);
    }

    public Measurement withShape(ShapeKind next) {
        return new Measurement(id, points, next, options, colour);
    }

    public Measurement withOptions(ShapeOptions next) {
        return new Measurement(id, points, shape, next, colour);
    }

    /** Colour is picked before points are added; completed measurements are never recoloured. */
    public Measurement withColour(int next) {
        if (!isEmpty()) {
            throw new IllegalStateException("cannot recolour a measurement with points");
        }
        return new Measurement(id, points, shape, options, next);
    }

    /** The box around the points, which rectangle, box-fit cylinder and fitted ellipsoid all fill. */
    public BlockBox box() {
        return BlockBox.around(points);
    }

    /** The blocks the measurement covers; the measurement must have a point. */
    public Shape geometry() {
        return switch (shape) {
            case RECTANGLE -> new BoxShape(box());
            case CYLINDER -> options.cylinder() == ShapeOptions.CylinderMode.CENTRE_RIM && points.size() >= 2
                    ? centreAndRim() : CylinderShape.fitting(box(), options.cylinder().axis());
            case ELLIPSOID -> options.ellipsoid() == ShapeOptions.EllipsoidMode.CENTRE_RADIUS && points.size() >= 2
                    ? centredEllipsoid() : EllipsoidShape.fitting(box());
            case LINE -> new PathShape(centres(), options.lineTube());
            case SPLINE -> new PathShape(Spline.sample(centres()), options.splineTube());
        };
    }

    /** The points' block centres, in order. */
    public List<Vec3> centres() {
        return points.stream().map(Vec3::atCenterOf).toList();
    }

    private Shape centreAndRim() {
        BlockPos centre = points.get(0);
        BlockPos rim = points.get(1);
        double radius = Math.hypot(rim.getX() - centre.getX(), rim.getZ() - centre.getZ()) + 0.5;
        BlockBox box = box();
        return CylinderShape.centreAndRim(centre.getX(), centre.getZ(), radius, box.minY(), box.maxY());
    }

    private Shape centredEllipsoid() {
        BlockPos centre = points.get(0);
        BlockPos edge = points.get(1);
        return EllipsoidShape.centred(centre.getX(), centre.getY(), centre.getZ(),
                edge.getX() - centre.getX(), edge.getY() - centre.getY(), edge.getZ() - centre.getZ());
    }
}
