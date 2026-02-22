package com.ArchitectsToolbox.render;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.renderer.MultiBufferSource;

public class RenderBuffer {
    private final ByteBufferBuilder buffer;

    public RenderBuffer(int initialCapacity) {
        this.buffer = new ByteBufferBuilder(initialCapacity);
    }

    public MultiBufferSource.BufferSource createSource() {
        return MultiBufferSource.immediate(buffer);
    }
}
