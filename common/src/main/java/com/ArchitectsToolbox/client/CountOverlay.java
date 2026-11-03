package com.ArchitectsToolbox.client;

import com.ArchitectsToolbox.gui.GuiCanvas;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The block count on the HUD (F22): at the left edge, centred vertically, one row per block type with its icon
 * and count, most first, at most {@link #MAX_ROWS}, then the total in green.
 */
final class CountOverlay {
    private static final int MAX_ROWS = 20;
    private static final int ROW = 18;
    private static final int PAD = 4;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFAAAAAA;
    private static final int TOTAL = 0xFF55FF55;
    private static final int BACKGROUND = 0x80000000;

    private CountOverlay() {
    }

    static void draw(GuiCanvas canvas, BlockCounter counter) {
        if (counter.tooLarge()) {
            Component text = Component.translatable("architects_toolbox.count.too_large", String.format(Locale.ROOT, "%,d", BlockCounter.LIMIT));
            int y = canvas.height() / 2 - GuiCanvas.LINE_HEIGHT / 2;
            canvas.fill(0, y - PAD, canvas.textWidth(text) + 2 * PAD, y + GuiCanvas.LINE_HEIGHT + PAD, BACKGROUND);
            canvas.text(text, PAD, y, MUTED);
            return;
        }
        BlockCounter.Count count = counter.count();
        if (count == null) {
            return;
        }
        Component header = count.progress() < 1
                ? Component.translatable("architects_toolbox.count.counting", Math.round(count.progress() * 100))
                : Component.translatable("architects_toolbox.count.header");
        int rows = Math.min(count.entries().size(), MAX_ROWS);
        boolean more = count.entries().size() > MAX_ROWS;
        int height = GuiCanvas.LINE_HEIGHT + PAD + rows * ROW + (more ? GuiCanvas.LINE_HEIGHT + PAD : 0) + GuiCanvas.LINE_HEIGHT + PAD;
        int top = (canvas.height() - height) / 2;
        Component total = Component.translatable("architects_toolbox.count.total", amount(count.total()));
        int width = Math.max(canvas.textWidth(header), canvas.textWidth(total));
        for (int i = 0; i < rows; i++) {
            width = Math.max(width, 20 + canvas.textWidth(rowText(count.entries().get(i))));
        }
        canvas.fill(0, top - PAD, width + 2 * PAD, top + height + PAD, BACKGROUND);

        int y = top;
        canvas.text(header, PAD, y, MUTED);
        y += GuiCanvas.LINE_HEIGHT + PAD;
        for (int i = 0; i < rows; i++) {
            BlockCounter.Entry entry = count.entries().get(i);
            if (entry.block().asItem() != Items.AIR) {
                canvas.item(new ItemStack(entry.block().asItem()), PAD, y);
            }
            canvas.text(rowText(entry), PAD + 20, y + (16 - GuiCanvas.LINE_HEIGHT) / 2 + 1, TEXT);
            y += ROW;
        }
        if (more) {
            canvas.text(Component.translatable("architects_toolbox.count.more", count.entries().size() - MAX_ROWS), PAD, y, MUTED);
            y += GuiCanvas.LINE_HEIGHT + PAD;
        }
        canvas.text(total, PAD, y, TOTAL);
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
