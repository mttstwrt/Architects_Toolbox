package com.ArchitectsToolbox.client;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/** Where {@code ClientLevelMixin} reports changed blocks; the client sets the listener once. Client thread. */
public final class BlockChanges {
    private static @Nullable Consumer<BlockPos> listener;

    private BlockChanges() {
    }

    static void listen(Consumer<BlockPos> next) {
        listener = next;
    }

    public static void changed(BlockPos pos) {
        Consumer<BlockPos> current = listener;
        if (current != null) {
            current.accept(pos);
        }
    }
}
