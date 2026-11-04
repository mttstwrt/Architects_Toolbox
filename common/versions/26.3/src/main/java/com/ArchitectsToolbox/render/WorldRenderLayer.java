package com.ArchitectsToolbox.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The only code that touches the game's world-render API; each Minecraft version has its own copy of this class.
 *
 * <p>26.3: the scene goes into the game's own submit queue every frame, lines and ghost blocks as custom geometry and
 * labels as text, and the game draws it with its own pipelines on OpenGL and Vulkan alike. The mod keeps no pipeline
 * or GPU buffer of its own. What is built once per change is the scene itself, and the model quads of its ghost
 * blocks, which are kept while the same blocks are shown, wherever they move.
 *
 * <p>Client thread: {@link #show} from the tick, {@link #submit} from {@code LevelRendererMixin} while the level is drawn.
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

    /** Queues the scene for this frame. Positions are sent relative to the camera, as the game's own submits are. */
    public static void submit(CameraRenderState camera, SubmitNodeCollector collector) {
        WorldScene current = scene;
        if (current.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 cam = camera.pos;
        PoseStack poseStack = new PoseStack();
        float lineWidth = minecraft.gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;
        for (LineMesh mesh : current.lines()) {
            poseStack.pushPose();
            poseStack.translate(mesh.originX() - cam.x, mesh.originY() - cam.y, mesh.originZ() - cam.z);
            collector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> putLines(mesh, pose, buffer, lineWidth));
            poseStack.popPose();
        }
        List<PreparedGhosts> ghosts = prepare(current.ghosts(), minecraft.getModelManager().getBlockStateModelSet());
        for (int i = 0; i < ghosts.size(); i++) {
            GhostMesh mesh = current.ghosts().get(i);
            PreparedGhosts blocks = ghosts.get(i);
            QuadInstance instance = new QuadInstance();
            instance.setColor(ARGB.white(mesh.opacity()));
            instance.setLightCoords(LightCoordsUtil.FULL_BRIGHT);
            instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
            poseStack.pushPose();
            poseStack.translate(mesh.originX() - cam.x, mesh.originY() - cam.y, mesh.originZ() - cam.z);
            collector.submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(), (pose, buffer) -> blocks.put(pose, buffer, instance));
            poseStack.popPose();
        }
        for (WorldLabel label : current.labels()) {
            double distance = label.pos().distanceTo(cam);
            if (distance > LABEL_RANGE) {
                continue;
            }
            float scale = labelScale(distance);
            poseStack.pushPose();
            poseStack.translate(label.pos().x - cam.x, label.pos().y - cam.y, label.pos().z - cam.z);
            poseStack.rotate(camera.orientation);
            poseStack.scale(scale, -scale, scale);
            FormattedCharSequence text = label.text().getVisualOrderText();
            collector.submitText(poseStack, -minecraft.font.width(text) / 2.0f, 0.0f, text, false, Font.DisplayMode.SEE_THROUGH,
                    LightCoordsUtil.FULL_BRIGHT, label.argb(), LABEL_BACKGROUND, 0);
            poseStack.popPose();
        }
    }

    /** Larger further away, so labels stay readable, as in 1.0.0. */
    private static float labelScale(double distance) {
        return (float) Math.clamp(0.015 + 0.002 * distance, 0.02, 0.08);
    }

    private static void putLines(LineMesh mesh, PoseStack.Pose pose, VertexConsumer buffer, float width) {
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
            // The line shaders widen each line across its direction, which they take from the normal.
            dx /= length;
            dy /= length;
            dz /= length;
            int color = mesh.color(line);
            buffer.addVertex(pose, ends[i], ends[i + 1], ends[i + 2]).setColor(color).setNormal(pose, dx, dy, dz).setLineWidth(width);
            buffer.addVertex(pose, ends[i + 3], ends[i + 4], ends[i + 5]).setColor(color).setNormal(pose, dx, dy, dz).setLineWidth(width);
        }
    }

    /**
     * Keeps the prepared quads of block sets still shown and prepares new ones, one per mesh in order; a set is
     * matched by identity, so a mesh that only moved keeps its quads.
     */
    private static List<PreparedGhosts> prepare(List<GhostMesh> meshes, BlockStateModelSet models) {
        List<PreparedGhosts> next = new ArrayList<>(meshes.size());
        for (GhostMesh mesh : meshes) {
            PreparedGhosts kept = null;
            for (PreparedGhosts old : prepared) {
                if (old.blocks == mesh.blocks()) {
                    kept = old;
                    break;
                }
            }
            next.add(kept != null ? kept : PreparedGhosts.of(mesh.blocks(), models));
        }
        prepared = next;
        return next;
    }

    /** A set of ghost blocks with the model quads of their faces that are not hidden, collected once. */
    private record PreparedGhosts(GhostBlocks blocks, List<List<BakedQuad>> quads) {
        static PreparedGhosts of(GhostBlocks blocks, BlockStateModelSet models) {
            List<List<BakedQuad>> quads = new ArrayList<>(blocks.size());
            List<BlockStateModelPart> parts = new ArrayList<>();
            RandomSource random = RandomSource.create();
            for (int i = 0; i < blocks.size(); i++) {
                BlockState state = blocks.state(i);
                parts.clear();
                random.setSeed(state.getSeed(BlockPos.of(blocks.position(i))));
                models.get(state).collectParts(random, parts);
                List<BakedQuad> blockQuads = new ArrayList<>();
                for (BlockStateModelPart part : parts) {
                    for (Direction direction : DIRECTIONS) {
                        if (!blocks.faceHidden(i, direction)) {
                            blockQuads.addAll(part.getQuads(direction));
                        }
                    }
                    blockQuads.addAll(part.getQuads(null));
                }
                quads.add(blockQuads);
            }
            return new PreparedGhosts(blocks, quads);
        }

        /** {@code pose} is at the mesh's origin. */
        void put(PoseStack.Pose pose, VertexConsumer buffer, QuadInstance instance) {
            PoseStack.Pose blockPose = pose.copy();
            for (int i = 0; i < quads.size(); i++) {
                long pos = blocks.position(i);
                blockPose.set(pose);
                blockPose.translate(BlockPos.getX(pos), BlockPos.getY(pos), BlockPos.getZ(pos));
                for (BakedQuad quad : quads.get(i)) {
                    buffer.putBakedQuad(blockPose, quad, instance);
                }
            }
        }
    }
}
