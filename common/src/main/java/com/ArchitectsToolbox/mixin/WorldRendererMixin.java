package com.ArchitectsToolbox.mixin;

import com.ArchitectsToolbox.render.MeasurementRenderer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class WorldRendererMixin {

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void onRenderEnd(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean renderBlockOutline,
                             Camera camera, Matrix4f positionMatrix, Matrix4f projectionMatrix, Matrix4f matrix3,
                             GpuBufferSlice bufferSlice, Vector4f vector, boolean flag,
                             CallbackInfo ci) {
        MeasurementRenderer.getInstance().render(camera, positionMatrix);
    }
}
