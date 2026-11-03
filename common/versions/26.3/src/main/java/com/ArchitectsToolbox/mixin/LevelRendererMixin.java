package com.ArchitectsToolbox.mixin;

import com.ArchitectsToolbox.render.WorldRenderLayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the mod's scene to the frame's submit queue in {@code LevelRenderer.submitFeatures}, after the block outline
 * and before the debug gizmos, where the game queues its own world overlays. A mixin rather than a loader event keeps
 * both loaders at the same place.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Inject(method = "submitFeatures", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;finalizeGizmoCollection()V"))
    private void architects_toolbox$submitScene(LevelRenderState levelRenderState, SubmitNodeCollector collector, boolean renderOutline, CallbackInfo callback) {
        WorldRenderLayer.submit(levelRenderState.cameraRenderState, collector);
    }
}
