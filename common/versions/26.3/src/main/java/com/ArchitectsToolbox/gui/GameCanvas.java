package com.ArchitectsToolbox.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** {@link GuiCanvas} over 26.3's {@code GuiGraphicsExtractor}. */
public record GameCanvas(GuiGraphicsExtractor graphics) implements GuiCanvas {
    @Override
    public int width() {
        return graphics.guiWidth();
    }

    @Override
    public int height() {
        return graphics.guiHeight();
    }

    @Override
    public void fill(int x0, int y0, int x1, int y1, int argb) {
        graphics.fill(x0, y0, x1, y1, argb);
    }

    @Override
    public void text(Component text, int x, int y, int argb) {
        graphics.text(Minecraft.getInstance().font, text, x, y, argb, true);
    }

    @Override
    public int textWidth(Component text) {
        return Minecraft.getInstance().font.width(text);
    }

    @Override
    public void item(ItemStack stack, int x, int y) {
        graphics.item(stack, x, y);
    }
}
