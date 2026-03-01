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
import java.util.List;
import java.util.Map;

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
