package com.ArchitectsToolbox.mixin;

import com.ArchitectsToolbox.render.MeasurementRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class WorldRendererMixin {

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void onRenderEnd(DeltaTracker deltaTracker, boolean renderBlockOutline,
                             Camera camera, GameRenderer gameRenderer, LightTexture lightTexture,
                             Matrix4f positionMatrix, Matrix4f projectionMatrix,
                             CallbackInfo ci) {
        MeasurementRenderer.getInstance().render(camera, positionMatrix);
    }
}
