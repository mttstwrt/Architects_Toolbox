package com.ArchitectsToolbox.client;

import com.ArchitectsToolbox.gui.Wheel;
import com.ArchitectsToolbox.gui.WheelScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * The mod's keys, read every client tick. The measure key: a tap adds the targeted block, or confirms the paste
 * preview while one is open; holding it opens the wheel, which the wheel screen closes on release. The layer keys
 * step the layer view, the rotate keys turn the paste preview, and the preview key turns the next-point preview on
 * and off. Also moves the paste preview to the block face under the crosshair. Client thread.
 */
final class MeasureInput {
    /** Held longer than this, in milliseconds, the measure key opens the wheel. */
    private static final long HOLD = 200;
    /** How far the crosshair reaches for a block, as in 1.0.0. */
    private static final double REACH = 512;

    private final Operations operations;
    private final Wheel wheel;
    private long pressedAt;
    private boolean wheelOpened;

    MeasureInput(Operations operations, Wheel wheel) {
        this.operations = operations;
        this.wheel = wheel;
    }

    void tick(Minecraft minecraft) {
        boolean noScreen = ClientCompat.screen(minecraft) == null;
        // A press and release between two ticks is never seen as down, only as a click.
        boolean clicked = false;
        while (ToolboxKeys.MEASURE.consumeClick()) {
            clicked = true;
        }
        // Opening a screen releases every key mapping, so the key reads as released once the wheel is open.
        if (ToolboxKeys.MEASURE.isDown()) {
            long now = System.currentTimeMillis();
            if (pressedAt == 0) {
                pressedAt = now;
                wheelOpened = false;
            } else if (!wheelOpened && noScreen && now - pressedAt > HOLD) {
                ClientCompat.setScreen(minecraft, new WheelScreen(wheel));
                wheelOpened = true;
            }
        } else {
            boolean tapped = pressedAt != 0 ? !wheelOpened : clicked;
            if (tapped && noScreen) {
                if (operations.pasting()) {
                    operations.confirmPaste();
                } else {
                    BlockPos target = target(minecraft);
                    if (target != null) {
                        operations.addPoint(target);
                    }
                }
            }
            pressedAt = 0;
            wheelOpened = false;
        }
        while (ToolboxKeys.LAYER_UP.consumeClick()) {
            stepLayer(minecraft, 1, noScreen);
        }
        while (ToolboxKeys.LAYER_DOWN.consumeClick()) {
            stepLayer(minecraft, -1, noScreen);
        }
        while (ToolboxKeys.ROTATE_LEFT.consumeClick()) {
            rotate(minecraft, -1, noScreen);
        }
        while (ToolboxKeys.ROTATE_RIGHT.consumeClick()) {
            rotate(minecraft, 1, noScreen);
        }
        if (operations.pasting() && noScreen) {
            BlockHitResult hit = hit(minecraft);
            operations.setPasteOrigin(hit != null ? hit.getBlockPos().relative(hit.getDirection()) : null);
        }
        while (ToolboxKeys.NEXT_COLOUR.consumeClick()) {
            if (noScreen) {
                operations.nextColour();
            }
        }
        while (ToolboxKeys.TOGGLE_PREVIEW.consumeClick()) {
            if (noScreen) {
                operations.togglePreview();
                ClientCompat.actionBar(minecraft, Component.translatable("architects_toolbox.wheel.preview",
                        Component.translatable(operations.views().preview() ? "architects_toolbox.wheel.on" : "architects_toolbox.wheel.off")));
            }
        }
    }

    private void rotate(Minecraft minecraft, int step, boolean noScreen) {
        if (noScreen && operations.rotatePaste(step)) {
            ClientCompat.actionBar(minecraft, Component.translatable("architects_toolbox.message.rotation", operations.rotationDegrees()));
        }
    }

    private void stepLayer(Minecraft minecraft, int step, boolean noScreen) {
        if (noScreen && operations.stepLayer(step)) {
            showLayer(minecraft, operations);
        }
    }

    /** "Layer: n/N (Y=y)" in the action bar. */
    static void showLayer(Minecraft minecraft, Operations operations) {
        var box = operations.layerBounds();
        if (!operations.views().layer() || box == null) {
            return;
        }
        int y = operations.views().layerY();
        ClientCompat.actionBar(minecraft, Component.translatable("architects_toolbox.message.layer", y - box.minY() + 1, box.sizeY(), y));
    }

    /** The block under the crosshair, fluids ignored, or null. */
    static @Nullable BlockPos target(Minecraft minecraft) {
        BlockHitResult hit = hit(minecraft);
        return hit != null ? hit.getBlockPos() : null;
    }

    private static @Nullable BlockHitResult hit(Minecraft minecraft) {
        Entity camera = minecraft.getCameraEntity();
        if (minecraft.level == null || camera == null) {
            return null;
        }
        HitResult hit = camera.pick(REACH, 1.0f, false);
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK ? block : null;
    }
}
