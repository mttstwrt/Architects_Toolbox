package com.ArchitectsToolbox.geometry;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** The Catmull-Rom curve through a list of points, as 1.0.0 drew it: the ends are extended by mirroring their neighbour. */
public final class Spline {
    /** Straight pieces per section between two control points. */
    public static final int SAMPLES_PER_SECTION = 32;

    private Spline() {
    }

    /** The curve as a polyline, starting and ending on the first and last control point. */
    public static List<Vec3> sample(List<Vec3> controls) {
        List<Vec3> samples = new ArrayList<>();
        if (controls.size() < 3) {
            samples.addAll(controls);
            return samples;
        }
        int last = controls.size() - 1;
        for (int i = 0; i < last; i++) {
            Vec3 p0 = i == 0 ? mirror(controls.get(1), controls.get(0)) : controls.get(i - 1);
            Vec3 p1 = controls.get(i);
            Vec3 p2 = controls.get(i + 1);
            Vec3 p3 = i + 1 == last ? mirror(controls.get(last - 1), controls.get(last)) : controls.get(i + 2);
            for (int j = 0; j < SAMPLES_PER_SECTION; j++) {
                samples.add(point(p0, p1, p2, p3, (double) j / SAMPLES_PER_SECTION));
            }
        }
        samples.add(controls.get(last));
        return samples;
    }

    /** {@code point} reflected through {@code about}. */
    private static Vec3 mirror(Vec3 point, Vec3 about) {
        return about.scale(2).subtract(point);
    }

    private static Vec3 point(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return new Vec3(axis(p0.x, p1.x, p2.x, p3.x, t, t2, t3), axis(p0.y, p1.y, p2.y, p3.y, t, t2, t3), axis(p0.z, p1.z, p2.z, p3.z, t, t2, t3));
    }

    private static double axis(double p0, double p1, double p2, double p3, double t, double t2, double t3) {
        return 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }
}
