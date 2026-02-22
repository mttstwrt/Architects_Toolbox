package com.ArchitectsToolbox.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;

/**
 * GPU-cached line buffer using VertexBuffer. Vertices are stored in world space;
 * camera offset is applied as a matrix uniform at draw time. Only rebuilds when
 * explicitly marked dirty.
 */
public class CachedLineBuffer implements AutoCloseable {

    private VertexBuffer vertexBuffer;
    private ByteBufferBuilder byteBufferBuilder;
    private BufferBuilder bufferBuilder;
    private boolean dirty = true;
    private boolean hasData = false;
    private boolean building = false;

    public CachedLineBuffer() {
        this.vertexBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
    }

    /**
     * Begin building vertex data. Call addLine() after this.
     */
    public void begin() {
        if (building) {
            // Discard previous incomplete build
            discardBuild();
        }
        byteBufferBuilder = new ByteBufferBuilder(65536);
        bufferBuilder = new BufferBuilder(byteBufferBuilder, VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        building = true;
    }

    /**
     * Add a line segment in world space. Computes normal from direction.
     */
    public void addLine(float x1, float y1, float z1,
                        float x2, float y2, float z2,
                        float r, float g, float b, float a) {
        if (!building) return;

        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6f) {
            dx = 0; dy = 1; dz = 0;
        } else {
            dx /= len; dy /= len; dz /= len;
        }

        bufferBuilder.addVertex(x1, y1, z1).setColor(r, g, b, a).setNormal(dx, dy, dz);
        bufferBuilder.addVertex(x2, y2, z2).setColor(r, g, b, a).setNormal(dx, dy, dz);
    }

    /**
     * Finish building and upload to GPU. Sets dirty=false.
     */
    public void upload() {
        if (!building) return;

        MeshData meshData = bufferBuilder.buildOrThrow();
        vertexBuffer.bind();
        vertexBuffer.upload(meshData);
        VertexBuffer.unbind();

        // Clean up CPU-side builder
        byteBufferBuilder.close();
        byteBufferBuilder = null;
        bufferBuilder = null;
        building = false;
        dirty = false;
        hasData = true;
    }

    /**
     * Draw the cached buffer with camera-adjusted modelView matrix.
     * Sets up RenderType.lines() render state.
     */
    public void draw(Matrix4f viewMatrix, double camX, double camY, double camZ) {
        if (!hasData) return;

        // Build camera-offset modelView matrix
        Matrix4f modelView = new Matrix4f(viewMatrix);
        modelView.translate((float) -camX, (float) -camY, (float) -camZ);

        // Get projection matrix
        Matrix4f projectionMatrix = RenderSystem.getProjectionMatrix();

        // Set up line rendering state
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.lineWidth(2.0f);

        ShaderInstance shader = GameRenderer.getRendertypeLinesShader();
        if (shader == null) return;

        vertexBuffer.bind();
        vertexBuffer.drawWithShader(modelView, projectionMatrix, shader);
        VertexBuffer.unbind();

        RenderSystem.disableBlend();
    }

    /**
     * Mark buffer as needing rebuild on next render.
     */
    public void markDirty() {
        dirty = true;
    }

    /**
     * Check if buffer needs rebuild.
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Discard an in-progress build without uploading.
     */
    private void discardBuild() {
        if (byteBufferBuilder != null) {
            byteBufferBuilder.close();
            byteBufferBuilder = null;
        }
        bufferBuilder = null;
        building = false;
    }

    @Override
    public void close() {
        discardBuild();
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }
        hasData = false;
    }
}
