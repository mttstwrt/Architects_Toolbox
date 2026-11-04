package com.ArchitectsToolbox.overlay;

import com.ArchitectsToolbox.client.ClientCompat;
import com.ArchitectsToolbox.measure.ViewSettings;
import com.ArchitectsToolbox.render.WorldScene;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * The world overlays (F40 to F42): which are on comes from {@link ViewSettings}, and this keeps each one's lines and
 * labels, rebuilding one only when what it shows may have changed. Chunk boundaries follow the player's chunk and
 * height, biome borders the player's position, and structure bounds the player's chunk and the chunks the client has
 * loaded. Client thread; the structure lookups run on the integrated server's thread and come back here.
 */
public final class WorldOverlays {
    /** How far the player moves before the biome borders are built again (1.0.0's distance). */
    private static final int BIOME_MOVE = 4;
    private static final int BIOME_CLIMB = 8;

    private final ViewSettings views;
    private @Nullable ClientLevel level;
    private StructureScanner scanner = new StructureScanner();
    private int loadedChunks;

    private WorldScene chunks = WorldScene.EMPTY;
    private @Nullable BlockPos chunksBuiltAt;

    private WorldScene biomes = WorldScene.EMPTY;
    private @Nullable BlockPos biomesBuiltAt;
    private boolean biomesComplete;

    private List<StructureScanner.Found> structures = List.of();
    private WorldScene structureScene = WorldScene.EMPTY;
    private boolean scanning;
    /** Something structure bounds show may have changed since the last lookup was sent. */
    private boolean structuresStale;
    private long structuresChunk;
    private boolean structurePiecesShown;

    private WorldScene scene = WorldScene.EMPTY;
    private boolean changed;

    public WorldOverlays(ViewSettings views) {
        this.views = views;
    }

    /** The scanner for the current world, which {@code /atb identify} shares. */
    public StructureScanner scanner() {
        return scanner;
    }

    /** All overlays that are on, together. */
    public WorldScene scene() {
        return scene;
    }

    /** Brings the overlays up to date; returns whether {@link #scene} changed. */
    public boolean tick(Minecraft minecraft) {
        ClientLevel current = minecraft.level;
        if (current != level) {
            level = current;
            scanner = new StructureScanner();
            scanning = false;
            structures = List.of();
            chunks = biomes = structureScene = WorldScene.EMPTY;
            chunksBuiltAt = null;
            biomesBuiltAt = null;
            structuresStale = true;
            changed = true;
        }
        if (current == null || minecraft.player == null) {
            chunks = biomes = structureScene = WorldScene.EMPTY;
            return finish();
        }
        BlockPos player = minecraft.player.blockPosition();
        int minY = ClientCompat.minY(current);
        int topY = minY + current.getHeight();
        int loaded = current.getChunkSource().getLoadedChunksCount();
        boolean chunksLoaded = loaded != loadedChunks;
        loadedChunks = loaded;

        updateChunks(player, minY, topY);
        updateBiomes(current, player, minY, topY, chunksLoaded);
        updateStructures(minecraft, current, player, chunksLoaded);
        return finish();
    }

    private void updateChunks(BlockPos player, int minY, int topY) {
        if (!views.chunks()) {
            if (chunksBuiltAt != null) {
                chunks = WorldScene.EMPTY;
                chunksBuiltAt = null;
                changed = true;
            }
            return;
        }
        if (chunksBuiltAt == null || (chunksBuiltAt.getX() >> 4) != (player.getX() >> 4) || (chunksBuiltAt.getZ() >> 4) != (player.getZ() >> 4)
                || chunksBuiltAt.getY() != player.getY()) {
            chunks = ChunkGrid.build(player.getX() >> 4, player.getZ() >> 4, player.getY(), minY, topY);
            chunksBuiltAt = player;
            changed = true;
        }
    }

    private void updateBiomes(ClientLevel current, BlockPos player, int minY, int topY, boolean chunksLoaded) {
        if (!views.biomes()) {
            if (biomesBuiltAt != null) {
                biomes = WorldScene.EMPTY;
                biomesBuiltAt = null;
                changed = true;
            }
            return;
        }
        boolean moved = biomesBuiltAt == null
                || Math.hypot(player.getX() - biomesBuiltAt.getX(), player.getZ() - biomesBuiltAt.getZ()) > BIOME_MOVE
                || Math.abs(player.getY() - biomesBuiltAt.getY()) > BIOME_CLIMB;
        if (moved || !biomesComplete && chunksLoaded) {
            BiomeBorders.Result result = BiomeBorders.build(current, player, minY, topY);
            biomes = result.scene();
            biomesComplete = result.complete();
            biomesBuiltAt = player;
            changed = true;
        }
    }

    private void updateStructures(Minecraft minecraft, ClientLevel current, BlockPos player, boolean chunksLoaded) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (!views.structures() || server == null) {
            if (!structures.isEmpty() || structureScene != WorldScene.EMPTY) {
                structures = List.of();
                structureScene = WorldScene.EMPTY;
                changed = true;
            }
            structuresStale = true;
            return;
        }
        long chunk = BlockPos.asLong(player.getX() >> 4, 0, player.getZ() >> 4);
        if (chunk != structuresChunk || chunksLoaded) {
            structuresChunk = chunk;
            structuresStale = true;
        }
        if (structuresStale && !scanning) {
            structuresStale = false;
            scanning = true;
            StructureScanner forScanner = scanner;
            scanner.scan(server, current.dimension(), loadedChunks(minecraft, current, player), minecraft, found -> {
                if (forScanner != scanner) {
                    // The world changed while the server looked.
                    return;
                }
                scanning = false;
                if (views.structures()) {
                    structures = found;
                    structureScene = StructureBounds.build(found, views.structurePieces());
                    structurePiecesShown = views.structurePieces();
                    changed = true;
                }
            });
        }
        if (structurePiecesShown != views.structurePieces()) {
            structurePiecesShown = views.structurePieces();
            structureScene = StructureBounds.build(structures, structurePiecesShown);
            changed = true;
        }
    }

    /** The chunks the client has loaded round the player, as X and Z in turn. */
    private static int[] loadedChunks(Minecraft minecraft, ClientLevel level, BlockPos player) {
        int radius = minecraft.options.getEffectiveRenderDistance() + 1;
        int centreX = player.getX() >> 4;
        int centreZ = player.getZ() >> 4;
        IntArrayList chunks = new IntArrayList();
        for (int x = centreX - radius; x <= centreX + radius; x++) {
            for (int z = centreZ - radius; z <= centreZ + radius; z++) {
                if (level.hasChunk(x, z)) {
                    chunks.add(x);
                    chunks.add(z);
                }
            }
        }
        return chunks.toIntArray();
    }

    private boolean finish() {
        if (!changed) {
            return false;
        }
        changed = false;
        scene = WorldScene.join(List.of(chunks, biomes, structureScene));
        return true;
    }
}
