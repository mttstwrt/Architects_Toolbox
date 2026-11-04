package com.ArchitectsToolbox.render;

import com.ArchitectsToolbox.clipboard.Placement;
import com.ArchitectsToolbox.geometry.BlockBox;
import com.ArchitectsToolbox.geometry.Shape;
import com.ArchitectsToolbox.geometry.ShapeBlocks;
import com.ArchitectsToolbox.measure.Measurement;
import java.util.ArrayList;
import java.util.List;
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

    /**
     * What to draw.
     *
     * @param measurements the measurements with points
     * @param hollow       hollow view on
     * @param layerY       the single layer shown, or null for all
     * @param preview      the active measurement with the targeted block added (F05), or null
     * @param placements   the placements locked as guides; the layer view slices them too
     * @param paste        the paste preview, or null
     * @param ghostModels  whether ghosts are drawn as block models as well as outlines
     * @param ghostOpacity the ghost models' opacity, 0 to 1
     */
    public record Input(List<Measurement> measurements, boolean hollow, @Nullable Integer layerY, @Nullable Measurement preview,
                        List<Placement> placements, @Nullable Placement paste, boolean ghostModels, float ghostOpacity) {
        public Input {
            measurements = List.copyOf(measurements);
            placements = List.copyOf(placements);
        }
    }

    /** The scene, and a reason the requested view could not be shown, if there is one. */
    public record Result(WorldScene scene, @Nullable Component warning) {}

    private MeasurementScene() {
    }

    /** Builds the scene; returns null if {@code cancelled} turned true on the way. */
    public static @Nullable Result build(Input input, BooleanSupplier cancelled) {
        List<LineMesh> meshes = new ArrayList<>();
        List<WorldLabel> labels = new ArrayList<>();
        Component warning = null;
        for (Measurement measurement : input.measurements()) {
            Shape shape = measurement.geometry();
            BlockBox box = shape.bounds();
            LineMesh.Builder lines = LineMesh.builder(box.minX(), box.minY(), box.minZ());
            List<WorldLabel> ownLabels = input.preview() == null ? labels : null;
            boolean blockView = input.hollow() || input.layerY() != null;
            Component problem = blockView ? blockViewProblem(shape) : null;
            if (blockView && problem == null) {
                BlockEdges edges = new BlockEdges(box.minX(), box.minY(), box.minZ());
                Boolean done = input.layerY() != null
                        ? layer(shape, input.hollow(), input.layerY(), edges, cancelled)
                        : hollow(shape, edges, cancelled);
                if (done == null) {
                    return null;
                }
                if (done) {
                    edges.addTo(lines, measurement.colour());
                    Outlines.add(measurement, shape, new Outlines.Sink(null, ownLabels, measurement.colour()), false);
                } else {
                    problem = Component.translatable("architects_toolbox.message.hollow_too_many", MeasurementScene.HOLLOW_LIMIT);
                }
            }
            if (!blockView || problem != null) {
                Outlines.add(measurement, shape, new Outlines.Sink(lines, ownLabels, measurement.colour()), !input.hollow());
            }
            if (problem != null) {
                warning = problem;
            }
            meshes.add(lines.build());
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
            tooManyGhosts |= addPlacement(placement, input.layerY(), LOCKED_COLOUR, input, meshes, ghosts);
        }
        if (input.paste() != null) {
            tooManyGhosts |= addPlacement(input.paste(), null, PASTE_COLOUR, input, meshes, ghosts);
        }
        if (tooManyGhosts && warning == null) {
            warning = Component.translatable("architects_toolbox.message.ghosts_too_many", ClipboardMeshes.GHOST_LIMIT);
        }
        return new Result(new WorldScene(meshes, ghosts, labels), warning);
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

    /** Halfway to white: the colour of the next-point preview. */
    private static int lighter(int argb) {
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
