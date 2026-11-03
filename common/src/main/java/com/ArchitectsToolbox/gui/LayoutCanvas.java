package com.ArchitectsToolbox.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** A canvas that only measures: for laying out the wheel's tabs when a click comes in between frames. */
record LayoutCanvas(Font font, int width, int height) implements GuiCanvas {
    @Override
    public void fill(int x0, int y0, int x1, int y1, int argb) {
    }

    @Override
    public void text(Component text, int x, int y, int argb) {
    }

    @Override
    public int textWidth(Component text) {
        return font.width(text);
    }

    @Override
    public void item(ItemStack stack, int x, int y) {
    }
}
