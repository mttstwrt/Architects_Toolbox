package com.ArchitectsToolbox.render;

import com.ArchitectsToolbox.clipboard.Clipboard;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * What is drawn for a clipboard wherever it is placed: its ghost blocks and the outline of its exposed faces, both
 * relative to its lowest corner, and the same for each single layer the layer view shows. The outline gets its
 * colour where it is placed. Built the first time a scene asks, and kept as long as the clipboard is. Scene worker
 * thread only, like the cache.
 */
final class ClipboardMeshes {
    /** More faces than this and the outline is the clipboard's box instead (the hollow view's limit). */
    private static final int OUTLINE_FACES = 200_000;
    /** More shown blocks than this and no models are drawn. */
    static final int GHOST_LIMIT = 250_000;

    private static final Map<Clipboard, ClipboardMeshes> CACHE = new WeakHashMap<>();

    private final Clipboard clipboard;
    private final Part whole;
    private final Int2ObjectOpenHashMap<Part> layers = new Int2ObjectOpenHashMap<>();

    /** Ghosts (null if over {@link #GHOST_LIMIT}) and outline, relative to the clipboard's lowest corner. */
    record Part(@Nullable GhostBlocks ghosts, LineMesh outline) {}

    private ClipboardMeshes(Clipboard clipboard) {
        this.clipboard = clipboard;
        IntArrayList all = new IntArrayList(clipboard.blockCount());
        for (int i = 0; i < clipboard.blockCount(); i++) {
            all.add(i);
        }
        this.whole = build(all);
    }

    static ClipboardMeshes of(Clipboard clipboard) {
        return CACHE.computeIfAbsent(clipboard, ClipboardMeshes::new);
    }

    Part whole() {
        return whole;
    }

    /** The blocks at relative height {@code y} only, outlined as a slice of their own; null if there are none. */
    @Nullable Part layer(int y) {
        if (y < 0 || y >= clipboard.sizeY()) {
            return null;
        }
        Part part = layers.get(y);
        if (part == null) {
            IntArrayList slice = new IntArrayList();
            for (int i = 0; i < clipboard.blockCount(); i++) {
                if (BlockPos.getY(clipboard.position(i)) == y) {
                    slice.add(i);
                }
            }
            part = build(slice);
            layers.put(y, part);
        }
        // A layer with blocks always has an outline.
        return part.outline().lineCount() == 0 ? null : part;
    }

    private Part build(IntArrayList blocks) {
        GhostBlocks ghosts = GhostBlocks.of(blocks.size(), i -> clipboard.position(blocks.getInt(i)), i -> clipboard.state(blocks.getInt(i)));
        LongOpenHashSet occupied = new LongOpenHashSet(blocks.size());
        for (int i = 0; i < blocks.size(); i++) {
            occupied.add(clipboard.position(blocks.getInt(i)));
        }
        LineMesh.Builder lines = LineMesh.builder(0, 0, 0);
        BlockEdges edges = new BlockEdges(0, 0, 0);
        int faces = 0;
        for (int i = 0; i < blocks.size() && faces <= OUTLINE_FACES; i++) {
            long pos = clipboard.position(blocks.getInt(i));
            for (Direction face : Direction.values()) {
                if (!occupied.contains(BlockPos.offset(pos, face))) {
                    edges.addFace(BlockPos.getX(pos), BlockPos.getY(pos), BlockPos.getZ(pos), face);
                    faces++;
                }
            }
        }
        if (faces <= OUTLINE_FACES) {
            edges.addTo(lines, -1);
        } else {
            lines.box(0, 0, 0, clipboard.sizeX(), clipboard.sizeY(), clipboard.sizeZ(), -1);
        }
        return new Part(ghosts.size() > GHOST_LIMIT ? null : ghosts, lines.build());
    }
}
