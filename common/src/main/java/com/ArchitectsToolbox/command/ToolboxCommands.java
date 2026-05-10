package com.ArchitectsToolbox.command;

import com.ArchitectsToolbox.BlockCounter;
import com.ArchitectsToolbox.SelectionManager;
import com.ArchitectsToolbox.UndoRedoManager;
import com.ArchitectsToolbox.render.MeasurementRenderer;
import com.ArchitectsToolbox.shapes.ShapeMode;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;

public class ToolboxCommands {

    // Typed helpers using ClientCommandSourceStack to avoid dependency on MC's Commands class
    // (which is tied to server CommandSourceStack, not Architectury's ClientCommandSourceStack).
    private static LiteralArgumentBuilder<ClientCommandRegistrationEvent.ClientCommandSourceStack> lit(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private static <T> RequiredArgumentBuilder<ClientCommandRegistrationEvent.ClientCommandSourceStack, T> arg(String name, com.mojang.brigadier.arguments.ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    public static void register(CommandDispatcher<ClientCommandRegistrationEvent.ClientCommandSourceStack> dispatcher, CommandBuildContext context) {
        // Store in a typed variable so Java can infer S throughout the builder chain.
        LiteralArgumentBuilder<ClientCommandRegistrationEvent.ClientCommandSourceStack> atb = lit("atb");

        atb.then(lit("pos1")
                .then(arg("x", IntegerArgumentType.integer())
                .then(arg("y", IntegerArgumentType.integer())
                .then(arg("z", IntegerArgumentType.integer())
                .executes(ctx -> setPos1(
                    IntegerArgumentType.getInteger(ctx, "x"),
                    IntegerArgumentType.getInteger(ctx, "y"),
                    IntegerArgumentType.getInteger(ctx, "z")))))));

        atb.then(lit("pos2")
                .then(arg("x", IntegerArgumentType.integer())
                .then(arg("y", IntegerArgumentType.integer())
                .then(arg("z", IntegerArgumentType.integer())
                .executes(ctx -> setPos2(
                    IntegerArgumentType.getInteger(ctx, "x"),
                    IntegerArgumentType.getInteger(ctx, "y"),
                    IntegerArgumentType.getInteger(ctx, "z")))))));

        atb.then(lit("clear")
                .executes(ctx -> clearSelection()));

        atb.then(lit("shape")
                .then(lit("rectangle").executes(ctx -> setShape(ShapeMode.RECTANGLE)))
                .then(lit("cylinder").executes(ctx -> setShape(ShapeMode.CYLINDER)))
                .then(lit("ellipsoid").executes(ctx -> setShape(ShapeMode.ELLIPSOID)))
                .then(lit("spline").executes(ctx -> setShape(ShapeMode.SPLINE)))
                .then(lit("line").executes(ctx -> setShape(ShapeMode.LINE))));

        atb.then(lit("info")
                .executes(ctx -> showInfo()));

        atb.then(lit("identify")
                .executes(ctx -> identify(0))
                .then(arg("radius", IntegerArgumentType.integer(0, 256))
                    .executes(ctx -> identify(IntegerArgumentType.getInteger(ctx, "radius")))));

        dispatcher.register(atb);
    }

    // --- Command handlers ---

    private static int setPos1(int x, int y, int z) {
        SelectionManager manager = SelectionManager.getInstance();
        UndoRedoManager.getInstance().saveState();

        List<BlockPos> blocks = new ArrayList<>(manager.getSelectedBlocks());
        BlockPos newPos = new BlockPos(x, y, z);
        if (blocks.isEmpty()) {
            blocks.add(newPos);
        } else {
            blocks.set(0, newPos);
        }
        manager.setSelectedBlocks(blocks);
        MeasurementRenderer.getInstance().invalidateHollowShapeCache();

        sendMsg(Component.literal("Position 1 set to ")
            .append(clickableCoords(x, y, z)));
        return 1;
    }

    private static int setPos2(int x, int y, int z) {
        SelectionManager manager = SelectionManager.getInstance();
        List<BlockPos> blocks = new ArrayList<>(manager.getSelectedBlocks());

        if (blocks.isEmpty()) {
            sendMsg(Component.literal("Set position 1 first with /atb pos1")
                .withStyle(ChatFormatting.RED));
            return 0;
        }

        UndoRedoManager.getInstance().saveState();
        BlockPos newPos = new BlockPos(x, y, z);
        if (blocks.size() == 1) {
            blocks.add(newPos);
        } else {
            blocks.set(1, newPos);
        }
        manager.setSelectedBlocks(blocks);
        MeasurementRenderer.getInstance().invalidateHollowShapeCache();

        sendMsg(Component.literal("Position 2 set to ")
            .append(clickableCoords(x, y, z)));
        return 1;
    }

    private static int clearSelection() {
        SelectionManager.getInstance().clearSelection();
        MeasurementRenderer.getInstance().invalidateHollowShapeCache();
        sendMsg(Component.literal("Selection cleared").withStyle(ChatFormatting.YELLOW));
        return 1;
    }

    private static int setShape(ShapeMode mode) {
        SelectionManager.getInstance().setShapeMode(mode);
        sendMsg(Component.empty()
            .append(Component.literal("Shape: ").withStyle(ChatFormatting.GRAY))
            .append(mode.getDisplayName().copy().withStyle(ChatFormatting.WHITE)));
        return 1;
    }

    private static int showInfo() {
        SelectionManager manager = SelectionManager.getInstance();

        if (!manager.hasSelection()) {
            sendMsg(Component.literal("No selection active.").withStyle(ChatFormatting.RED));
            return 0;
        }

        BlockPos min = manager.getMinPos();
        BlockPos max = manager.getMaxPos();

        sendMsg(Component.literal("--- Architect's Toolbox ---").withStyle(ChatFormatting.GOLD));

        // Shape mode
        sendMsg(Component.empty()
            .append(Component.literal("Shape: ").withStyle(ChatFormatting.GRAY))
            .append(manager.getShapeMode().getDisplayName().copy().withStyle(ChatFormatting.WHITE))
            .append(Component.literal("  |  Points: " + manager.getSelectedBlocks().size())
                .withStyle(ChatFormatting.DARK_GRAY)));

        // Bounds with clickable coords
        sendMsg(Component.empty()
            .append(Component.literal("Min: ").withStyle(ChatFormatting.GRAY))
            .append(clickableCoords(min.getX(), min.getY(), min.getZ()))
            .append(Component.literal("  Max: ").withStyle(ChatFormatting.GRAY))
            .append(clickableCoords(max.getX(), max.getY(), max.getZ())));

        // Both corners combined (single click copies both)
        sendMsg(Component.empty()
            .append(Component.literal("Both: ").withStyle(ChatFormatting.GRAY))
            .append(clickableBothCorners(min, max)));

        // Dimensions + volume
        int dx = max.getX() - min.getX() + 1;
        int dy = max.getY() - min.getY() + 1;
        int dz = max.getZ() - min.getZ() + 1;
        long volume = (long) dx * dy * dz;
        sendMsg(Component.literal("Size: " + dx + " \u00d7 " + dy + " \u00d7 " + dz
            + " (X\u00d7Y\u00d7Z)  |  Volume: " + volume + " blocks")
            .withStyle(ChatFormatting.GRAY));

        // Block count breakdown if counting is enabled
        if (manager.isBlockCountingEnabled()) {
            BlockCounter counter = BlockCounter.getInstance();
            int counted = counter.getTotalBlockCount();
            Map<?, Integer> typeCounts = counter.getBlockCounts();
            sendMsg(Component.literal("Counted: " + counted + " blocks (" + typeCounts.size() + " types)")
                .withStyle(ChatFormatting.GRAY));
        }

        return 1;
    }

    private static int identify(int radius) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return 0;

        SelectionManager sel = SelectionManager.getInstance();
        boolean hasSel = sel.hasSelection();
        BlockPos selMin = hasSel ? sel.getMinPos() : null;
        BlockPos selMax = hasSel ? sel.getMaxPos() : null;
        BlockPos scanMin, scanMax;

        if (hasSel) {
            scanMin = selMin.offset(-radius, -radius, -radius);
            scanMax = selMax.offset(radius, radius, radius);
        } else {
            BlockPos pp = mc.player.blockPosition();
            scanMin = pp.offset(-radius, -radius, -radius);
            scanMax = pp.offset(radius, radius, radius);
        }

        // Clamp Y to world bounds
        int minY = mc.level.getMinY();
        int maxY = mc.level.getMaxY();
        scanMin = new BlockPos(scanMin.getX(), Math.max(minY, scanMin.getY()), scanMin.getZ());
        scanMax = new BlockPos(scanMax.getX(), Math.min(maxY, scanMax.getY()), scanMax.getZ());

        sendMsg(Component.literal("--- Identify ---").withStyle(ChatFormatting.GOLD));
        if (!hasSel) {
            sendMsg(Component.literal("No selection \u2014 using player position").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (radius > 0) {
            sendMsg(Component.literal("Search radius: " + radius).withStyle(ChatFormatting.DARK_GRAY));
        }

        identifyBiomes(mc, scanMin, scanMax, selMin, selMax);
        identifyStructures(mc, scanMin, scanMax, selMin, selMax);

        return 1;
    }

    private static void identifyBiomes(Minecraft mc, BlockPos scanMin, BlockPos scanMax,
            BlockPos selMin, BlockPos selMax) {
        // Sample at 4-block intervals (matches biome storage resolution)
        Map<ResourceLocation, boolean[]> found = new LinkedHashMap<>();
        Map<ResourceLocation, BlockPos> nearestOutside = new LinkedHashMap<>();

        for (int x = scanMin.getX(); x <= scanMax.getX(); x += 4) {
            for (int z = scanMin.getZ(); z <= scanMax.getZ(); z += 4) {
                for (int y = scanMin.getY(); y <= scanMax.getY(); y += 4) {
                    BlockPos pos = new BlockPos(x, y, z);
                    mc.level.getBiome(pos).unwrapKey().ifPresent(key -> {
                        ResourceLocation id = key.location();
                        boolean[] flags = found.computeIfAbsent(id, k -> new boolean[2]);
                        boolean inside = selMin != null && isInside(pos, selMin, selMax);
                        if (inside) {
                            flags[0] = true;
                        } else {
                            flags[1] = true;
                            if (selMin != null) {
                                BlockPos prev = nearestOutside.get(id);
                                if (prev == null || distToBox(pos, selMin, selMax) < distToBox(prev, selMin, selMax)) {
                                    nearestOutside.put(id, pos);
                                }
                            }
                        }
                    });
                }
            }
        }

        sendMsg(Component.literal("Biomes:").withStyle(ChatFormatting.YELLOW));
        if (found.isEmpty()) {
            sendMsg(Component.literal("  (none)").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        for (var entry : found.entrySet()) {
            ResourceLocation id = entry.getKey();
            boolean[] flags = entry.getValue();
            MutableComponent line = Component.literal("  ").append(clickableId(id));
            if (selMin != null && !flags[0] && flags[1]) {
                BlockPos nearest = nearestOutside.get(id);
                if (nearest != null) {
                    int dist = (int) Math.ceil(distToBox(nearest, selMin, selMax));
                    line.append(Component.literal(" (~" + dist + " blocks away)").withStyle(ChatFormatting.DARK_GRAY));
                }
            }
            sendMsg(line);
        }
    }

    private static void identifyStructures(Minecraft mc, BlockPos scanMin, BlockPos scanMax,
            BlockPos selMin, BlockPos selMax) {
        var server = mc.getSingleplayerServer();
        if (server == null) {
            sendMsg(Component.literal("Structures:").withStyle(ChatFormatting.YELLOW));
            sendMsg(Component.literal("  (requires singleplayer)").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        var serverLevel = server.getLevel(mc.level.dimension());
        if (serverLevel == null) return;

        var registry = serverLevel.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        var structureManager = serverLevel.structureManager();

        int minCX = scanMin.getX() >> 4, maxCX = scanMax.getX() >> 4;
        int minCZ = scanMin.getZ() >> 4, maxCZ = scanMax.getZ() >> 4;

        Map<ResourceLocation, BlockPos> positions = new LinkedHashMap<>();
        Map<ResourceLocation, Boolean> insideMap = new LinkedHashMap<>();

        // Use StructureManager.getStructureAt which resolves both starts AND references,
        // so we find structures even if their origin chunk is outside the scan area.
        for (var entry : registry.entrySet()) {
            ResourceLocation id = entry.getKey().location();
            boolean found = false;
            for (int cx = minCX; cx <= maxCX && !found; cx++) {
                for (int cz = minCZ; cz <= maxCZ && !found; cz++) {
                    BlockPos sample = new BlockPos(cx * 16 + 8, (scanMin.getY() + scanMax.getY()) / 2, cz * 16 + 8);
                    var start = structureManager.getStructureAt(sample, entry.getValue());
                    if (start != null && !start.getPieces().isEmpty()) {
                        var bb = start.getBoundingBox();
                        positions.put(id, bb.getCenter());
                        boolean inside = selMin == null
                            || (bb.minX() <= selMax.getX() && bb.maxX() >= selMin.getX()
                                && bb.minY() <= selMax.getY() && bb.maxY() >= selMin.getY()
                                && bb.minZ() <= selMax.getZ() && bb.maxZ() >= selMin.getZ());
                        insideMap.put(id, inside);
                        found = true;
                    }
                }
            }
        }

        sendMsg(Component.literal("Structures:").withStyle(ChatFormatting.YELLOW));
        if (positions.isEmpty()) {
            sendMsg(Component.literal("  (none in loaded chunks)").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        for (var e : positions.entrySet()) {
            ResourceLocation id = e.getKey();
            BlockPos center = e.getValue();
            boolean inside = insideMap.getOrDefault(id, false);
            MutableComponent line = Component.literal("  ").append(clickableId(id))
                .append(Component.literal(" at ").withStyle(ChatFormatting.GRAY))
                .append(clickableCoords(center.getX(), center.getY(), center.getZ()));
            if (selMin != null && !inside) {
                int dist = (int) Math.ceil(distToBox(center, selMin, selMax));
                line.append(Component.literal(" (~" + dist + " blocks away)").withStyle(ChatFormatting.DARK_GRAY));
            }
            sendMsg(line);
        }
    }

    private static boolean isInside(BlockPos pos, BlockPos min, BlockPos max) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
            && pos.getY() >= min.getY() && pos.getY() <= max.getY()
            && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    private static double distToBox(BlockPos pos, BlockPos min, BlockPos max) {
        int dx = Math.max(0, Math.max(min.getX() - pos.getX(), pos.getX() - max.getX()));
        int dy = Math.max(0, Math.max(min.getY() - pos.getY(), pos.getY() - max.getY()));
        int dz = Math.max(0, Math.max(min.getZ() - pos.getZ(), pos.getZ() - max.getZ()));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Clickable resource ID that copies the full ID string on click. */
    private static MutableComponent clickableId(ResourceLocation id) {
        String s = id.toString();
        return Component.literal(s)
            .withStyle(style -> style
                .withClickEvent(new ClickEvent.CopyToClipboard(s))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to copy: " + s)))
                .withColor(ChatFormatting.GREEN));
    }

    // --- Component helpers ---

    /** Clickable [x, y, z] that copies "x y z" to clipboard on click. */
    private static MutableComponent clickableCoords(int x, int y, int z) {
        String copyStr = x + " " + y + " " + z;
        return Component.literal("[" + x + ", " + y + ", " + z + "]")
            .withStyle(style -> style
                .withClickEvent(new ClickEvent.CopyToClipboard(copyStr))
                .withHoverEvent(new HoverEvent.ShowText(
                    Component.literal("Click to copy: " + copyStr)))
                .withColor(ChatFormatting.GREEN));
    }

    /** Clickable range that copies "x1 y1 z1 x2 y2 z2" to clipboard on click. */
    private static MutableComponent clickableBothCorners(BlockPos min, BlockPos max) {
        String copyStr = min.getX() + " " + min.getY() + " " + min.getZ()
            + " " + max.getX() + " " + max.getY() + " " + max.getZ();
        String display = "[" + min.getX() + ", " + min.getY() + ", " + min.getZ()
            + "  \u2192  " + max.getX() + ", " + max.getY() + ", " + max.getZ() + "]";
        return Component.literal(display)
            .withStyle(style -> style
                .withClickEvent(new ClickEvent.CopyToClipboard(copyStr))
                .withHoverEvent(new HoverEvent.ShowText(
                    Component.literal("Click to copy both corners:\n" + copyStr)))
                .withColor(ChatFormatting.AQUA));
    }

    private static void sendMsg(Component component) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(component, false);
        }
    }
}
