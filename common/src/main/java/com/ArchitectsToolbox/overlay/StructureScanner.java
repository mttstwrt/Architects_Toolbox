package com.ArchitectsToolbox.overlay;

import com.ArchitectsToolbox.ArchitectsToolbox;
import com.ArchitectsToolbox.client.ClientCompat;
import com.ArchitectsToolbox.geometry.BlockBox;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jspecify.annotations.Nullable;

/**
 * Finds the structures that reach into given chunks, for the structure bounds overlay (F42) and {@code /atb identify}
 * (F50e). Structures are only known to the server, so this reads the integrated server's world, on the server's
 * thread: each loaded chunk records which structure starts reach into it, and each start is looked up once and kept.
 * Singleplayer only. One scanner serves one client world; a new world gets a new scanner, so the kept starts never
 * outlive it.
 */
public final class StructureScanner {
    /** One structure where it was generated: its id, its whole box and the boxes of its pieces. */
    public record Found(String id, BlockBox bounds, List<BlockBox> pieces) {}

    /**
     * Starts already looked up, by structure and the start's chunk; null where there was no valid start. Server
     * thread only.
     */
    private final Map<Structure, Long2ObjectOpenHashMap<Found>> known = new IdentityHashMap<>();

    /**
     * Finds the structures reaching into the chunks, of those the server has loaded, and hands them to {@code done}
     * on {@code client}. {@code chunks} holds chunk X and Z in turn; it must not change afterwards.
     */
    public void scan(IntegratedServer server, ResourceKey<Level> dimension, int[] chunks, Executor client, Consumer<List<Found>> done) {
        server.execute(() -> {
            List<Found> found = List.of();
            try {
                ServerLevel level = server.getLevel(dimension);
                if (level != null) {
                    found = scan(level, chunks);
                }
            } catch (RuntimeException e) {
                ArchitectsToolbox.LOGGER.error("Could not look up structures", e);
            }
            List<Found> result = found;
            client.execute(() -> done.accept(result));
        });
    }

    private List<Found> scan(ServerLevel level, int[] chunks) {
        Set<Found> found = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i + 1 < chunks.length; i += 2) {
            // Only chunks already loaded: looking must not load or generate any.
            LevelChunk chunk = level.getChunkSource().getChunkNow(chunks[i], chunks[i + 1]);
            if (chunk == null) {
                continue;
            }
            for (Map.Entry<Structure, LongSet> references : chunk.getAllReferences().entrySet()) {
                Structure structure = references.getKey();
                Long2ObjectOpenHashMap<Found> starts = known.computeIfAbsent(structure, s -> new Long2ObjectOpenHashMap<>());
                for (long start : references.getValue()) {
                    if (!starts.containsKey(start)) {
                        starts.put(start, lookUp(level, structure, start));
                    }
                    Found one = starts.get(start);
                    if (one != null) {
                        found.add(one);
                    }
                }
            }
        }
        return new ArrayList<>(found);
    }

    /** Reads the start from its chunk, which may load the chunk from disk, as the game's own lookups do. */
    private static @Nullable Found lookUp(ServerLevel level, Structure structure, long startChunk) {
        Found[] found = {null};
        level.structureManager().fillStartsForStructure(structure, LongSets.singleton(startChunk), start -> found[0] = describe(level, structure, start));
        return found[0];
    }

    private static Found describe(ServerLevel level, Structure structure, StructureStart start) {
        List<BlockBox> pieces = new ArrayList<>(start.getPieces().size());
        for (StructurePiece piece : start.getPieces()) {
            pieces.add(box(piece.getBoundingBox()));
        }
        return new Found(ClientCompat.structureId(level.registryAccess(), structure), box(start.getBoundingBox()), List.copyOf(pieces));
    }

    private static BlockBox box(BoundingBox box) {
        return new BlockBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }
}
