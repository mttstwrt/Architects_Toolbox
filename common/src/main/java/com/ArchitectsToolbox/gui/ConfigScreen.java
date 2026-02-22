package com.ArchitectsToolbox.gui;

import com.ArchitectsToolbox.ModConfig;
import com.ArchitectsToolbox.SelectionManager;
import com.ArchitectsToolbox.shapes.EllipsoidMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ConfigScreen extends Screen {
    private final Screen parent;
    private AbstractSliderButton opacitySlider;

    public ConfigScreen(Screen parent) {
        super(Component.literal("MeasurementTools Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = this.height / 4;

        // Ellipsoid Mode Toggle Button
        addRenderableWidget(Button.builder(
            getEllipsoidModeText(),
            button -> {
                toggleEllipsoidMode();
                button.setMessage(getEllipsoidModeText());
            })
            .bounds(centerX - 100, startY, 200, 20)
            .build()
        );

        // Ghost Block Render Mode Toggle Button
        addRenderableWidget(Button.builder(
            getGhostBlockRenderModeText(),
            button -> {
                toggleGhostBlockRenderMode();
                button.setMessage(getGhostBlockRenderModeText());
            })
            .bounds(centerX - 100, startY + 50, 200, 20)
            .build()
        );

        // Ghost Block Opacity Slider
        float currentOpacity = ModConfig.getInstance().getGhostBlockOpacity();
        opacitySlider = new AbstractSliderButton(centerX - 100, startY + 80, 200, 20,
                getOpacityText(currentOpacity), currentOpacity) {
            @Override
            protected void updateMessage() {
                setMessage(getOpacityText((float) this.value));
            }

            @Override
            protected void applyValue() {
                ModConfig.getInstance().setGhostBlockOpacity((float) this.value);
            }
        };
        addRenderableWidget(opacitySlider);

        // Done Button
        addRenderableWidget(Button.builder(
            Component.literal("Done"),
            button -> onClose())
            .bounds(centerX - 100, this.height - 40, 200, 20)
            .build()
        );
    }

    private Component getEllipsoidModeText() {
        EllipsoidMode mode = SelectionManager.getInstance().getEllipsoidMode();
        String modeName = mode == EllipsoidMode.FIT_TO_BOX ? "Fit to Box" : "Center + Radius";
        return Component.literal("Ellipsoid Mode: " + modeName);
    }

    private void toggleEllipsoidMode() {
        SelectionManager manager = SelectionManager.getInstance();
        if (manager.getEllipsoidMode() == EllipsoidMode.FIT_TO_BOX) {
            manager.setEllipsoidMode(EllipsoidMode.CENTER_RADIUS);
        } else {
            manager.setEllipsoidMode(EllipsoidMode.FIT_TO_BOX);
        }
    }

    private Component getGhostBlockRenderModeText() {
        ModConfig.GhostBlockRenderMode mode = ModConfig.getInstance().getGhostBlockRenderMode();
        String modeName = mode == ModConfig.GhostBlockRenderMode.WIREFRAME ? "Wireframe" : "Solid";
        return Component.literal("Ghost Block Style: " + modeName);
    }

    private void toggleGhostBlockRenderMode() {
        ModConfig config = ModConfig.getInstance();
        if (config.getGhostBlockRenderMode() == ModConfig.GhostBlockRenderMode.WIREFRAME) {
            config.setGhostBlockRenderMode(ModConfig.GhostBlockRenderMode.SOLID);
        } else {
            config.setGhostBlockRenderMode(ModConfig.GhostBlockRenderMode.WIREFRAME);
        }
    }

    private Component getOpacityText(float opacity) {
        return Component.literal("Ghost Block Opacity: " + String.format("%.0f%%", opacity * 100));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Draw title
        guiGraphics.drawCenteredString(
            this.font,
            this.title,
            this.width / 2,
            20,
            0xFFFFFF
        );

        // Draw ellipsoid mode description
        String desc1 = "Fit to Box: Ellipsoid fits inside the selection bounding box";
        String desc2 = "Center + Radius: First block is center, furthest block defines radius";

        int descY = this.height / 4 + 25;
        guiGraphics.drawCenteredString(this.font, desc1, this.width / 2, descY, 0xAAAAAA);
        guiGraphics.drawCenteredString(this.font, desc2, this.width / 2, descY + 10, 0xAAAAAA);

        // Draw ghost block settings description
        int ghostDescY = this.height / 4 + 105;
        guiGraphics.drawCenteredString(this.font,
            "Wireframe shows block outlines, Solid shows filled blocks",
            this.width / 2, ghostDescY, 0xAAAAAA);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }
}
