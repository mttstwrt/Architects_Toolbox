package com.ArchitectsToolbox.render;

import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;
import java.util.List;

public interface ShapeRenderer {
    void render(Camera camera, Matrix4f viewMatrix, List<BlockPos> selection, RenderConfig config);
    default void renderLabels(Camera camera, Matrix4f viewMatrix, List<BlockPos> selection, RenderConfig config) {}
    record RenderConfig(float red, float green, float blue, float alpha, int subdivisions, boolean showLabels) {
        public static RenderConfig defaultConfig() {
            return new RenderConfig(1.0f, 0.3f, 0.3f, 1.0f, 0, true);
        }
    }
}
