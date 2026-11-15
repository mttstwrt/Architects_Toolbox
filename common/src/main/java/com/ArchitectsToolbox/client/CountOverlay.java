package com.ArchitectsToolbox.client;

import com.ArchitectsToolbox.gui.GuiCanvas;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Numbered block counts on the HUD, using each measurement's colour. Every enabled measurement keeps its summary;
 * block-type detail shrinks with the available height, rather than letting one large count hide later measurements.
 */
final class CountOverlay {
    private static final int MAX_ROWS = 20;
    private static final int ROW = 18;
    private static final int PAD = 4;
    private static final int HEADER = GuiCanvas.LINE_HEIGHT + PAD;
    private static final int BACKGROUND = 0x80000000;

    /** One section; headers contain the total even when no detail rows fit. */
    private record Section(Component header, int colour, List<BlockCounter.Entry> entries, int more) {
        int height() {
            return HEADER + entries.size() * ROW + (more > 0 ? HEADER : 0);
        }
    }

    private CountOverlay() {
    }

    static void draw(GuiCanvas canvas, MeasurementCounts counts) {
        List<MeasurementCounts.Tracked> tracked = counts.tracked();
        if (tracked.isEmpty()) {
            return;
        }
        int available = Math.max(HEADER, canvas.height() - 2 * PAD);
        int perColumn = Math.max(1, available / HEADER);
        int x = 0;
        for (int start = 0; start < tracked.size(); start += perColumn) {
            int end = Math.min(start + perColumn, tracked.size());
            int detailRows = Math.min(MAX_ROWS, Math.max(0, (available / (end - start) - 2 * HEADER) / ROW));
            List<Section> sections = new ArrayList<>();
            int height = 0;
            int width = 0;
            for (int i = start; i < end; i++) {
                MeasurementCounts.Tracked entry = tracked.get(i);
                Section section = section(entry, detailRows);
                sections.add(section);
                height += section.height();
                width = Math.max(width, canvas.textWidth(section.header()));
                for (BlockCounter.Entry block : section.entries()) {
                    width = Math.max(width, 20 + canvas.textWidth(rowText(block)));
                }
                if (section.more() > 0) {
                    width = Math.max(width, canvas.textWidth(more(section.more())));
                }
            }
            int y = (canvas.height() - height) / 2;
            canvas.fill(x, y - PAD, x + width + 2 * PAD, y + height + PAD, BACKGROUND);
            for (Section section : sections) {
                canvas.text(section.header(), x + PAD, y, section.colour());
                y += HEADER;
                for (BlockCounter.Entry block : section.entries()) {
                    if (block.block().asItem() != Items.AIR) {
                        canvas.item(new ItemStack(block.block().asItem()), x + PAD, y);
                    }
                    canvas.text(rowText(block), x + PAD + 20, y + (16 - GuiCanvas.LINE_HEIGHT) / 2 + 1, section.colour());
                    y += ROW;
                }
                if (section.more() > 0) {
                    canvas.text(more(section.more()), x + PAD, y, section.colour());
                    y += HEADER;
                }
            }
            x += width + 2 * PAD + 2;
        }
    }

    private static Section section(MeasurementCounts.Tracked entry, int detailRows) {
        BlockCounter counter = entry.counter();
        int colour = entry.measurement().colour();
        if (counter.tooLarge()) {
            return new Section(Component.translatable("architects_toolbox.count.measurement_too_large", entry.number()), colour, List.of(), 0);
        }
        BlockCounter.Count count = counter.count();
        Component header = count == null || count.progress() < 1
                ? Component.translatable("architects_toolbox.count.measurement_counting", entry.number(),
                        count == null ? "0" : amount(count.total()), count == null ? 0 : Math.round(count.progress() * 100))
                : Component.translatable("architects_toolbox.count.measurement_total", entry.number(), amount(count.total()));
        if (count == null) {
            return new Section(header, colour, List.of(), 0);
        }
        int rows = Math.min(count.entries().size(), detailRows);
        // A summary-only section needs no overflow row: its total remains visible and leaves room for the others.
        int more = rows == 0 ? 0 : count.entries().size() - rows;
        return new Section(header, colour, count.entries().subList(0, rows), more);
    }

    private static Component more(int count) {
        return Component.translatable("architects_toolbox.count.more", count);
    }

    /** The count, with the block's name for blocks that have no item to show (water, fire, ...). */
    private static Component rowText(BlockCounter.Entry entry) {
        Component number = Component.literal(amount(entry.count()));
        return entry.block().asItem() != Items.AIR ? number : Component.empty().append(number).append(" ").append(entry.block().getName());
    }

    /** 1.0.0's short form: 950, 1.2K, 3.4M. */
    static String amount(long n) {
        if (n < 1_000) {
            return Long.toString(n);
        }
        if (n < 1_000_000) {
            return String.format(Locale.ROOT, "%.1fK", n / 1_000.0);
        }
        return String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0);
    }
}
