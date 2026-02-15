package com.ArchitectsToolbox.render;

import com.ArchitectsToolbox.ClipboardManager;
import com.ArchitectsToolbox.SelectionManager;
import com.ArchitectsToolbox.shapes.ShapeMode;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class MeasurementRenderer {
    private static final MeasurementRenderer INSTANCE = new MeasurementRenderer();
    private final Map<ShapeMode, ShapeRenderer> renderers = new EnumMap<>(ShapeMode.class);
    private final SubdivisionRenderer subdivisionRenderer = new SubdivisionRenderer();
    private final ChunkBoundaryRenderer chunkBoundaryRenderer = new ChunkBoundaryRenderer();
    private final HollowShapeRenderer hollowShapeRenderer = new HollowShapeRenderer();
    private final GhostBlockRenderer ghostBlockRenderer = new GhostBlockRenderer();

    private MeasurementRenderer() {
        renderers.put(ShapeMode.RECTANGLE, new RectangleRenderer());
        renderers.put(ShapeMode.CYLINDER, new CylinderRenderer());
        renderers.put(ShapeMode.ELLIPSOID, new EllipsoidRenderer());
        renderers.put(ShapeMode.SPLINE, new SplineRenderer());
        renderers.put(ShapeMode.LINE, new LineRenderer());
    }

    public static MeasurementRenderer getInstance() {
        return INSTANCE;
    }

    public void render(Camera camera, Matrix4f viewMatrix) {
        SelectionManager selection = SelectionManager.getInstance();
        List<BlockPos> selectedBlocks = selection.getSelectedBlocks();

        ShapeMode mode = selection.getShapeMode();
        int subdivisions = selection.getSubdivisionCount();
        boolean showLabels = true;

        ShapeRenderer.RenderConfig config = new ShapeRenderer.RenderConfig(
                1.0f, 0.3f, 0.3f, 1.0f, subdivisions, showLabels
        );

        // Render selection shapes if there's a selection
        if (!selectedBlocks.isEmpty()) {
            boolean hollowEligible = selectedBlocks.size() >= 2 ||
                    (mode == ShapeMode.CYLINDER && selectedBlocks.size() >= 1 &&
                     selection.getCylinderRadiusOffsetBlocks() > 0);
            if (selection.isHollowMode() && hollowEligible) {
                hollowShapeRenderer.render(camera, viewMatrix, selectedBlocks, config, mode);

                // Also render shape-specific labels
                ShapeRenderer renderer = renderers.get(mode);
                if (renderer != null) {
                    renderer.renderLabels(camera, viewMatrix, selectedBlocks, config);
                }
            } else {
                ShapeRenderer renderer = renderers.get(mode);
                if (renderer != null) {
                    renderer.render(camera, viewMatrix, selectedBlocks, config);
                    renderer.renderLabels(camera, viewMatrix, selectedBlocks, config);
                }

                if (subdivisions > 0 && selectedBlocks.size() >= 2) {
                    subdivisionRenderer.render(camera, viewMatrix, selectedBlocks, config);
                }
            }
        }

        // Ghost blocks render independently of selection
        ghostBlockRenderer.render(camera, viewMatrix, selectedBlocks, config);

        // Chunk boundaries render independently of selection
        if (selection.isChunkBoundariesEnabled()) {
            chunkBoundaryRenderer.render(camera, viewMatrix, selectedBlocks, config);
        }
    }

    public void invalidateHollowShapeCache() {
        hollowShapeRenderer.invalidateCache();
    }

    public void invalidateGhostBlockCaches() {
        ghostBlockRenderer.invalidateCache();
    }

    public AABB getSelectionBounds(List<BlockPos> blocks) {
        if (blocks.isEmpty()) return null;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : blocks) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }
}
