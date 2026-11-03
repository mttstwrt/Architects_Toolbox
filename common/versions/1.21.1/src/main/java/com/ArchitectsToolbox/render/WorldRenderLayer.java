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
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The only code that touches the game's world-render API; each Minecraft version has its own copy of this class.
 *
 * <p>1.21.1: the scene is drawn every frame through the game's shared buffer source, once the level is drawn. What is
 * built once per change is the scene itself, and the model quads of its ghosts.
 *
 * <p>Client thread: {@link #show} from the tick, {@link #draw} from {@code LevelRendererMixin} at the end of the level.
 */
public final class WorldRenderLayer {
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int LABEL_BACKGROUND = 0x40000000;
    private static final float LABEL_SCALE = 0.025f;

    private static WorldScene scene = WorldScene.EMPTY;
    /** The model quads of each shown ghost mesh, prepared when the mesh is first drawn. */
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
        for (PreparedGhosts mesh : prepare(current.ghosts(), minecraft.getBlockRenderer())) {
            mesh.put(cam, poseStack, ghosts);
        }
        buffers.endBatch(RenderType.translucentMovingBlock());

        for (WorldLabel label : current.labels()) {
            poseStack.pushPose();
            poseStack.translate(label.pos().x - cam.x, label.pos().y - cam.y, label.pos().z - cam.z);
            poseStack.mulPose(camera.rotation());
            poseStack.scale(LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);
            FormattedCharSequence text = label.text().getVisualOrderText();
            minecraft.font.drawInBatch(text, -minecraft.font.width(text) / 2.0f, 0.0f, label.argb(), false, poseStack.last().pose(),
                    buffers, Font.DisplayMode.SEE_THROUGH, LABEL_BACKGROUND, LightTexture.FULL_BRIGHT);
            poseStack.popPose();
        }
        buffers.endBatch();
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

    /** Keeps the prepared quads of meshes still shown and prepares new ones; a mesh is matched by identity. */
    private static List<PreparedGhosts> prepare(List<GhostMesh> meshes, BlockRenderDispatcher blocks) {
        List<PreparedGhosts> next = new ArrayList<>(meshes.size());
        for (GhostMesh mesh : meshes) {
            PreparedGhosts kept = null;
            for (PreparedGhosts old : prepared) {
                if (old.mesh == mesh) {
                    kept = old;
                    break;
                }
            }
            next.add(kept != null ? kept : PreparedGhosts.of(mesh, blocks));
        }
        prepared = next;
        return next;
    }

    /** A ghost mesh's blocks with their model quads, collected once. */
    private record PreparedGhosts(GhostMesh mesh, List<BlockPos> positions, List<List<BakedQuad>> quads) {
        static PreparedGhosts of(GhostMesh mesh, BlockRenderDispatcher blocks) {
            List<BlockPos> positions = new ArrayList<>(mesh.blocks().size());
            List<List<BakedQuad>> quads = new ArrayList<>(mesh.blocks().size());
            RandomSource random = RandomSource.create();
            for (GhostMesh.Ghost ghost : mesh.blocks()) {
                var model = blocks.getBlockModel(ghost.state());
                List<BakedQuad> blockQuads = new ArrayList<>();
                for (Direction direction : DIRECTIONS) {
                    random.setSeed(ghost.state().getSeed(ghost.pos()));
                    blockQuads.addAll(model.getQuads(ghost.state(), direction, random));
                }
                random.setSeed(ghost.state().getSeed(ghost.pos()));
                blockQuads.addAll(model.getQuads(ghost.state(), null, random));
                positions.add(ghost.pos());
                quads.add(blockQuads);
            }
            return new PreparedGhosts(mesh, positions, quads);
        }

        void put(Vec3 cam, PoseStack poseStack, VertexConsumer buffer) {
            float opacity = mesh.opacity();
            for (int i = 0; i < positions.size(); i++) {
                BlockPos pos = positions.get(i);
                poseStack.pushPose();
                poseStack.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
                for (BakedQuad quad : quads.get(i)) {
                    buffer.putBulkData(poseStack.last(), quad, 1.0f, 1.0f, 1.0f, opacity, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                }
                poseStack.popPose();
            }
        }
    }
}
