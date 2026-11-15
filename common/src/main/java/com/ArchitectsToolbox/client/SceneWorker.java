package com.ArchitectsToolbox.client;

import com.ArchitectsToolbox.ArchitectsToolbox;
import com.ArchitectsToolbox.render.MeasurementScene;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

/**
 * Builds scenes on a thread of its own, so a large hollow view never holds up a frame. The newest request wins: a
 * build that is overtaken stops early and its result is dropped. {@link #request} and {@link #take} are called on
 * the client thread; the inputs are immutable. The thread is a daemon and ends with the game.
 */
final class SceneWorker {
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Architect's Toolbox scene");
        thread.setDaemon(true);
        return thread;
    });
    private final MeasurementScene.Builder builder = new MeasurementScene.Builder();
    private final AtomicInteger generation = new AtomicInteger();
    private final AtomicReference<MeasurementScene.Result> ready = new AtomicReference<>();
    private MeasurementScene.@Nullable Input requested;

    /** Starts a build unless the same input was the last one asked for. */
    void request(MeasurementScene.Input input) {
        if (Objects.equals(input, requested)) {
            return;
        }
        requested = input;
        int mine = generation.incrementAndGet();
        executor.execute(() -> {
            if (generation.get() != mine) {
                return;
            }
            try {
                MeasurementScene.Result result = builder.build(input, () -> generation.get() != mine);
                if (result != null && generation.get() == mine) {
                    ready.set(result);
                }
            } catch (RuntimeException e) {
                ArchitectsToolbox.LOGGER.error("Could not build the measurement scene", e);
            }
        });
    }

    /** The newest finished scene, once; null if there is none since the last call. */
    MeasurementScene.@Nullable Result take() {
        return ready.getAndSet(null);
    }
}
