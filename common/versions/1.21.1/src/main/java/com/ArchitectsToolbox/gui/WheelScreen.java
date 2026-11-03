package com.ArchitectsToolbox.gui;

import com.ArchitectsToolbox.client.ToolboxKeys;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The screen that shows the {@link Wheel} while the measure key is held; each version has its own copy for its
 * screen API. It closes and runs the hovered item when whatever the measure key is bound to is released, keyboard
 * key or mouse button. 1.21.1.
 */
public final class WheelScreen extends Screen {
    private final Wheel wheel;

    public WheelScreen(Wheel wheel) {
        super(Component.translatable("architects_toolbox.wheel.title"));
        this.wheel = wheel;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        wheel.draw(new GameCanvas(graphics), mouseX, mouseY);
    }

    /** The wheel dims the game itself; no blur or menu background. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        wheel.scroll(scrollY, hasShiftDown());
        return true;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (ToolboxKeys.MEASURE.matchesMouse(button)) {
            return true;
        }
        if (button == 0 && wheel.click(new LayoutCanvas(font, width, height), x, y)) {
            onClose();
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (ToolboxKeys.MEASURE.matchesMouse(button)) {
            release();
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (ToolboxKeys.MEASURE.matches(keyCode, scanCode)) {
            release();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
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
