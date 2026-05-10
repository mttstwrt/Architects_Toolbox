package com.ArchitectsToolbox.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StructureBoundsRenderer implements ShapeRenderer {

    private static final RenderBuffer BUFFER = new RenderBuffer(16384);
    private static final int RENDER_RADIUS_CHUNKS = 8;
    private static final long CACHE_DURATION_MS = 2000;

    // Orange wireframe
    private static final float R = 1.0f, G = 0.6f, B = 0.2f, A = 0.8f;

    // Cache
    private List<StructureEntry> cachedStructures = null;
    private ChunkPos cachedPlayerChunk = null;
    private long lastScanTime = 0;

    private record StructureEntry(BoundingBox bounds, Identifier id, Vec3 labelPos) {}

    @Override
    public void render(Camera camera, Matrix4f viewMatrix, List<BlockPos> selection, RenderConfig config) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Singleplayer only
        var server = mc.getSingleplayerServer();
        if (server == null) return;

        // Update cache if needed
        ChunkPos playerChunk = new ChunkPos(mc.player.blockPosition());
        long now = System.currentTimeMillis();
        if (cachedStructures == null
                || !playerChunk.equals(cachedPlayerChunk)
                || now - lastScanTime > CACHE_DURATION_MS) {
            scanStructures(mc, server, playerChunk);
            cachedPlayerChunk = playerChunk;
            lastScanTime = now;
        }

        if (cachedStructures == null || cachedStructures.isEmpty()) return;

        // Render wireframe boxes
        Vec3 camPos = camera.position();
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(viewMatrix);

        MultiBufferSource.BufferSource source = BUFFER.createSource();
        VertexConsumer consumer = source.getBuffer(RenderTypes.LINES);
        Matrix4f matrix = poseStack.last().pose();

        for (StructureEntry entry : cachedStructures) {
            BoundingBox bb = entry.bounds();
            float minX = (float) (bb.minX() - camPos.x);
            float minY = (float) (bb.minY() - camPos.y);
            float minZ = (float) (bb.minZ() - camPos.z);
            float maxX = (float) (bb.maxX() + 1 - camPos.x);
            float maxY = (float) (bb.maxY() + 1 - camPos.y);
            float maxZ = (float) (bb.maxZ() + 1 - camPos.z);

            RenderUtils.drawBox(consumer, matrix, minX, minY, minZ, maxX, maxY, maxZ, R, G, B, A);
        }

        source.endBatch();

        // Render labels
        for (StructureEntry entry : cachedStructures) {
            double dist = camPos.distanceTo(entry.labelPos());
            if (dist < 256.0) {
                RenderUtils.renderFloatingText(camera, viewMatrix,
                        entry.id().toString(), entry.labelPos(), R, G, B);
            }
        }
    }

    private void scanStructures(Minecraft mc, net.minecraft.server.MinecraftServer server, ChunkPos playerChunk) {
        var serverLevel = server.getLevel(mc.level.dimension());
        if (serverLevel == null) {
            cachedStructures = List.of();
            return;
        }

        var registry = serverLevel.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        var structureManager = serverLevel.structureManager();

        int minCX = playerChunk.x - RENDER_RADIUS_CHUNKS;
        int maxCX = playerChunk.x + RENDER_RADIUS_CHUNKS;
        int minCZ = playerChunk.z - RENDER_RADIUS_CHUNKS;
        int maxCZ = playerChunk.z + RENDER_RADIUS_CHUNKS;
        int midY = (int) mc.player.getY();

        List<StructureEntry> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (var entry : registry.entrySet()) {
            Identifier id = entry.getKey().identifier();
            boolean found = false;
            for (int cx = minCX; cx <= maxCX && !found; cx++) {
                for (int cz = minCZ; cz <= maxCZ && !found; cz++) {
                    BlockPos sample = new BlockPos(cx * 16 + 8, midY, cz * 16 + 8);
                    var start = structureManager.getStructureAt(sample, entry.getValue());
                    if (start != null && !start.getPieces().isEmpty()) {
                        BoundingBox bb = start.getBoundingBox();
                        // Deduplicate: same structure type at same origin
                        String dedupeKey = id + "@" + bb.minX() + "," + bb.minY() + "," + bb.minZ();
                        if (seen.add(dedupeKey)) {
                            Vec3 labelPos = new Vec3(
                                    (bb.minX() + bb.maxX() + 1) / 2.0,
                                    bb.maxY() + 2.0,
                                    (bb.minZ() + bb.maxZ() + 1) / 2.0
                            );
                            results.add(new StructureEntry(bb, id, labelPos));
                        }
                        found = true;
                    }
                }
            }
        }

        cachedStructures = results;
    }
}
