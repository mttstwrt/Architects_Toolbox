package com.ArchitectsToolbox.client;

import com.ArchitectsToolbox.clipboard.Placement;
import com.ArchitectsToolbox.measure.MeasurementStore;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Writes a paste into the world for a player in creative (F38), and writes it back for undo. In singleplayer the
 * blocks go straight into the integrated server's level, on the server's thread. On a server they go as
 * {@code /fill} and {@code /setblock} commands, which need operator rights; vanilla never kicks an operator for
 * sending many. Either way a queue paces the writes over client ticks. Block entities are placed empty. Client thread.
 */
final class WorldPlacer {
    private static final int BLOCKS_PER_TICK = 32_768;
    private static final int COMMANDS_PER_TICK = 32;
    /** Set without neighbour updates, as {@code /setblock} does, so nothing pasted breaks or falls on the way in. */
    private static final int FLAGS = Block.UPDATE_CLIENTS;

    private final ArrayDeque<Write> queue = new ArrayDeque<>();

    /** How the player can place blocks here, if at all. */
    enum Route {
        DIRECT,
        COMMANDS,
        NONE
    }

    static Route route(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            return Route.NONE;
        }
        if (minecraft.getSingleplayerServer() != null) {
            return Route.DIRECT;
        }
        return ClientCompat.canUseGameMasterCommands(minecraft.player) ? Route.COMMANDS : Route.NONE;
    }

    /**
     * Queues the placement's blocks, skipping blocks in chunks the client has not loaded, blocks already as pasted,
     * and, unless {@code replace}, blocks that are not air. Returns the change for undo, or null if nothing changes.
     */
    @Nullable Change place(Minecraft minecraft, Placement placement, boolean replace) {
        ClientLevel level = minecraft.level;
        if (level == null || route(minecraft) == Route.NONE) {
            return null;
        }
        LongArrayList positions = new LongArrayList();
        List<BlockState> before = new ArrayList<>();
        List<BlockState> after = new ArrayList<>();
        for (int i = 0; i < placement.blocks().blockCount(); i++) {
            BlockPos pos = placement.pos(i);
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState now = level.getBlockState(pos);
            BlockState next = placement.blocks().state(i);
            if (now == next || !replace && !now.isAir()) {
                continue;
            }
            positions.add(pos.asLong());
            before.add(now);
            after.add(next);
        }
        if (positions.isEmpty()) {
            return null;
        }
        // In order of Y, then Z, then X, so that runs along X sit next to each other for /fill.
        int[] order = new int[positions.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        IntArrays.quickSort(order, (a, b) -> compareYzx(positions.getLong(a), positions.getLong(b)));
        long[] sortedPositions = new long[order.length];
        BlockState[] sortedBefore = new BlockState[order.length];
        BlockState[] sortedAfter = new BlockState[order.length];
        for (int i = 0; i < order.length; i++) {
            sortedPositions[i] = positions.getLong(order[i]);
            sortedBefore[i] = before.get(order[i]);
            sortedAfter[i] = after.get(order[i]);
        }
        Change change = new Change(level, sortedPositions, sortedBefore, sortedAfter);
        change.reapply();
        return change;
    }

    private static int compareYzx(long a, long b) {
        int byY = Integer.compare(BlockPos.getY(a), BlockPos.getY(b));
        if (byY != 0) {
            return byY;
        }
        int byZ = Integer.compare(BlockPos.getZ(a), BlockPos.getZ(b));
        return byZ != 0 ? byZ : Integer.compare(BlockPos.getX(a), BlockPos.getX(b));
    }

    /** Sends this tick's share of the queued writes. */
    void tick(Minecraft minecraft) {
        Write write = queue.peek();
        if (write == null) {
            return;
        }
        if (write.level != minecraft.level) {
            // The player left the world the writes were for.
            queue.clear();
            ClientCompat.actionBar(minecraft, Component.translatable("architects_toolbox.message.place_stopped"));
            return;
        }
        switch (route(minecraft)) {
            case DIRECT -> writeDirect(minecraft, write);
            case COMMANDS -> writeCommands(minecraft, write);
            case NONE -> {
                queue.clear();
                ClientCompat.actionBar(minecraft, Component.translatable("architects_toolbox.message.place_stopped"));
                return;
            }
        }
        if (write.done()) {
            queue.poll();
        }
    }

    private static void writeDirect(Minecraft minecraft, Write write) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        int from = write.next;
        int to = Math.min(write.positions.length, from + BLOCKS_PER_TICK);
        write.next = to;
        var dimension = write.level.dimension();
        server.execute(() -> {
            ServerLevel level = server.getLevel(dimension);
            if (level == null) {
                return;
            }
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int i = from; i < to; i++) {
                level.setBlock(pos.set(write.positions[i]), write.states[i], FLAGS);
            }
        });
    }

    /** Runs of the same block along X become one {@code /fill}; a single block is a {@code /setblock}. */
    private static void writeCommands(Minecraft minecraft, Write write) {
        for (int sent = 0; sent < COMMANDS_PER_TICK && !write.done(); sent++) {
            int start = write.next;
            int end = start;
            BlockState state = write.states[start];
            long first = write.positions[start];
            while (end + 1 < write.positions.length && write.states[end + 1] == state
                    && write.positions[end + 1] == BlockPos.offset(write.positions[end], 1, 0, 0)) {
                end++;
            }
            write.next = end + 1;
            String block = BlockStateParser.serialize(state);
            int x = BlockPos.getX(first);
            int y = BlockPos.getY(first);
            int z = BlockPos.getZ(first);
            String command = end == start
                    ? "setblock " + x + " " + y + " " + z + " " + block
                    : "fill " + x + " " + y + " " + z + " " + (x + end - start) + " " + y + " " + z + " " + block;
            minecraft.player.connection.sendCommand(command);
        }
    }

    /** Blocks to write, and how far the writing has got. */
    private static final class Write {
        final ClientLevel level;
        final long[] positions;
        final BlockState[] states;
        int next;

        Write(ClientLevel level, long[] positions, BlockState[] states) {
            this.level = level;
            this.positions = positions;
            this.states = states;
        }

        boolean done() {
            return next >= positions.length;
        }
    }

    /** Blocks written by one paste: what was there before and what was put there, for undo and redo. */
    final class Change implements MeasurementStore.WorldChange {
        private final ClientLevel level;
        private final long[] positions;
        private final BlockState[] before;
        private final BlockState[] after;

        private Change(ClientLevel level, long[] positions, BlockState[] before, BlockState[] after) {
            this.level = level;
            this.positions = positions;
            this.before = before;
            this.after = after;
        }

        int size() {
            return positions.length;
        }

        @Override
        public void revert() {
            queue.add(new Write(level, positions, before));
        }

        @Override
        public void reapply() {
            queue.add(new Write(level, positions, after));
        }
    }
}
