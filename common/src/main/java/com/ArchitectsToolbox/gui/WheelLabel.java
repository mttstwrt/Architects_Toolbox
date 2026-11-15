package com.ArchitectsToolbox.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/** Lays out a wheel label inside its annular wedge, including padding for the text shadow. */
final class WheelLabel {
    private static final int PAD = 3;
    private static final int MAX_LINES = 3;

    record Line(Component text, int x, int y) {}

    private WheelLabel() {}

    /** Called only when a label, radius or page changes; the wheel reuses these positioned lines while drawing. */
    static List<Line> layout(GuiCanvas canvas, String text, int inner, int outer, double start, double end) {
        double middle = (start + end) / 2;
        int radius = (inner + outer) / 2;
        int x = (int) Math.round(Math.cos(middle) * radius);
        int y = (int) Math.round(Math.sin(middle) * radius);
        List<String> lines = List.of();
        int width = 0;
        for (int rows = 1; rows <= MAX_LINES; rows++) {
            int halfHeight = (rows * GuiCanvas.LINE_HEIGHT + 1) / 2;
            width = 0;
            for (int candidate = 1; candidate <= outer; candidate++) {
                if (!fits(x, y, candidate, halfHeight, inner, outer, start, end)) {
                    break;
                }
                width = 2 * candidate;
            }
            lines = wrap(canvas, text, Math.max(1, width));
            if (lines.size() <= rows) {
                break;
            }
        }
        if (width < canvas.textWidth(Component.literal("…"))) {
            // At an extreme GUI scale, retain the hover caption rather than draw outside the wedge.
            return List.of();
        }
        if (lines.size() > MAX_LINES) {
            lines = new ArrayList<>(lines.subList(0, MAX_LINES));
            String last = lines.get(MAX_LINES - 1);
            while (!last.isEmpty() && canvas.textWidth(Component.literal(last + "…")) > width) {
                last = last.substring(0, last.offsetByCodePoints(last.length(), -1));
            }
            lines.set(MAX_LINES - 1, last + "…");
        }
        List<Line> positioned = new ArrayList<>(lines.size());
        int top = y - lines.size() * GuiCanvas.LINE_HEIGHT / 2;
        for (String line : lines) {
            Component component = Component.literal(line);
            positioned.add(new Line(component, x - canvas.textWidth(component) / 2, top));
            top += GuiCanvas.LINE_HEIGHT;
        }
        return List.copyOf(positioned);
    }

    /** The whole padded rectangle must stay outside the centre, inside the circle and between both wedge rays. */
    private static boolean fits(int x, int y, int halfWidth, int halfHeight, int inner, int outer, double start, double end) {
        int w = halfWidth + PAD;
        int h = halfHeight + PAD;
        int nearX = Math.max(0, Math.abs(x) - w);
        int nearY = Math.max(0, Math.abs(y) - h);
        if (nearX * nearX + nearY * nearY < inner * inner) {
            return false;
        }
        for (int dx : new int[] {-w, w}) {
            for (int dy : new int[] {-h, h}) {
                double px = x + dx;
                double py = y + dy;
                if (px * px + py * py > outer * outer) {
                    return false;
                }
                if (end - start < 2 * Math.PI - 0.1
                        && (Math.cos(start) * py - Math.sin(start) * px < 0
                        || px * Math.sin(end) - py * Math.cos(end) < 0)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Prefer spaces; split an oversized word at code-point boundaries so custom colours and translations fit too. */
    private static List<String> wrap(GuiCanvas canvas, String text, int width) {
        List<String> lines = new ArrayList<>();
        String line = "";
        for (String word : text.strip().split("\\s+")) {
            String combined = line.isEmpty() ? word : line + " " + word;
            if (canvas.textWidth(Component.literal(combined)) <= width) {
                line = combined;
                continue;
            }
            if (!line.isEmpty()) {
                lines.add(line);
            }
            line = "";
            for (int at = 0; at < word.length();) {
                int next = word.offsetByCodePoints(at, 1);
                String glyph = word.substring(at, next);
                if (!line.isEmpty() && canvas.textWidth(Component.literal(line + glyph)) > width) {
                    lines.add(line);
                    line = "";
                }
                line += glyph;
                at = next;
            }
        }
        if (!line.isEmpty()) {
            lines.add(line);
        }
        return lines;
    }
}
