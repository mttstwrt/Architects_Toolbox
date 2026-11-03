package com.ArchitectsToolbox.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The few 2D drawing calls the wheel and the count overlay need, over each version's own GUI drawing class
 * ({@code GameCanvas} in the version sources). Coordinates are GUI pixels; colours are ARGB.
 */
public interface GuiCanvas {
    int LINE_HEIGHT = 9;

    int width();

    int height();

    void fill(int x0, int y0, int x1, int y1, int argb);

    /** Text with a shadow, its top left corner at (x, y). */
    void text(Component text, int x, int y, int argb);

    int textWidth(Component text);

    /** A 16 by 16 item icon. */
    void item(ItemStack stack, int x, int y);

    default void centredText(Component text, int centreX, int y, int argb) {
        text(text, centreX - textWidth(text) / 2, y, argb);
    }
}
