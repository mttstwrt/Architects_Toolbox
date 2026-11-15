package com.ArchitectsToolbox.client;

import com.ArchitectsToolbox.geometry.BlockBox;
import com.ArchitectsToolbox.geometry.PathShape;
import com.ArchitectsToolbox.geometry.Shape;
import com.ArchitectsToolbox.geometry.ShapeBlocks;
import com.ArchitectsToolbox.measure.Measurement;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Counts the non-air blocks inside one measurement's filled shape (F22). A count runs a slice per tick,
 * so a large shape never stalls the game, and runs again only when the measurement changes or a block inside it
 * does. Reads the client's world, so client thread only.
 */
final class BlockCounter {
    /** Shapes that would take more block reads than this are not counted. */
    static final long LIMIT = 64_000_000;
    /** A block change inside the shape starts a new count at most this often, in milliseconds. */
    private static final long RECOUNT_DELAY = 1_000;

    /** A finished or running count. {@code progress} is 1 once finished. */
    record Count(List<Entry> entries, long total, float progress) {}

    record Entry(Block block, int count) {}

    private @Nullable Measurement measurement;
    private @Nullable Shape shape;
    private @Nullable Count shown;
    private boolean tooLarge;
    private boolean changed;
    private long startedAt;
    // The count in progress.
    private long next;
    private long end;
    private long @Nullable [] pathBlocks;
    private final Object2IntOpenHashMap<Block> counting = new Object2IntOpenHashMap<>();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    /** Follows the measurement to count, or null to stop counting. */
    void track(@Nullable Measurement next) {
        if (next == null || next.isEmpty()) {
            measurement = null;
            shape = null;
            shown = null;
            pathBlocks = null;
            return;
        }
        if (next.equals(measurement)) {
            return;
        }
        measurement = next;
        shape = next.geometry();
        shown = null;
        tooLarge = ShapeBlocks.scanCost(shape) > LIMIT;
        changed = false;
        if (!tooLarge) {
            start();
        } else {
            pathBlocks = null;
            counting.clear();
        }
    }

    void blockChanged(BlockPos pos) {
        if (shape != null && shape.bounds().contains(pos.getX(), pos.getY(), pos.getZ())) {
            changed = true;
        }
    }

    boolean tooLarge() {
        return shape != null && tooLarge;
    }

    /** The count to show: the last finished one while a new one runs, or the running one if there is none. */
    @Nullable Count count() {
        return shape == null || tooLarge ? null : shown;
    }

    /** Advances at most {@code budget} scan positions and returns the number consumed. */
    int tick(@Nullable Level level, int budget) {
        if (shape == null || tooLarge || level == null || budget <= 0) {
            return 0;
        }
        if (changed && next >= end && System.currentTimeMillis() - startedAt >= RECOUNT_DELAY) {
            changed = false;
            start();
        }
        if (next >= end) {
            return 0;
        }
        long before = next;
        long stop = Math.min(end, next + budget);
        BlockBox box = shape.bounds();
        for (; next < stop; next++) {
            if (pathBlocks != null) {
                cursor.set(pathBlocks[(int) next]);
            } else {
                long i = next;
                int x = box.minX() + (int) (i % box.sizeX());
                i /= box.sizeX();
                int z = box.minZ() + (int) (i % box.sizeZ());
                int y = box.minY() + (int) (i / box.sizeZ());
                if (!shape.contains(x, y, z)) {
                    continue;
                }
                cursor.set(x, y, z);
            }
            BlockState state = level.getBlockState(cursor);
            if (!state.isAir()) {
                counting.addTo(state.getBlock(), 1);
            }
        }
        boolean finished = next >= end;
        if (finished || shown == null || shown.progress() < 1) {
            shown = snapshot(finished ? 1 : (float) next / end);
        }
        return (int) (next - before);
    }

    private void start() {
        startedAt = System.currentTimeMillis();
        counting.clear();
        next = 0;
        if (shape instanceof PathShape path) {
            pathBlocks = path.blocks().toLongArray();
            end = pathBlocks.length;
            if (end == 0) {
                shown = new Count(List.of(), 0, 1);
            }
        } else {
            pathBlocks = null;
            end = shape == null ? 0 : shape.bounds().volume();
        }
    }

    private Count snapshot(float progress) {
        List<Entry> entries = new ArrayList<>(counting.size());
        long total = 0;
        for (Object2IntMap.Entry<Block> entry : counting.object2IntEntrySet()) {
            entries.add(new Entry(entry.getKey(), entry.getIntValue()));
            total += entry.getIntValue();
        }
        entries.sort(Comparator.comparingInt(Entry::count).reversed());
        return new Count(List.copyOf(entries), total, progress);
    }
}
