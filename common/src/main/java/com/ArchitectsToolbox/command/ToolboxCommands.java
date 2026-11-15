package com.ArchitectsToolbox.command;

import com.ArchitectsToolbox.client.ClientCompat;
import com.ArchitectsToolbox.client.MeasurementColours;
import com.ArchitectsToolbox.client.Operations;
import com.ArchitectsToolbox.geometry.BlockBox;
import com.ArchitectsToolbox.measure.Measurement;
import com.ArchitectsToolbox.measure.ShapeKind;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.function.IntConsumer;
import java.util.function.LongSupplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * The client-side {@code /atb} command (F50a to F50e), with 1.0.0's syntax, and {@code /atb config} for the settings
 * screen. Built for whatever command source the loader gives client commands; nothing here reads the source, replies
 * go to the player's chat.
 */
public final class ToolboxCommands {
    private ToolboxCommands() {
    }

    /**
     * @param counted    the counted total while the block count is on and finished, or a negative number
     * @param openConfig opens the settings screen
     * @param identify   runs {@code /atb identify} with the given radius
     */
    public static <S> LiteralArgumentBuilder<S> create(Operations operations, LongSupplier counted, Runnable openConfig, IntConsumer identify) {
        LiteralArgumentBuilder<S> atb = LiteralArgumentBuilder.literal("atb");
        atb.then(position("pos1", 0, operations));
        atb.then(position("pos2", 1, operations));
        atb.then(LiteralArgumentBuilder.<S>literal("clear").executes(context -> {
            operations.clear();
            reply(Component.translatable("architects_toolbox.command.cleared").withStyle(ChatFormatting.YELLOW));
            return 1;
        }).then(LiteralArgumentBuilder.<S>literal("all").executes(context -> {
            operations.clearAll();
            reply(Component.translatable("architects_toolbox.command.cleared_all").withStyle(ChatFormatting.YELLOW));
            return 1;
        })));
        atb.then(LiteralArgumentBuilder.<S>literal("color").then(
                RequiredArgumentBuilder.<S, String>argument("colour", StringArgumentType.greedyString())
                        .suggests((context, builder) -> {
                            for (int colour : operations.palette()) {
                                builder.suggest(MeasurementColours.hex(colour));
                            }
                            for (String name : MeasurementColours.names()) {
                                builder.suggest(name);
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            Integer colour = MeasurementColours.parse(StringArgumentType.getString(context, "colour"));
                            if (colour == null) {
                                reply(Component.translatable("architects_toolbox.command.invalid_colour").withStyle(ChatFormatting.RED));
                                return 0;
                            }
                            if (!operations.canStartMeasurement()) {
                                reply(Component.translatable("architects_toolbox.message.measurement_limit", operations.measurementLimit())
                                        .withStyle(ChatFormatting.RED));
                                return 0;
                            }
                            operations.pickColour(colour);
                            reply(Component.translatable("architects_toolbox.message.active_measurement", operations.store().state().active() + 1,
                                    MeasurementColours.name(colour)).withStyle(style -> style.withColor(colour & 0xFFFFFF)));
                            return 1;
                        })));
        LiteralArgumentBuilder<S> shape = LiteralArgumentBuilder.literal("shape");
        for (ShapeKind kind : ShapeKind.values()) {
            shape.then(LiteralArgumentBuilder.<S>literal(kind.id()).executes(context -> {
                operations.setShape(kind);
                reply(Component.translatable("architects_toolbox.command.shape", kind.displayName().copy().withStyle(ChatFormatting.WHITE))
                        .withStyle(ChatFormatting.GRAY));
                return 1;
            }));
        }
        atb.then(shape);
        atb.then(LiteralArgumentBuilder.<S>literal("info").executes(context -> info(operations.active(), counted.getAsLong())));
        atb.then(LiteralArgumentBuilder.<S>literal("identify")
                .executes(context -> {
                    identify.accept(0);
                    return 1;
                })
                .then(RequiredArgumentBuilder.<S, Integer>argument("radius", IntegerArgumentType.integer(0, 256)).executes(context -> {
                    identify.accept(IntegerArgumentType.getInteger(context, "radius"));
                    return 1;
                })));
        atb.then(LiteralArgumentBuilder.<S>literal("config").executes(context -> {
            openConfig.run();
            return 1;
        }));
        return atb;
    }

    private static <S> LiteralArgumentBuilder<S> position(String name, int index, Operations operations) {
        return LiteralArgumentBuilder.<S>literal(name).then(RequiredArgumentBuilder.<S, Integer>argument("x", IntegerArgumentType.integer())
                .then(RequiredArgumentBuilder.<S, Integer>argument("y", IntegerArgumentType.integer())
                        .then(RequiredArgumentBuilder.<S, Integer>argument("z", IntegerArgumentType.integer())
                                .executes(context -> setPosition(context, index, operations)))));
    }

    private static <S> int setPosition(CommandContext<S> context, int index, Operations operations) {
        BlockPos pos = new BlockPos(IntegerArgumentType.getInteger(context, "x"), IntegerArgumentType.getInteger(context, "y"),
                IntegerArgumentType.getInteger(context, "z"));
        if (index > operations.active().points().size()) {
            reply(Component.translatable("architects_toolbox.command.pos1_first").withStyle(ChatFormatting.RED));
            return 0;
        }
        operations.setPoint(index, pos);
        reply(Component.translatable("architects_toolbox.command.position_set", index + 1, coordinates(pos)));
        return 1;
    }

    private static int info(Measurement measurement, long counted) {
        if (measurement.isEmpty()) {
            reply(Component.translatable("architects_toolbox.command.no_measurement").withStyle(ChatFormatting.RED));
            return 0;
        }
        BlockBox box = measurement.box();
        reply(Component.translatable("architects_toolbox.command.info_header").withStyle(ChatFormatting.GOLD));
        reply(Component.translatable("architects_toolbox.command.info_shape", measurement.shape().displayName().copy().withStyle(ChatFormatting.WHITE),
                measurement.points().size()).withStyle(ChatFormatting.GRAY));
        reply(Component.translatable("architects_toolbox.command.info_bounds", coordinates(box.min()), coordinates(box.max())).withStyle(ChatFormatting.GRAY));
        String both = text(box.min()) + " " + text(box.max());
        reply(Component.translatable("architects_toolbox.command.info_both", copyable(both)).withStyle(ChatFormatting.GRAY));
        reply(Component.translatable("architects_toolbox.command.info_size", box.sizeX(), box.sizeY(), box.sizeZ(), box.volume())
                .withStyle(ChatFormatting.GRAY));
        if (counted >= 0) {
            reply(Component.translatable("architects_toolbox.command.info_counted", counted).withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }

    /** "x y z", which a click copies. */
    static MutableComponent coordinates(BlockPos pos) {
        return copyable(text(pos));
    }

    /** Text that a click copies. */
    static MutableComponent copyable(String text) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(ClientCompat.copyToClipboard(text)));
    }

    private static String text(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    static void reply(Component message) {
        ClientCompat.chat(Minecraft.getInstance(), message);
    }
}
