package com.ArchitectsToolbox.mixin;

import com.ArchitectsToolbox.render.WorldRenderLayer;
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

/**
 * Draws the mod's scene at the end of {@code LevelRenderer.renderLevel}, over the finished level, as 1.0.0 did. A
 * mixin rather than a loader event keeps both loaders at the same place.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void architects_toolbox$drawScene(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
                                              LightTexture lightTexture, Matrix4f viewRotation, Matrix4f projection, CallbackInfo callback) {
        WorldRenderLayer.draw(camera, viewRotation);
    }
}
