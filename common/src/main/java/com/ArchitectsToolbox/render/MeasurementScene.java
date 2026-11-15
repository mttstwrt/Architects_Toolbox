package com.ArchitectsToolbox.render;

import com.ArchitectsToolbox.clipboard.Placement;
import com.ArchitectsToolbox.geometry.BlockBox;
import com.ArchitectsToolbox.geometry.Shape;
import com.ArchitectsToolbox.geometry.ShapeBlocks;
import com.ArchitectsToolbox.measure.Measurement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Turns measurements, placements and their views into a {@link WorldScene}. Pure apart from the clipboard meshes it
 * keeps (see {@link ClipboardMeshes}): the input is immutable and the result is new, so it runs on the scene worker
 * thread.
 */
public final class MeasurementScene {
    /** More surface blocks than this and the hollow view gives way to the outline (1.0.0's limit). */
    public static final int HOLLOW_LIMIT = 50_000;
    /** More blocks than this to look at and the hollow and layer views are not built. */
    public static final long SCAN_LIMIT = 16_000_000;
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    /** Outline of a paste preview (F31) and of a locked placement (F32), as in 1.0.0. */
    private static final int PASTE_COLOUR = 0xFF80C8FF;
    private static final int LOCKED_COLOUR = 0xFF55FF55;

    /** The immutable view of one measurement; only changed measurements need new meshes. */
    public record Shown(Measurement measurement, boolean hollow, @Nullable Integer layerY) {}

    /**
     * What to draw. The preview carries the active measurement's ID, so it replaces only that measurement's labels.
     * Locked ghosts use the active layer separately; another measurement's layer never slices them.
     */
    public record Input(List<Shown> measurements, @Nullable Measurement preview, List<Placement> placements,
                        @Nullable Integer placementLayerY, @Nullable Placement paste, boolean ghostModels, float ghostOpacity) {
        public Input {
            measurements = List.copyOf(measurements);
            placements = List.copyOf(placements);
        }
    }

    /** The scene, and a reason the requested view could not be shown, if there is one. */
    public record Result(WorldScene scene, @Nullable Component warning) {}

    private MeasurementScene() {
    }

    /** Builds an uncached scene; the worker uses a persistent {@link Builder} instead. */
    public static @Nullable Result build(Input input, BooleanSupplier cancelled) {
        return new Builder().build(input, cancelled);
    }

    /**
     * The scene worker's committed meshes. Kept by stable measurement ID and replaced only when its data or view
     * changes: preview movement costs O(measurements + preview geometry), rather than rescanning every hollow shape.
     * Entries absent from the current request are removed, so undo history cannot retain old meshes. Worker thread only.
     */
    public static final class Builder {
        private record Cached(Shown input, Result result) {}

        private final Map<UUID, Cached> committed = new HashMap<>();

        public @Nullable Result build(Input input, BooleanSupplier cancelled) {
            List<LineMesh> meshes = new ArrayList<>();
            List<WorldLabel> labels = new ArrayList<>();
            Set<UUID> retained = new HashSet<>();
            for (Shown shown : input.measurements()) {
                retained.add(shown.measurement().id());
            }
            committed.keySet().retainAll(retained);
            Component warning = null;
            for (Shown shown : input.measurements()) {
                if (cancelled.getAsBoolean()) {
                    return null;
                }
                Measurement measurement = shown.measurement();
                Cached cached = committed.get(measurement.id());
                if (cached == null || !cached.input().equals(shown)) {
                    Result result = buildMeasurement(shown, cancelled);
                    if (result == null) {
                        return null;
                    }
                    cached = new Cached(shown, result);
                    committed.put(measurement.id(), cached);
                }
                Result result = cached.result();
                meshes.addAll(result.scene().lines());
                if (input.preview() == null || !measurement.id().equals(input.preview().id())) {
                    labels.addAll(result.scene().labels());
                }
                if (result.warning() != null) {
                    warning = result.warning();
                }
            }
            Measurement preview = input.preview();
            if (preview != null) {
                Shape shape = preview.geometry();
                BlockBox box = shape.bounds();
                LineMesh.Builder lines = LineMesh.builder(box.minX(), box.minY(), box.minZ());
                Outlines.add(preview, shape, new Outlines.Sink(lines, labels, lighter(preview.colour())), true);
                meshes.add(lines.build());
            }
            List<GhostMesh> ghosts = new ArrayList<>();
            boolean tooManyGhosts = false;
            for (Placement placement : input.placements()) {
                if (cancelled.getAsBoolean()) {
                    return null;
                }
                tooManyGhosts |= addPlacement(placement, input.placementLayerY(), LOCKED_COLOUR, input, meshes, ghosts);
            }
            if (input.paste() != null) {
                tooManyGhosts |= addPlacement(input.paste(), null, PASTE_COLOUR, input, meshes, ghosts);
            }
            if (tooManyGhosts && warning == null) {
                warning = Component.translatable("architects_toolbox.message.ghosts_too_many", ClipboardMeshes.GHOST_LIMIT);
            }
            return new Result(new WorldScene(meshes, ghosts, labels), warning);
        }
    }

