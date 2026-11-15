package com.ArchitectsToolbox.gui;

import com.ArchitectsToolbox.client.MeasurementColours;
import com.ArchitectsToolbox.client.Operations;
import com.ArchitectsToolbox.measure.ShapeKind;
import com.ArchitectsToolbox.measure.ShapeOptions;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The measure wheel (F61): pages of items round a ring, tabs above it. Plain scroll turns the page, Shift+scroll
 * changes the hovered item's setting, releasing the measure key or clicking runs the hovered item. The pages are
 * the table in {@link #PAGES}: a new page or item is one entry there. Keeps its page between openings. Client thread.
 */
public final class Wheel {
    private static final int INNER_RADIUS = 35;
    private static final int OUTER_RADIUS = 150;
    private static final double GAP = 0.03;
    private static final int TAB_HEIGHT = 14;
    private static final int TAB_PAD = 6;
    private static final int DIM = 0x80000000;
    private static final int SECTOR = 0xA0202020;
    private static final int SECTOR_HOVERED = 0xD0505050;
    private static final int CENTRE = 0xE0181818;
    private static final int TEXT = 0xFFDDDDDD;
    private static final int TEXT_HOVERED = 0xFFFFFFFF;
    private static final int TEXT_DISABLED = 0xFF666666;
    private static final int ACTIVE = 0xFF55FF55;
    private static final int MODIFIED = 0xFFFFFF55;
    private static final int HINT = 0xFF999999;

    /**
     * One item of a page.
     *
     * @param label   its text, which carries its state ("Count: On", "Rectangle ÷4")
     * @param colour  its state colour, or 0 for the plain text colour
     * @param enabled whether it can run now
     * @param run     what it does
     * @param setting what Shift+scroll does over it, given the step (+1 or -1), or null if nothing
     */
    public record Item(Function<Operations, Component> label, Function<Operations, Integer> colour, Predicate<Operations> enabled,
                       Action run, @Nullable Setting setting) {}

    public record Page(String id, List<Item> items) {
        public Component title() {
            return Component.translatable("architects_toolbox.wheel.page." + id);
        }
    }

    @FunctionalInterface
    public interface Action {
        void run(Operations operations);
    }

    @FunctionalInterface
    public interface Setting {
        void step(Operations operations, int step);
    }

    /** The pages. */
    public static final List<Page> PAGES = List.of(
            new Page("edit", List.of(
                    toggle("preview", ops -> ops.views().preview(), ops -> true, Operations::togglePreview),
                    item("new_measurement", Operations::canStartMeasurement, Operations::newMeasurement),
                    new Item(ops -> Component.translatable("architects_toolbox.wheel.active_measurement", ops.store().state().active() + 1,
                                    ops.store().state().measurements().size()), ops -> ops.active().colour(),
                            ops -> ops.store().state().measurements().size() > 1, ops -> ops.stepMeasurement(1), Operations::stepMeasurement),
                    item("clear", ops -> !ops.active().isEmpty() || ops.store().state().measurements().size() > 1, Operations::clear),
                    item("clear_all", ops -> !ops.active().isEmpty() || ops.store().state().measurements().size() > 1, Operations::clearAll),
                    item("undo", ops -> ops.store().canUndo(), Operations::undo),
                    item("redo", ops -> ops.store().canRedo(), Operations::redo),
                    item("copy", ops -> !ops.active().isEmpty(), Operations::copy),
                    new Item(ops -> Component.translatable(ops.pasting() ? "architects_toolbox.wheel.cancel_paste" : "architects_toolbox.wheel.paste"),
                            ops -> ops.pasting() ? ACTIVE : 0, ops -> ops.pasting() || ops.hasClipboard(), Operations::togglePaste, null),
                    item("clear_placements", ops -> !ops.placements().isEmpty(), Operations::clearPlacements))),
            new Page("shape", List.of(
                    shape(ShapeKind.RECTANGLE), shape(ShapeKind.CYLINDER), shape(ShapeKind.ELLIPSOID), shape(ShapeKind.SPLINE), shape(ShapeKind.LINE))),
            new Page("display", List.of(
                    toggle("count", ops -> ops.views().count(), ops -> true, Operations::toggleCount),
                    toggle("hollow", ops -> ops.views().hollow(), ops -> true, Operations::toggleHollow),
                    toggle("layer", ops -> ops.views().layer(), ops -> ops.views().layer() || ops.canShowLayer(), Operations::toggleLayer),
                    toggle("chunks", ops -> ops.views().chunks(), ops -> true, Operations::toggleChunks),
                    toggle("biomes", ops -> ops.views().biomes(), ops -> true, Operations::toggleBiomes),
                    structures())));

    private final Operations operations;
    private List<Integer> palette = List.of();
    private List<Page> pages = PAGES;
    private record Label(String text, int outer, List<WheelLabel.Line> lines) {}

    private final Map<Item, Label> labels = new IdentityHashMap<>();
    private int page;
    private int hovered = -1;
    private int centreX;
    private int centreY;
    private int outer = OUTER_RADIUS;

    public Wheel(Operations operations) {
        this.operations = operations;
    }

    private static Item item(String id, Predicate<Operations> enabled, Action run) {
        return new Item(ops -> Component.translatable("architects_toolbox.wheel." + id), ops -> 0, enabled, run, null);
    }

    private static Item toggle(String id, Predicate<Operations> on, Predicate<Operations> enabled, Action run) {
        return new Item(ops -> Component.translatable("architects_toolbox.wheel." + id,
                        Component.translatable(on.test(ops) ? "architects_toolbox.wheel.on" : "architects_toolbox.wheel.off")),
                ops -> on.test(ops) ? ACTIVE : 0, enabled, run, null);
    }

    /** On or off; Shift+scroll shows or hides the pieces. Greyed out on a server, where structures are not known. */
    private static Item structures() {
        return new Item(ops -> Component.translatable(ops.views().structurePieces() ? "architects_toolbox.wheel.structures_pieces" : "architects_toolbox.wheel.structures",
                        Component.translatable(ops.views().structures() ? "architects_toolbox.wheel.on" : "architects_toolbox.wheel.off")),
                ops -> ops.views().structures() ? ACTIVE : ops.views().structurePieces() ? MODIFIED : 0,
                ops -> ops.views().structures() || ops.canShowStructures(), Operations::toggleStructures, (ops, step) -> ops.toggleStructurePieces());
    }

    private static Item shape(ShapeKind shape) {
        return new Item(ops -> shapeLabel(shape, ops.active().options()),
                ops -> ops.active().shape() == shape ? ACTIVE : ops.active().options().modified(shape) ? MODIFIED : 0,
                ops -> true, ops -> ops.setShape(shape), (ops, step) -> ops.stepShapeOption(shape, step));
    }

    /** The shape's name with the setting Shift+scroll changes, when it is not the default. */
    private static Component shapeLabel(ShapeKind shape, ShapeOptions options) {
        String key = "architects_toolbox.wheel.shape_option." + shape.id();
        return switch (shape) {
            case RECTANGLE -> options.subdivisions() == 0 ? shape.displayName() : Component.translatable(key, shape.displayName(), options.subdivisions());
            case CYLINDER -> options.cylinder() == ShapeOptions.CylinderMode.ALONG_Y ? shape.displayName()
                    : Component.translatable(key, shape.displayName(), Component.translatable("architects_toolbox.cylinder." + options.cylinder().name().toLowerCase(Locale.ROOT)));
            case ELLIPSOID -> options.ellipsoid() == ShapeOptions.EllipsoidMode.FIT_TO_BOX ? shape.displayName() : Component.translatable(key, shape.displayName());
            case LINE, SPLINE -> options.tube(shape) == 0 ? shape.displayName() : Component.translatable(key, shape.displayName(), options.tube(shape));
        };
    }

    /** Palette edits rebuild the colour page once; drawing and scrolling reuse its items. */
    private List<Page> pages() {
        List<Integer> configured = operations.palette();
        if (!configured.equals(palette)) {
            palette = configured;
            labels.clear();
            List<Item> colours = new ArrayList<>();
            for (int colour : palette) {
                colours.add(new Item(ops -> ops.active().colour() == colour
                                ? Component.translatable("architects_toolbox.wheel.selected_colour", MeasurementColours.name(colour))
                                : MeasurementColours.name(colour),
                        ops -> colour, ops -> true, ops -> ops.pickColour(colour), null));
            }
            List<Page> next = new ArrayList<>(PAGES);
            next.add(new Page("colour", List.copyOf(colours)));
            pages = List.copyOf(next);
        }
        return pages;
    }

    private Page current() {
        return pages().get(page);
    }

    /** Draws the wheel and works out which item the mouse is over. */
    public void draw(GuiCanvas canvas, int mouseX, int mouseY) {
        centreX = canvas.width() / 2;
        centreY = canvas.height() / 2 + TAB_HEIGHT / 2;
        outer = Math.max(INNER_RADIUS + 30, Math.min(OUTER_RADIUS, Math.min(canvas.width(), canvas.height()) / 2 - TAB_HEIGHT - 12));
        hovered = itemAt(mouseX, mouseY);
        canvas.fill(0, 0, canvas.width(), canvas.height(), DIM);

        List<Item> items = current().items();
        double span = 2 * Math.PI / items.size();
        for (int i = 0; i < items.size(); i++) {
            double start = -Math.PI / 2 + i * span;
            Sectors.fill(canvas, centreX, centreY, INNER_RADIUS, outer, start + GAP, start + span - GAP, i == hovered ? SECTOR_HOVERED : SECTOR);
            Item item = items.get(i);
            String text = item.label().apply(operations).getString();
            Label label = labels.get(item);
            if (label == null || label.outer() != outer || !label.text().equals(text)) {
                label = new Label(text, outer, WheelLabel.layout(canvas, text, INNER_RADIUS, outer, start + GAP, start + span - GAP));
                labels.put(item, label);
            }
            int colour = textColour(item, i == hovered);
            for (WheelLabel.Line line : label.lines()) {
                canvas.text(line.text(), centreX + line.x(), centreY + line.y(), colour);
            }
        }
        Sectors.fill(canvas, centreX, centreY, 0, INNER_RADIUS - 2, 0, 2 * Math.PI, CENTRE);
        Component middleText = hovered >= 0 ? items.get(hovered).label().apply(operations) : current().title();
        if (hovered >= 0) {
            int y = centreY + outer + 4;
            int halfWidth = canvas.textWidth(middleText) / 2 + 5;
            canvas.fill(centreX - halfWidth, y - 2, centreX + halfWidth, y + GuiCanvas.LINE_HEIGHT + 2, CENTRE);
            canvas.centredText(middleText, centreX, y, TEXT_HOVERED);
        } else {
            canvas.centredText(middleText, centreX, centreY - GuiCanvas.LINE_HEIGHT / 2, HINT);
        }
        drawTabs(canvas);
    }

    private int textColour(Item item, boolean isHovered) {
        if (!item.enabled().test(operations)) {
            return TEXT_DISABLED;
        }
        int state = item.colour().apply(operations);
        if (state != 0) {
            return state;
        }
        return isHovered ? TEXT_HOVERED : TEXT;
    }

    private void drawTabs(GuiCanvas canvas) {
        int x = centreX - tabsWidth(canvas) / 2;
        int y = tabsTop();
        for (int i = 0; i < pages().size(); i++) {
            Component title = pages().get(i).title();
            int width = canvas.textWidth(title) + 2 * TAB_PAD;
            canvas.fill(x, y, x + width, y + TAB_HEIGHT, i == page ? SECTOR_HOVERED : SECTOR);
            canvas.text(title, x + TAB_PAD, y + (TAB_HEIGHT - GuiCanvas.LINE_HEIGHT) / 2 + 1, i == page ? TEXT_HOVERED : HINT);
            x += width + 2;
        }
    }

    private int tabsTop() {
        return centreY - outer - TAB_HEIGHT - 6;
    }

    private int tabsWidth(GuiCanvas canvas) {
        int width = -2;
        for (Page p : pages()) {
            width += canvas.textWidth(p.title()) + 2 * TAB_PAD + 2;
        }
        return width;
    }

    private int itemAt(double mouseX, double mouseY) {
        double dx = mouseX - centreX;
        double dy = mouseY - centreY;
        double distance = Math.hypot(dx, dy);
        if (distance < INNER_RADIUS || distance > outer) {
            return -1;
        }
        double angle = Math.atan2(dy, dx) + Math.PI / 2;
        if (angle < 0) {
            angle += 2 * Math.PI;
        }
        int count = current().items().size();
        return Math.min((int) (angle / (2 * Math.PI / count)), count - 1);
    }

    /** Plain scroll turns the page; Shift+scroll changes the hovered item's setting. */
    public void scroll(double amount, boolean shift) {
        int step = amount > 0 ? 1 : amount < 0 ? -1 : 0;
        if (step == 0) {
            return;
        }
        if (shift) {
            if (hovered >= 0) {
                Item item = current().items().get(hovered);
                if (item.setting() != null && item.enabled().test(operations)) {
                    item.setting().step(operations, step);
                }
            }
            return;
        }
        // Scrolling down (a negative amount) moves to the next page, as it moves down a list.
        page = Math.floorMod(page - step, pages().size());
        hovered = -1;
    }

    /**
     * A left click: a tab turns to its page, an item runs. Returns true when an item ran, and the wheel should
     * close; {@code canvas} gives the text widths the tabs were laid out with.
     */
    public boolean click(GuiCanvas canvas, double mouseX, double mouseY) {
        int y = tabsTop();
        if (mouseY >= y && mouseY < y + TAB_HEIGHT) {
            int x = centreX - tabsWidth(canvas) / 2;
            for (int i = 0; i < pages().size(); i++) {
                int width = canvas.textWidth(pages().get(i).title()) + 2 * TAB_PAD;
                if (mouseX >= x && mouseX < x + width) {
                    page = i;
                    hovered = -1;
                    return false;
                }
                x += width + 2;
            }
            return false;
        }
        int item = itemAt(mouseX, mouseY);
        if (item >= 0) {
            hovered = item;
            return runHovered();
        }
        return false;
    }

    /** Runs the hovered item, if it can run; called when the measure key is released. */
    public boolean runHovered() {
        if (hovered < 0) {
            return false;
        }
        Item item = current().items().get(hovered);
        if (!item.enabled().test(operations)) {
            return false;
        }
        item.run().run(operations);
        return true;
    }
}
