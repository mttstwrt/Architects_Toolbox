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
 * The mod's keys, read every client tick. The measure key: a tap adds the targeted block, holding it opens the
 * wheel, which the wheel screen closes on release. The layer keys step the layer view. Client thread.
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
                BlockPos target = target(minecraft);
                if (target != null) {
                    operations.addPoint(target);
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
    }

    private void stepLayer(Minecraft minecraft, int step, boolean noScreen) {
        if (noScreen && operations.stepLayer(step)) {
            showLayer(minecraft, operations);
        }
    }

    /** "Layer: n/N (Y=y)" in the action bar. */
    static void showLayer(Minecraft minecraft, Operations operations) {
        if (!operations.views().layer() || !operations.canShowLayer()) {
            return;
        }
        var box = operations.active().geometry().bounds();
        int y = operations.views().layerY();
        ClientCompat.actionBar(minecraft, Component.translatable("architects_toolbox.message.layer", y - box.minY() + 1, box.sizeY(), y));
    }

    /** The block under the crosshair, fluids ignored, or null. */
    static @Nullable BlockPos target(Minecraft minecraft) {
        Entity camera = minecraft.getCameraEntity();
        if (minecraft.level == null || camera == null) {
            return null;
        }
        HitResult hit = camera.pick(REACH, 1.0f, false);
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK ? block.getBlockPos() : null;
    }
}
