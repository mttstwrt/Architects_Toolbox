package com.ArchitectsToolbox;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClipboardManager {
    private static final ClipboardManager INSTANCE = new ClipboardManager();

    private Map<BlockPos, BlockState> copiedBlocks = new HashMap<>();
    private BlockPos copiedMinPos = null;
    private BlockPos copiedMaxPos = null;
    private BlockPos copyAnchorPos = null;

    private boolean pastePreviewActive = false;
    private BlockPos previewAnchorPos = null;
    private int previewRotation = 0;

    private final List<LockedPlacement> lockedPlacements = new ArrayList<>();
    private boolean layerViewEnabled = false;
    private int currentViewLayer = 0;

    private ClipboardManager() {}

    public static ClipboardManager getInstance() {
        return INSTANCE;
    }

    public void copySelection() {
        SelectionManager selection = SelectionManager.getInstance();
        if (!selection.hasSelection()) return;

        Minecraft client = Minecraft.getInstance();
        Level level = client.level;
        if (level == null) return;

        copiedBlocks.clear();
        List<BlockPos> selectedBlocks = selection.getSelectedBlocks();

        BlockPos minPos = selection.getMinPos();
        BlockPos maxPos = selection.getMaxPos();

        if (minPos == null || maxPos == null) return;

        copiedMinPos = minPos;
        copiedMaxPos = maxPos;
        copyAnchorPos = minPos;

        for (int x = minPos.getX(); x <= maxPos.getX(); x++) {
            for (int y = minPos.getY(); y <= maxPos.getY(); y++) {
                for (int z = minPos.getZ(); z <= maxPos.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir()) {
                        BlockPos relative = new BlockPos(
                            x - minPos.getX(),
                            y - minPos.getY(),
                            z - minPos.getZ()
                        );
                        copiedBlocks.put(relative, state);
                    }
                }
            }
        }
    }

    public void copySelection(Level world) {
        SelectionManager selection = SelectionManager.getInstance();
        if (!selection.hasSelection()) return;

        if (world == null) return;

        copiedBlocks.clear();
        List<BlockPos> selectedBlocks = selection.getSelectedBlocks();

        BlockPos minPos = selection.getMinPos();
        BlockPos maxPos = selection.getMaxPos();

        if (minPos == null || maxPos == null) return;

        copiedMinPos = minPos;
        copiedMaxPos = maxPos;
        copyAnchorPos = minPos;

        for (int x = minPos.getX(); x <= maxPos.getX(); x++) {
            for (int y = minPos.getY(); y <= maxPos.getY(); y++) {
                for (int z = minPos.getZ(); z <= maxPos.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    if (!state.isAir()) {
                        BlockPos relative = new BlockPos(
                            x - minPos.getX(),
                            y - minPos.getY(),
                            z - minPos.getZ()
                        );
                        copiedBlocks.put(relative, state);
                    }
                }
            }
        }
    }

    public void copySelectionExact() {
        SelectionManager selection = SelectionManager.getInstance();
        if (!selection.hasSelection()) return;

        Minecraft client = Minecraft.getInstance();
        Level level = client.level;
        if (level == null) return;

        copiedBlocks.clear();
        List<BlockPos> selectedBlocks = selection.getSelectedBlocks();

        if (selectedBlocks.isEmpty()) return;

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (BlockPos pos : selectedBlocks) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        copiedMinPos = new BlockPos(minX, minY, minZ);
        copiedMaxPos = new BlockPos(maxX, maxY, maxZ);
        copyAnchorPos = copiedMinPos;

        for (BlockPos pos : selectedBlocks) {
            BlockState state = level.getBlockState(pos);
            BlockPos relative = new BlockPos(
                pos.getX() - minX,
                pos.getY() - minY,
                pos.getZ() - minZ
            );
            copiedBlocks.put(relative, state);
        }
    }

    public boolean hasCopiedBlocks() {
        return !copiedBlocks.isEmpty();
    }

    public Map<BlockPos, BlockState> getCopiedBlocks() {
        return Collections.unmodifiableMap(copiedBlocks);
    }

    public boolean hasClipboardData() {
        return hasCopiedBlocks();
    }

    public Map<BlockPos, BlockState> getClipboardData() {
        return getRotatedBlocks();
    }

    public Map<BlockPos, BlockState> getClipboardBlocks() {
        return getRotatedBlocks();
    }

    public void clearClipboard() {
        copiedBlocks.clear();
        pastePreviewActive = false;
        previewAnchorPos = null;
    }

    public BlockPos getCopiedMinPos() { return copiedMinPos; }
    public BlockPos getCopiedMaxPos() { return copiedMaxPos; }

    public int getCopiedSizeX() {
        if (copiedMinPos == null || copiedMaxPos == null) return 0;
        return copiedMaxPos.getX() - copiedMinPos.getX() + 1;
    }

    public int getCopiedSizeY() {
        if (copiedMinPos == null || copiedMaxPos == null) return 0;
        return copiedMaxPos.getY() - copiedMinPos.getY() + 1;
    }

    public int getCopiedSizeZ() {
        if (copiedMinPos == null || copiedMaxPos == null) return 0;
        return copiedMaxPos.getZ() - copiedMinPos.getZ() + 1;
    }

    public void startPastePreview() {
        if (!hasCopiedBlocks()) return;
        pastePreviewActive = true;
        previewRotation = 0;
        previewAnchorPos = null;
    }

    public void cancelPastePreview() {
        pastePreviewActive = false;
        previewAnchorPos = null;
        previewRotation = 0;
    }

    public boolean isPastePreviewActive() { return pastePreviewActive; }

    public void setPastePreviewActive(boolean active) {
        this.pastePreviewActive = active;
        if (!active) {
            previewAnchorPos = null;
        }
    }

    public Map<BlockPos, BlockState> getPreviewBlocks() { return getRotatedBlocks(); }
    public BlockPos getPreviewAnchorPos() { return previewAnchorPos; }
    public void setPreviewAnchorPos(BlockPos pos) { this.previewAnchorPos = pos; }
    public int getPreviewRotation() { return previewRotation; }

    public void rotateClockwise() { previewRotation = (previewRotation + 1) % 4; }
    public void rotateCounterClockwise() { previewRotation = (previewRotation + 3) % 4; }

    public Rotation getRotationEnum() {
        return switch (previewRotation) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    public Map<BlockPos, BlockState> getRotatedBlocks() {
        Map<BlockPos, BlockState> rotated = new HashMap<>();
        Rotation rotation = getRotationEnum();

        int sizeX = getCopiedSizeX();
        int sizeZ = getCopiedSizeZ();

        for (Map.Entry<BlockPos, BlockState> entry : copiedBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState state = entry.getValue();

            BlockPos rotatedPos = applyRotation(pos, rotation, sizeX, sizeZ);
            BlockState rotatedState = state.rotate(rotation);
            rotated.put(rotatedPos, rotatedState);
        }

        return rotated;
    }

    private BlockPos applyRotation(BlockPos pos, Rotation rotation, int sizeX, int sizeZ) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        return switch (rotation) {
            case CLOCKWISE_90 -> new BlockPos(sizeZ - 1 - z, y, x);
            case CLOCKWISE_180 -> new BlockPos(sizeX - 1 - x, y, sizeZ - 1 - z);
            case COUNTERCLOCKWISE_90 -> new BlockPos(z, y, sizeX - 1 - x);
            default -> pos;
        };
    }

    public Map<BlockPos, BlockState> getWorldPositionedBlocks(BlockPos anchor) {
        Map<BlockPos, BlockState> worldBlocks = new HashMap<>();
        Map<BlockPos, BlockState> rotated = getRotatedBlocks();

        for (Map.Entry<BlockPos, BlockState> entry : rotated.entrySet()) {
            BlockPos relPos = entry.getKey();
            BlockPos worldPos = anchor.offset(relPos.getX(), relPos.getY(), relPos.getZ());
            worldBlocks.put(worldPos, entry.getValue());
        }

        return worldBlocks;
    }

    public void lockCurrentPlacement() {
        if (previewAnchorPos == null || !hasCopiedBlocks()) return;

        Map<BlockPos, BlockState> blocks = getWorldPositionedBlocks(previewAnchorPos);
        lockedPlacements.add(new LockedPlacement(previewAnchorPos, blocks, previewRotation));

        cancelPastePreview();
    }

    public boolean hasLockedPlacements() { return !lockedPlacements.isEmpty(); }
    public List<LockedPlacement> getLockedPlacements() { return Collections.unmodifiableList(lockedPlacements); }

    public Map<BlockPos, BlockState> getAllLockedBlocks() {
        Map<BlockPos, BlockState> allBlocks = new HashMap<>();
        for (LockedPlacement placement : lockedPlacements) {
            allBlocks.putAll(placement.getBlocks());
        }
        return allBlocks;
    }

    public void setLockedPlacements(List<UndoRedoManager.LockedPlacementData> placementData) {
        lockedPlacements.clear();
        for (UndoRedoManager.LockedPlacementData data : placementData) {
            lockedPlacements.add(new LockedPlacement(data.anchorPos, new HashMap<>(data.blocks), 0));
        }
        if (lockedPlacements.isEmpty()) {
            layerViewEnabled = false;
            currentViewLayer = 0;
        }
    }

    public void clearLockedPlacements() {
        lockedPlacements.clear();
        layerViewEnabled = false;
        currentViewLayer = 0;
    }

    public void removeLastPlacement() {
        if (!lockedPlacements.isEmpty()) {
            lockedPlacements.remove(lockedPlacements.size() - 1);
            if (lockedPlacements.isEmpty()) {
                layerViewEnabled = false;
                currentViewLayer = 0;
            }
        }
    }

    public boolean isLayerViewEnabled() { return layerViewEnabled; }

    public void setLayerViewEnabled(boolean enabled) {
        this.layerViewEnabled = enabled;
        if (enabled && !lockedPlacements.isEmpty()) {
            int totalLayers = getLayerCount();
            currentViewLayer = totalLayers / 2;
        }
    }

    public void toggleLayerView() { setLayerViewEnabled(!layerViewEnabled); }
    public int getCurrentViewLayer() { return currentViewLayer; }

    public void setCurrentViewLayer(int layer) {
        this.currentViewLayer = Math.max(0, Math.min(layer, getLayerCount() - 1));
    }

    public void cycleLayerUp() {
        int maxLayer = getLayerCount() - 1;
        if (currentViewLayer < maxLayer) currentViewLayer++;
    }

    public void cycleLayerDown() {
        if (currentViewLayer > 0) currentViewLayer--;
    }

    public int getLayerCount() {
        if (lockedPlacements.isEmpty()) return 0;

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (LockedPlacement placement : lockedPlacements) {
            for (BlockPos pos : placement.getBlocks().keySet()) {
                minY = Math.min(minY, pos.getY());
                maxY = Math.max(maxY, pos.getY());
            }
        }

        if (minY == Integer.MAX_VALUE) return 0;
        return maxY - minY + 1;
    }

    public int getLayerMinY() {
        if (lockedPlacements.isEmpty()) return 0;

        int minY = Integer.MAX_VALUE;
        for (LockedPlacement placement : lockedPlacements) {
            for (BlockPos pos : placement.getBlocks().keySet()) {
                minY = Math.min(minY, pos.getY());
            }
        }

        return minY == Integer.MAX_VALUE ? 0 : minY;
    }

    public int getCurrentViewLayerY() { return getLayerMinY() + currentViewLayer; }

    public void mirrorX() {
        if (copiedBlocks.isEmpty()) return;
        Map<BlockPos, BlockState> mirrored = new HashMap<>();
        int sizeX = getCopiedSizeX();

        for (Map.Entry<BlockPos, BlockState> entry : copiedBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockPos mirroredPos = new BlockPos(sizeX - 1 - pos.getX(), pos.getY(), pos.getZ());
            mirrored.put(mirroredPos, entry.getValue());
        }

        copiedBlocks = mirrored;
    }

    public void mirrorZ() {
        if (copiedBlocks.isEmpty()) return;
        Map<BlockPos, BlockState> mirrored = new HashMap<>();
        int sizeZ = getCopiedSizeZ();

        for (Map.Entry<BlockPos, BlockState> entry : copiedBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockPos mirroredPos = new BlockPos(pos.getX(), pos.getY(), sizeZ - 1 - pos.getZ());
            mirrored.put(mirroredPos, entry.getValue());
        }

        copiedBlocks = mirrored;
    }

    public void stackPaste(BlockPos anchor, int countX, int countY, int countZ, int spacingX, int spacingY, int spacingZ) {
        if (!hasCopiedBlocks() || anchor == null) return;

        int sizeX = getCopiedSizeX();
        int sizeY = getCopiedSizeY();
        int sizeZ = getCopiedSizeZ();

        for (int ix = 0; ix < countX; ix++) {
            for (int iy = 0; iy < countY; iy++) {
                for (int iz = 0; iz < countZ; iz++) {
                    if (ix == 0 && iy == 0 && iz == 0) continue;

                    int offsetX = ix * (sizeX + spacingX);
                    int offsetY = iy * (sizeY + spacingY);
                    int offsetZ = iz * (sizeZ + spacingZ);

                    BlockPos stackAnchor = anchor.offset(offsetX, offsetY, offsetZ);
                    Map<BlockPos, BlockState> blocks = getWorldPositionedBlocks(stackAnchor);
                    lockedPlacements.add(new LockedPlacement(stackAnchor, blocks, previewRotation));
                }
            }
        }
    }

    public void clearAll() {
        copiedBlocks.clear();
        copiedMinPos = null;
        copiedMaxPos = null;
        copyAnchorPos = null;
        pastePreviewActive = false;
        previewAnchorPos = null;
        previewRotation = 0;
        lockedPlacements.clear();
        layerViewEnabled = false;
        currentViewLayer = 0;
    }

    public static class LockedPlacement {
        private final BlockPos anchorPos;
        private final Map<BlockPos, BlockState> blocks;
        private final int rotation;

        public LockedPlacement(BlockPos anchorPos, Map<BlockPos, BlockState> blocks, int rotation) {
            this.anchorPos = anchorPos;
            this.blocks = Collections.unmodifiableMap(new HashMap<>(blocks));
            this.rotation = rotation;
        }

        public BlockPos getAnchorPos() { return anchorPos; }
        public Map<BlockPos, BlockState> getBlocks() { return blocks; }
        public int getRotation() { return rotation; }

        public int getMinY() {
            int minY = Integer.MAX_VALUE;
            for (BlockPos pos : blocks.keySet()) minY = Math.min(minY, pos.getY());
            return minY == Integer.MAX_VALUE ? 0 : minY;
        }

        public int getMaxY() {
            int maxY = Integer.MIN_VALUE;
            for (BlockPos pos : blocks.keySet()) maxY = Math.max(maxY, pos.getY());
            return maxY == Integer.MIN_VALUE ? 0 : maxY;
        }

        public int getLayerCount() {
            if (blocks.isEmpty()) return 0;
            return getMaxY() - getMinY() + 1;
        }

        public Map<BlockPos, BlockState> getVisibleBlocks() {
            Map<BlockPos, BlockState> visible = new HashMap<>();
            for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
                BlockPos pos = entry.getKey();
                if (!blocks.containsKey(pos.above()) ||
                    !blocks.containsKey(pos.below()) ||
                    !blocks.containsKey(pos.north()) ||
                    !blocks.containsKey(pos.south()) ||
                    !blocks.containsKey(pos.east()) ||
                    !blocks.containsKey(pos.west())) {
                    visible.put(pos, entry.getValue());
                }
            }
            return visible;
        }

        public Map<BlockPos, BlockState> getBlocksAtY(int y) {
            Map<BlockPos, BlockState> layerBlocks = new HashMap<>();
            for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
                if (entry.getKey().getY() == y) {
                    layerBlocks.put(entry.getKey(), entry.getValue());
                }
            }
            return layerBlocks;
        }
    }
}
