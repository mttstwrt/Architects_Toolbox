package com.ArchitectsToolbox.gui;

/**
 * Fills a ring sector with horizontal strips, the only shape GUI fills give. A strip is where a row meets the ring
 * (one or two spans) and the wedge between the two angles (one span, as the wedge is never wider than half a turn,
 * or the whole row for a full turn). Angles are radians, clockwise on screen from the positive x axis.
 */
final class Sectors {
    private static final int STRIP = 2;

    private Sectors() {
    }

    static void fill(GuiCanvas canvas, int cx, int cy, int inner, int outer, double start, double end, int argb) {
        boolean fullTurn = end - start >= 2 * Math.PI - 1.0e-9;
        double ux0 = Math.cos(start), uy0 = Math.sin(start);
        double ux1 = Math.cos(end), uy1 = Math.sin(end);
        for (int dy = -outer; dy < outer; dy += STRIP) {
            double row = dy + STRIP / 2.0;
            double outerHalf = halfWidth(outer, row);
            if (Double.isNaN(outerHalf)) {
                continue;
            }
            double innerHalf = halfWidth(inner, row);
            double lo = -outerHalf;
            double hi = outerHalf;
            if (!fullTurn) {
                // Left of the start ray and right of the end ray: ux0*row - uy0*x >= 0 and x*uy1 - row*ux1 >= 0.
                double[] span = {lo, hi};
                if (!clip(span, -uy0, -ux0 * row) || !clip(span, uy1, row * ux1)) {
                    continue;
                }
                lo = span[0];
                hi = span[1];
            }
            if (Double.isNaN(innerHalf)) {
                strip(canvas, cx, cy + dy, lo, hi, argb);
            } else {
                strip(canvas, cx, cy + dy, lo, Math.min(hi, -innerHalf), argb);
                strip(canvas, cx, cy + dy, Math.max(lo, innerHalf), hi, argb);
            }
        }
    }

    /** Half the width of a circle at a row, or NaN if the row misses it. */
    private static double halfWidth(int radius, double row) {
        return Math.abs(row) >= radius ? Double.NaN : Math.sqrt((double) radius * radius - row * row);
    }

    /** Narrows {@code span} to where {@code coefficient * x >= limit}; false if nothing is left. */
    private static boolean clip(double[] span, double coefficient, double limit) {
        if (Math.abs(coefficient) < 1.0e-12) {
            return limit <= 0;
        }
        double bound = limit / coefficient;
        if (coefficient > 0) {
            span[0] = Math.max(span[0], bound);
        } else {
            span[1] = Math.min(span[1], bound);
        }
        return span[0] < span[1];
    }

    private static void strip(GuiCanvas canvas, int cx, int y, double lo, double hi, int argb) {
        int x0 = cx + (int) Math.round(lo);
        int x1 = cx + (int) Math.round(hi);
        if (x1 > x0) {
            canvas.fill(x0, y, x1, y + STRIP, argb);
        }
    }
}
