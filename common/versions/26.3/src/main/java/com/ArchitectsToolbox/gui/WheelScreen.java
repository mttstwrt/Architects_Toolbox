package com.ArchitectsToolbox.gui;

import com.ArchitectsToolbox.client.ToolboxKeys;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The screen that shows the {@link Wheel} while the measure key is held; each version has its own copy for its
 * screen API. It closes and runs the hovered item when whatever the measure key is bound to is released, keyboard
 * key or mouse button. 26.3.
 */
public final class WheelScreen extends Screen {
    private final Wheel wheel;

    public WheelScreen(Wheel wheel) {
        super(Component.translatable("architects_toolbox.wheel.title"));
        this.wheel = wheel;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        wheel.draw(new GameCanvas(graphics), mouseX, mouseY);
    }

    /** The wheel dims the game itself; no blur or menu background. */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        wheel.scroll(scrollY, minecraft.hasShiftDown());
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (ToolboxKeys.MEASURE.matchesMouse(event)) {
            return true;
        }
        if (event.button() == 0 && wheel.click(new LayoutCanvas(font, width, height), event.x(), event.y())) {
            onClose();
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (ToolboxKeys.MEASURE.matchesMouse(event)) {
            release();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (ToolboxKeys.MEASURE.matches(event)) {
            release();
            return true;
        }
        return super.keyReleased(event);
    }

    private void release() {
        wheel.runHovered();
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