    private static @Nullable Result buildMeasurement(Shown shown, BooleanSupplier cancelled) {
        Measurement measurement = shown.measurement();
        if (measurement.isEmpty()) {
            return new Result(WorldScene.EMPTY, null);
        }
        Shape shape = measurement.geometry();
        BlockBox box = shape.bounds();
        LineMesh.Builder lines = LineMesh.builder(box.minX(), box.minY(), box.minZ());
        List<WorldLabel> labels = new ArrayList<>();
        boolean blockView = shown.hollow() || shown.layerY() != null;
        Component problem = blockView ? blockViewProblem(shape) : null;
        if (blockView && problem == null) {
            BlockEdges edges = new BlockEdges(box.minX(), box.minY(), box.minZ());
            Boolean done = shown.layerY() != null
                    ? layer(shape, shown.hollow(), shown.layerY(), edges, cancelled)
                    : hollow(shape, edges, cancelled);
            if (done == null) {
                return null;
            }
            if (done) {
                edges.addTo(lines, measurement.colour());
                Outlines.add(measurement, shape, new Outlines.Sink(null, labels, measurement.colour()), false);
            } else {
                problem = Component.translatable("architects_toolbox.message.hollow_too_many", HOLLOW_LIMIT);
            }
        }
        if (!blockView || problem != null) {
            Outlines.add(measurement, shape, new Outlines.Sink(lines, labels, measurement.colour()), !shown.hollow());
        }
        return new Result(new WorldScene(List.of(lines.build()), List.of(), labels), problem);
    }

    /**
     * Adds a placement's outline, and its ghost models if they are on, or just the layer at {@code layerY}. Returns
     * true if the models were wanted but there are too many.
     */
    private static boolean addPlacement(Placement placement, @Nullable Integer layerY, int argb, Input input, List<LineMesh> lines,
                                        List<GhostMesh> ghosts) {
        ClipboardMeshes meshes = ClipboardMeshes.of(placement.blocks());
        BlockPos origin = placement.origin();
        ClipboardMeshes.Part part = layerY == null ? meshes.whole() : meshes.layer(layerY - origin.getY());
        if (part == null) {
            return false;
        }
        lines.add(part.outline().movedTo(origin.getX(), origin.getY(), origin.getZ(), argb));
        if (!input.ghostModels()) {
            return false;
        }
        if (part.ghosts() == null) {
            return true;
        }
        ghosts.add(new GhostMesh(part.ghosts(), origin.getX(), origin.getY(), origin.getZ(), input.ghostOpacity()));
        return false;
    }

    /** Why the shape is too big to look at block by block, or null if it is not. */
    private static @Nullable Component blockViewProblem(Shape shape) {
        if (ShapeBlocks.scanCost(shape) > SCAN_LIMIT) {
            return Component.translatable("architects_toolbox.message.too_large_to_scan", SCAN_LIMIT);
        }
        return null;
    }

    /**
     * Adds the exposed faces of the shape's surface blocks. Returns false if there are more than the limit, null if
     * cancelled.
     */
    private static @Nullable Boolean hollow(Shape shape, BlockEdges edges, BooleanSupplier cancelled) {
        int[] visited = {0};
        int[] surface = {0};
        boolean[] stop = {false};
        boolean finished = ShapeBlocks.forEach(shape, (x, y, z) -> {
            if ((++visited[0] & 0xFFFF) == 0 && cancelled.getAsBoolean()) {
                stop[0] = true;
                return false;
            }
            boolean exposed = false;
            for (Direction face : Direction.values()) {
                if (!shape.inside(x + face.getStepX(), y + face.getStepY(), z + face.getStepZ())) {
                    edges.addFace(x, y, z, face);
                    exposed = true;
                }
            }
            return !exposed || ++surface[0] <= HOLLOW_LIMIT;
        });
        if (stop[0]) {
            return null;
        }
        return finished;
    }

    /**
     * Adds one layer of the shape, or of its surface when hollow: the outline of each side a block of the layer shows
     * to a neighbour in the same layer that is not part of it.
     */
    private static @Nullable Boolean layer(Shape shape, boolean hollow, int y, BlockEdges edges, BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean()) {
            return null;
        }
        ShapeBlocks.forEachInLayer(shape, y, (x, layerY, z) -> {
            if (hollow && !shape.isSurface(x, layerY, z)) {
                return true;
            }
            for (Direction side : HORIZONTAL) {
                int nx = x + side.getStepX();
                int nz = z + side.getStepZ();
                boolean neighbourShown = shape.inside(nx, layerY, nz) && (!hollow || shape.isSurface(nx, layerY, nz));
                if (!neighbourShown) {
                    edges.addFace(x, layerY, z, side);
                }
            }
            return true;
        });
        return true;
    }

    /** Halfway to white: the colour of the next-point preview, and of structure pieces. */
    public static int lighter(int argb) {
        int r = ((argb >> 16) & 0xFF) + 0xFF >> 1;
        int g = ((argb >> 8) & 0xFF) + 0xFF >> 1;
        int b = (argb & 0xFF) + 0xFF >> 1;
        return (argb & 0xFF000000) | r << 16 | g << 8 | b;
    }

    /** The point the preview would add, unless it is already one. */
    public static @Nullable Measurement preview(Measurement active, @Nullable BlockPos target) {
        if (target == null || active.isEmpty() || active.points().contains(target)) {
            return null;
        }
        return active.withPoint(target);
    }
}
