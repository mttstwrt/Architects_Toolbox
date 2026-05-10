package com.ArchitectsToolbox.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public class RenderUtils {

    private static final RenderBuffer LABEL_BUFFER = new RenderBuffer(256);

    public static void drawLine(VertexConsumer consumer, Matrix4f matrix,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float r, float g, float b, float a) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6f) {
            dx = 0; dy = 1; dz = 0;
        } else {
            dx /= len; dy /= len; dz /= len;
        }
        consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(dx, dy, dz);
        consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(dx, dy, dz);
    }

    public static void drawLine(VertexConsumer consumer, Matrix4f matrix,
                                double x1, double y1, double z1,
                                double x2, double y2, double z2,
                                float r, float g, float b, float a) {
        drawLine(consumer, matrix,
                (float) x1, (float) y1, (float) z1,
                (float) x2, (float) y2, (float) z2,
                r, g, b, a);
    }

    public static void drawBox(VertexConsumer consumer, Matrix4f matrix,
                               float minX, float minY, float minZ,
                               float maxX, float maxY, float maxZ,
                               float r, float g, float b, float a) {
        drawLine(consumer, matrix, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        drawLine(consumer, matrix, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        drawLine(consumer, matrix, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        drawLine(consumer, matrix, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);

        drawLine(consumer, matrix, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        drawLine(consumer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        drawLine(consumer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        drawLine(consumer, matrix, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);

        drawLine(consumer, matrix, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        drawLine(consumer, matrix, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        drawLine(consumer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        drawLine(consumer, matrix, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    public static void renderFloatingText(Camera camera, Matrix4f viewMatrix,
                                          String text, Vec3 position) {
        renderFloatingText(camera, viewMatrix, text, position, 1.0f, 1.0f, 1.0f);
    }

    public static void renderFloatingText(Camera camera, Matrix4f viewMatrix,
                                          String text, Vec3 position,
                                          float r, float g, float b) {
        Minecraft client = Minecraft.getInstance();
        Font font = client.font;

        Vec3 camPos = camera.position();
        double dist = camPos.distanceTo(position);
        if (dist > 256.0) return;

        // Scale based on distance (smaller when close, larger when far)
        float scale = (float) Math.max(0.02f, 0.015f + 0.002f * dist);
        scale = Math.min(scale, 0.08f);

        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(viewMatrix);
        poseStack.translate(
                position.x - camPos.x,
                position.y - camPos.y,
                position.z - camPos.z
        );

        Quaternionf rotation = camera.rotation();
        poseStack.mulPose(rotation);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
        poseStack.scale(-scale, -scale, scale);

        float halfWidth = font.width(text) / 2.0f;

        MultiBufferSource.BufferSource source = LABEL_BUFFER.createSource();

        int textColor = ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
        int alpha = 0xFF000000;

        font.drawInBatch(
                Component.literal(text),
                -halfWidth, 0,
                textColor | alpha,
                false,
                poseStack.last().pose(),
                source,
                Font.DisplayMode.SEE_THROUGH,
                0x40000000,
                0xF000F0
        );

        font.drawInBatch(
                Component.literal(text),
                -halfWidth, 0,
                textColor | alpha,
                false,
                poseStack.last().pose(),
                source,
                Font.DisplayMode.NORMAL,
                0,
                0xF000F0
        );

        source.endBatch();
    }

    public static int getBlockCount(int dx, int dy, int dz) {
        return dx * dy * dz;
    }
}
