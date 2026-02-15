package com.ArchitectsToolbox.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

public class RadialMenuScreen extends Screen {
    private static final int MENU_RADIUS = 150;
    private static final int INNER_RADIUS = 35;

    private final int triggerKey;
    private List<RadialMenuAction> actions;
    private int hoveredIndex = -1;

    public RadialMenuScreen(int triggerKey) {
        super(Component.literal("Measurement Options"));
        this.triggerKey = triggerKey;
        this.actions = RadialMenuRegistry.getActions();
    }

    @Override
    protected void init() {
        super.init();
        actions = RadialMenuRegistry.getActions();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Dark overlay
        guiGraphics.fill(0, 0, width, height, 0xA0000000);

        int centerX = width / 2;
        int centerY = height / 2;

        if (actions == null || actions.isEmpty()) {
            guiGraphics.drawString(font, "No options", centerX - 30, centerY, 0xFFFFFFFF, true);
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }

        hoveredIndex = getHoveredIndex(mouseX, mouseY, centerX, centerY);

        int total = actions.size();
        double segmentAngle = 2.0 * Math.PI / total;

        // Draw each segment
        for (int i = 0; i < total; i++) {
            double startAngle = -Math.PI / 2 + i * segmentAngle;
            double midAngle = startAngle + segmentAngle / 2;

            RadialMenuAction action = actions.get(i);
            boolean isHovered = (i == hoveredIndex);
            boolean isEnabled = action.isEnabled();

            // Draw segment outline
            int outlineColor = isHovered ? 0xFFFFFFFF : 0xFF888888;
            drawSegmentOutline(guiGraphics, centerX, centerY, MENU_RADIUS, startAngle, startAngle + segmentAngle, outlineColor);

            // Draw text label at midpoint of segment
            int labelRadius = (INNER_RADIUS + MENU_RADIUS) / 2;
            int labelX = centerX + (int) (Math.cos(midAngle) * labelRadius);
            int labelY = centerY + (int) (Math.sin(midAngle) * labelRadius);

            String labelText = action.getName().getString();
            int textColor = isEnabled ? (isHovered ? 0xFFFFFFFF : 0xFFCCCCCC) : 0xFF666666;

            // Draw text centered at label position
            int textWidth = font.width(labelText);
            guiGraphics.drawString(font, labelText, labelX - textWidth / 2, labelY - 4, textColor, true);
        }

        // Draw center circle
        drawFilledCircle(guiGraphics, centerX, centerY, INNER_RADIUS, 0xEE222222);
        drawCircleOutline(guiGraphics, centerX, centerY, INNER_RADIUS, 0xFF888888);

        // Draw selected action name in center
        if (hoveredIndex >= 0 && hoveredIndex < actions.size()) {
            String name = actions.get(hoveredIndex).getName().getString();
            int textWidth = font.width(name);
            guiGraphics.drawString(font, name, centerX - textWidth / 2, centerY - 4, 0xFFFFFFFF, true);
        } else {
            String hint = "Move to select";
            int textWidth = font.width(hint);
            guiGraphics.drawString(font, hint, centerX - textWidth / 2, centerY - 4, 0xFF888888, true);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void drawSegmentOutline(GuiGraphics guiGraphics, int cx, int cy, int radius,
                                    double startAngle, double endAngle, int color) {
        // Draw arc outline with minimal rectangles
        int steps = 5;
        for (int i = 0; i <= steps; i++) {
            double angle = startAngle + (endAngle - startAngle) * i / steps;
            int x = cx + (int) (Math.cos(angle) * radius);
            int y = cy + (int) (Math.sin(angle) * radius);
            guiGraphics.fill(x - 1, y - 1, x + 2, y + 2, color);
        }
    }

    private void drawCircleOutline(GuiGraphics guiGraphics, int cx, int cy, int radius, int color) {
        int steps = 12;
        for (int i = 0; i < steps; i++) {
            double angle = 2 * Math.PI * i / steps;
            int x = cx + (int) (Math.cos(angle) * radius);
            int y = cy + (int) (Math.sin(angle) * radius);
            guiGraphics.fill(x - 1, y - 1, x + 2, y + 2, color);
        }
    }

    private void drawFilledCircle(GuiGraphics guiGraphics, int cx, int cy, int radius, int color) {
        // Draw filled circle with horizontal slices - step by 4 for fewer calls
        for (int y = -radius; y <= radius; y += 4) {
            int halfWidth = (int) Math.sqrt(radius * radius - y * y);
            guiGraphics.fill(cx - halfWidth, cy + y, cx + halfWidth, cy + y + 4, color);
        }
    }

    private int getHoveredIndex(int mouseX, int mouseY, int centerX, int centerY) {
        if (actions == null || actions.isEmpty()) {
            return -1;
        }

        float dx = mouseX - centerX;
        float dy = mouseY - centerY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        if (distance < INNER_RADIUS || distance > MENU_RADIUS) {
            return -1;
        }

        double angle = Math.atan2(dy, dx);
        angle = angle + Math.PI / 2;  // Rotate so 0 is at top
        if (angle < 0) angle += 2 * Math.PI;

        double segmentAngle = 2 * Math.PI / actions.size();
        int index = (int) (angle / segmentAngle);

        return Math.min(index, actions.size() - 1);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (hoveredIndex >= 0 && actions != null && hoveredIndex < actions.size()) {
            RadialMenuAction action = actions.get(hoveredIndex);
            if (action.isEnabled() && action.onScroll(verticalAmount)) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyReleased(KeyEvent keyEvent) {
        if (keyEvent.key() == triggerKey) {
            if (hoveredIndex >= 0 && actions != null && hoveredIndex < actions.size()) {
                RadialMenuAction action = actions.get(hoveredIndex);
                if (action.isEnabled()) {
                    action.execute();
                }
            }
            this.onClose();
            return true;
        }
        return super.keyReleased(keyEvent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
