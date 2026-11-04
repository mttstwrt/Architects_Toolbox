package com.ArchitectsToolbox.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The only code that touches the game's world-render API; each Minecraft version has its own copy of this class.
 *
 * <p>1.21.1: the scene is drawn every frame through the game's shared buffer source, once the level is drawn. What is
 * built once per change is the scene itself, and the model quads of its ghost blocks, which are kept while the same
 * blocks are shown, wherever they move.
 *
 * <p>Client thread: {@link #show} from the tick, {@link #draw} from {@code LevelRendererMixin} at the end of the level.
 */
public final class WorldRenderLayer {
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int LABEL_BACKGROUND = 0x40000000;
    /** Labels further than this are not drawn. */
    private static final double LABEL_RANGE = 256.0;

    private static WorldScene scene = WorldScene.EMPTY;
    /** The model quads of each shown set of ghost blocks, prepared when the set is first drawn. */
    private static List<PreparedGhosts> prepared = List.of();

    private WorldRenderLayer() {
    }

    public static void show(WorldScene next) {
        scene = next;
    }

    /** Draws the scene; {@code viewRotation} is the camera's rotation the level was drawn with. */
    public static void draw(Camera camera, Matrix4f viewRotation) {
        WorldScene current = scene;
        if (current.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Vec3 cam = camera.getPosition();
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(viewRotation);

        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (LineMesh mesh : current.lines()) {
            poseStack.pushPose();
            poseStack.translate(mesh.originX() - cam.x, mesh.originY() - cam.y, mesh.originZ() - cam.z);
            putLines(mesh, poseStack.last(), lines);
            poseStack.popPose();
        }
        buffers.endBatch(RenderType.lines());

        VertexConsumer ghosts = buffers.getBuffer(RenderType.translucentMovingBlock());
        List<PreparedGhosts> ready = prepare(current.ghosts(), minecraft.getBlockRenderer());
        for (int i = 0; i < ready.size(); i++) {
            GhostMesh mesh = current.ghosts().get(i);
            poseStack.pushPose();
            poseStack.translate(mesh.originX() - cam.x, mesh.originY() - cam.y, mesh.originZ() - cam.z);
            ready.get(i).put(poseStack, ghosts, mesh.opacity());
            poseStack.popPose();
        }
        buffers.endBatch(RenderType.translucentMovingBlock());

        for (WorldLabel label : current.labels()) {
            double distance = label.pos().distanceTo(cam);
            if (distance > LABEL_RANGE) {
                continue;
            }
            float scale = labelScale(distance);
            poseStack.pushPose();
            poseStack.translate(label.pos().x - cam.x, label.pos().y - cam.y, label.pos().z - cam.z);
            poseStack.mulPose(camera.rotation());
            poseStack.scale(scale, -scale, scale);
            FormattedCharSequence text = label.text().getVisualOrderText();
            minecraft.font.drawInBatch(text, -minecraft.font.width(text) / 2.0f, 0.0f, label.argb(), false, poseStack.last().pose(),
                    buffers, Font.DisplayMode.SEE_THROUGH, LABEL_BACKGROUND, LightTexture.FULL_BRIGHT);
            poseStack.popPose();
        }
        buffers.endBatch();
    }

    /** Larger further away, so labels stay readable, as in 1.0.0. */
    private static float labelScale(double distance) {
        return (float) Math.clamp(0.015 + 0.002 * distance, 0.02, 0.08);
    }

    private static void putLines(LineMesh mesh, PoseStack.Pose pose, VertexConsumer buffer) {
        float[] ends = mesh.ends();
        for (int line = 0; line < mesh.lineCount(); line++) {
            int i = LineMesh.endsOffset(line);
            float dx = ends[i + 3] - ends[i];
            float dy = ends[i + 4] - ends[i + 1];
            float dz = ends[i + 5] - ends[i + 2];
            float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length < 1.0e-6f) {
                continue;
            }
            // The line shader widens each line across its direction, which it takes from the normal.
            dx /= length;
            dy /= length;
            dz /= length;
            int color = mesh.color(line);
            buffer.addVertex(pose, ends[i], ends[i + 1], ends[i + 2]).setColor(color).setNormal(pose, dx, dy, dz);
            buffer.addVertex(pose, ends[i + 3], ends[i + 4], ends[i + 5]).setColor(color).setNormal(pose, dx, dy, dz);
        }
    }

    /**
     * Keeps the prepared quads of block sets still shown and prepares new ones, one per mesh in order; a set is
     * matched by identity, so a mesh that only moved keeps its quads.
     */
    private static List<PreparedGhosts> prepare(List<GhostMesh> meshes, BlockRenderDispatcher dispatcher) {
        List<PreparedGhosts> next = new ArrayList<>(meshes.size());
        for (GhostMesh mesh : meshes) {
            PreparedGhosts kept = null;
            for (PreparedGhosts old : prepared) {
                if (old.blocks == mesh.blocks()) {
                    kept = old;
                    break;
                }
            }
            next.add(kept != null ? kept : PreparedGhosts.of(mesh.blocks(), dispatcher));
        }
        prepared = next;
        return next;
    }

    /** A set of ghost blocks with the model quads of their faces that are not hidden, collected once. */
    private record PreparedGhosts(GhostBlocks blocks, List<List<BakedQuad>> quads) {
        static PreparedGhosts of(GhostBlocks blocks, BlockRenderDispatcher dispatcher) {
            List<List<BakedQuad>> quads = new ArrayList<>(blocks.size());
            RandomSource random = RandomSource.create();
            for (int i = 0; i < blocks.size(); i++) {
                BlockState state = blocks.state(i);
                long seed = state.getSeed(BlockPos.of(blocks.position(i)));
                var model = dispatcher.getBlockModel(state);
                List<BakedQuad> blockQuads = new ArrayList<>();
                for (Direction direction : DIRECTIONS) {
                    if (!blocks.faceHidden(i, direction)) {
                        random.setSeed(seed);
                        blockQuads.addAll(model.getQuads(state, direction, random));
                    }
                }
                random.setSeed(seed);
                blockQuads.addAll(model.getQuads(state, null, random));
                quads.add(blockQuads);
            }
            return new PreparedGhosts(blocks, quads);
        }

        /** {@code poseStack} is at the mesh's origin. */
        void put(PoseStack poseStack, VertexConsumer buffer, float opacity) {
            for (int i = 0; i < quads.size(); i++) {
                long pos = blocks.position(i);
                poseStack.pushPose();
                poseStack.translate(BlockPos.getX(pos), BlockPos.getY(pos), BlockPos.getZ(pos));
                for (BakedQuad quad : quads.get(i)) {
                    buffer.putBulkData(poseStack.last(), quad, 1.0f, 1.0f, 1.0f, opacity, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                }
                poseStack.popPose();
            }
        }
    }
}
