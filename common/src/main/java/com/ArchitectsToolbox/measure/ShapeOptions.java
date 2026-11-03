package com.ArchitectsToolbox.measure;

import net.minecraft.core.Direction;

/**
 * Each shape's own settings; a measurement keeps all of them, so switching shape and back finds them as they were.
 *
 * @param subdivisions rectangle: parts each side is divided into, 0 for none (one of {@link #SUBDIVISION_STEPS})
 * @param cylinder     cylinder: the axis it stands along, or centre and rim
 * @param ellipsoid    ellipsoid: fitted to the box, or centre and radius
 * @param lineTube     line: tube radius, 0 to {@link #MAX_TUBE}
 * @param splineTube   spline: tube radius, 0 to {@link #MAX_TUBE}
 */
public record ShapeOptions(int subdivisions, CylinderMode cylinder, EllipsoidMode ellipsoid, int lineTube, int splineTube) {
    public static final int[] SUBDIVISION_STEPS = {0, 2, 3, 4, 5, 8, 10, 16};
    public static final int MAX_TUBE = 16;
    public static final ShapeOptions DEFAULT = new ShapeOptions(0, CylinderMode.ALONG_Y, EllipsoidMode.FIT_TO_BOX, 0, 0);

    public ShapeOptions {
        if (subdivisions < 0 || lineTube < 0 || lineTube > MAX_TUBE || splineTube < 0 || splineTube > MAX_TUBE) {
            throw new IllegalArgumentException("shape option out of range");
        }
    }

    /** The cylinder's forms, in the order Shift+scroll steps through them. */
    public enum CylinderMode {
        ALONG_Y(Direction.Axis.Y),
        ALONG_X(Direction.Axis.X),
        ALONG_Z(Direction.Axis.Z),
        /** Point 1 is the centre, point 2 on the rim; upright. */
        CENTRE_RIM(Direction.Axis.Y);

        private final Direction.Axis axis;

        CylinderMode(Direction.Axis axis) {
            this.axis = axis;
        }

        public Direction.Axis axis() {
            return axis;
        }
    }

    public enum EllipsoidMode {
        FIT_TO_BOX,
        /** Point 1 is the centre, point 2 sets the three radii. */
        CENTRE_RADIUS
    }

    /** The next (or, for a negative step, previous) subdivision step, wrapping round. */
    public ShapeOptions stepSubdivisions(int step) {
        int index = 0;
        for (int i = 0; i < SUBDIVISION_STEPS.length; i++) {
            if (SUBDIVISION_STEPS[i] == subdivisions) {
                index = i;
            }
        }
        int next = Math.floorMod(index + Integer.signum(step), SUBDIVISION_STEPS.length);
        return new ShapeOptions(SUBDIVISION_STEPS[next], cylinder, ellipsoid, lineTube, splineTube);
    }

    public ShapeOptions stepCylinder(int step) {
        CylinderMode[] modes = CylinderMode.values();
        return new ShapeOptions(subdivisions, modes[Math.floorMod(cylinder.ordinal() + Integer.signum(step), modes.length)], ellipsoid, lineTube, splineTube);
    }

    public ShapeOptions toggleEllipsoid() {
        EllipsoidMode next = ellipsoid == EllipsoidMode.FIT_TO_BOX ? EllipsoidMode.CENTRE_RADIUS : EllipsoidMode.FIT_TO_BOX;
        return new ShapeOptions(subdivisions, cylinder, next, lineTube, splineTube);
    }

    /** Changes the tube radius of the line or the spline; other shapes have none. */
    public ShapeOptions stepTube(ShapeKind shape, int step) {
        return switch (shape) {
            case LINE -> new ShapeOptions(subdivisions, cylinder, ellipsoid, Math.clamp(lineTube + Integer.signum(step), 0, MAX_TUBE), splineTube);
            case SPLINE -> new ShapeOptions(subdivisions, cylinder, ellipsoid, lineTube, Math.clamp(splineTube + Integer.signum(step), 0, MAX_TUBE));
            default -> this;
        };
    }

    public int tube(ShapeKind shape) {
        return switch (shape) {
            case LINE -> lineTube;
            case SPLINE -> splineTube;
            default -> 0;
        };
    }

    /** Whether the shape's options differ from the defaults, which the wheel shows. */
    public boolean modified(ShapeKind shape) {
        return switch (shape) {
            case RECTANGLE -> subdivisions != 0;
            case CYLINDER -> cylinder != CylinderMode.ALONG_Y;
            case ELLIPSOID -> ellipsoid != EllipsoidMode.FIT_TO_BOX;
            case LINE -> lineTube != 0;
            case SPLINE -> splineTube != 0;
        };
    }
}
