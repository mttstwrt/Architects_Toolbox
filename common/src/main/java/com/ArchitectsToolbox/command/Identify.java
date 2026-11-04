package com.ArchitectsToolbox.command;

import com.ArchitectsToolbox.client.ClientCompat;
import com.ArchitectsToolbox.geometry.BlockBox;
import com.ArchitectsToolbox.measure.Measurement;
import com.ArchitectsToolbox.overlay.StructureScanner;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

/**
 * {@code /atb identify [radius]} (F50e): the biomes and structures in the active measurement's box widened by the
 * radius, or round the player without one. Biomes are sampled from the client's world every 4 blocks, as they are
 * stored, leaving out chunks it has not loaded. Structures come from the structure scanner, so only in singleplayer,
 * and are listed a moment later, when the server has looked. Client thread.
 */
public final class Identify {
    /** More biome samples than this and the biomes are not listed. */
    private static final long SAMPLE_LIMIT = 4_000_000;
    private static final int STEP = 4;

    private Identify() {
    }

    public static void run(Minecraft minecraft, Measurement active, int radius, StructureScanner scanner) {
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            return;
        }
        BlockBox selection = active.isEmpty() ? null : active.box();
        BlockBox around = selection != null ? selection : new BlockBox(minecraft.player.getBlockX(), minecraft.player.getBlockY(),
                minecraft.player.getBlockZ(), minecraft.player.getBlockX(), minecraft.player.getBlockY(), minecraft.player.getBlockZ());
        int minY = ClientCompat.minY(level);
        int maxY = minY + level.getHeight() - 1;
        BlockBox area = new BlockBox(around.minX() - radius, Math.clamp(around.minY() - radius, minY, maxY), around.minZ() - radius,
                around.maxX() + radius, Math.clamp(around.maxY() + radius, minY, maxY), around.maxZ() + radius);

        ToolboxCommands.reply(Component.translatable("architects_toolbox.command.identify_header").withStyle(ChatFormatting.GOLD));
        if (selection == null) {
            ToolboxCommands.reply(Component.translatable("architects_toolbox.command.identify_no_measurement").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (radius > 0) {
            ToolboxCommands.reply(Component.translatable("architects_toolbox.command.identify_radius", radius).withStyle(ChatFormatting.DARK_GRAY));
        }
        biomes(level, area, selection);
        structures(minecraft, level, area, selection, scanner);
    }

    private static void biomes(ClientLevel level, BlockBox area, @Nullable BlockBox selection) {
        ToolboxCommands.reply(Component.translatable("architects_toolbox.command.identify_biomes").withStyle(ChatFormatting.YELLOW));
        long samples = (long) Math.ceilDiv(area.sizeX(), STEP) * Math.ceilDiv(area.sizeY(), STEP) * Math.ceilDiv(area.sizeZ(), STEP);
        if (samples > SAMPLE_LIMIT) {
            ToolboxCommands.reply(Component.translatable("architects_toolbox.command.identify_too_large", SAMPLE_LIMIT).withStyle(ChatFormatting.RED));
            return;
        }
        // For each biome: whether it was seen inside the measurement, and its nearest sample outside.
        Map<String, Boolean> inside = new LinkedHashMap<>();
        Map<String, BlockPos> nearestOutside = new LinkedHashMap<>();
        BlockPos.MutableBlockPos sample = new BlockPos.MutableBlockPos();
        for (int x = area.minX(); x <= area.maxX(); x += STEP) {
            for (int z = area.minZ(); z <= area.maxZ(); z += STEP) {
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                for (int y = area.minY(); y <= area.maxY(); y += STEP) {
                    var key = level.getBiome(sample.set(x, y, z)).unwrapKey();
                    if (key.isEmpty()) {
                        continue;
                    }
                    String id = ClientCompat.id(key.get());
                    boolean in = selection != null && selection.contains(x, y, z);
                    inside.merge(id, in, Boolean::logicalOr);
                    if (selection != null && !in) {
                        BlockPos here = sample.immutable();
                        nearestOutside.merge(id, here, (a, b) -> distance(selection, a) <= distance(selection, b) ? a : b);
                    }
                }
            }
        }
        if (inside.isEmpty()) {
            ToolboxCommands.reply(Component.translatable("architects_toolbox.command.identify_none").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        inside.forEach((id, in) -> {
            MutableComponent line = Component.literal("  ").append(ToolboxCommands.copyable(id));
            if (selection != null && !in) {
                line.append(away(distance(selection, nearestOutside.get(id))));
            }
            ToolboxCommands.reply(line);
        });
    }

    private static void structures(Minecraft minecraft, ClientLevel level, BlockBox area, @Nullable BlockBox selection, StructureScanner scanner) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            ToolboxCommands.reply(Component.translatable("architects_toolbox.command.identify_structures").withStyle(ChatFormatting.YELLOW));
            ToolboxCommands.reply(Component.translatable("architects_toolbox.command.identify_singleplayer").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        // The area's chunks the client has loaded; the rest are left out, so a huge area costs no more than the view.
        int reach = minecraft.options.getEffectiveRenderDistance() + 1;
        int playerX = minecraft.player.getBlockX() >> 4;
        int playerZ = minecraft.player.getBlockZ() >> 4;
        IntArrayList chunks = new IntArrayList();
        for (int x = Math.max(area.minX() >> 4, playerX - reach); x <= Math.min(area.maxX() >> 4, playerX + reach); x++) {
            for (int z = Math.max(area.minZ() >> 4, playerZ - reach); z <= Math.min(area.maxZ() >> 4, playerZ + reach); z++) {
                if (level.hasChunk(x, z)) {
                    chunks.add(x);
                    chunks.add(z);
                }
            }
        }
        scanner.scan(server, level.dimension(), chunks.toIntArray(), minecraft, found -> {
            ToolboxCommands.reply(Component.translatable("architects_toolbox.command.identify_structures").withStyle(ChatFormatting.YELLOW));
            List<StructureScanner.Found> inArea = new ArrayList<>();
            for (StructureScanner.Found structure : found) {
                if (structure.bounds().intersects(area)) {
                    inArea.add(structure);
                }
            }
            if (inArea.isEmpty()) {
                ToolboxCommands.reply(Component.translatable("architects_toolbox.command.identify_none_loaded").withStyle(ChatFormatting.DARK_GRAY));
                return;
            }
            inArea.sort(Comparator.comparing(StructureScanner.Found::id));
            for (StructureScanner.Found structure : inArea) {
                BlockBox bounds = structure.bounds();
                BlockPos centre = new BlockPos((bounds.minX() + bounds.maxX()) / 2, (bounds.minY() + bounds.maxY()) / 2, (bounds.minZ() + bounds.maxZ()) / 2);
                MutableComponent line = Component.literal("  ").append(Component.translatable("architects_toolbox.command.identify_at",
                        ToolboxCommands.copyable(structure.id()), ToolboxCommands.coordinates(centre)).withStyle(ChatFormatting.GRAY));
                if (selection != null && !bounds.intersects(selection)) {
                    line.append(away(distance(selection, centre)));
                }
                ToolboxCommands.reply(line);
            }
        });
    }

    private static Component away(double distance) {
        return Component.translatable("architects_toolbox.command.identify_away", (int) Math.ceil(distance)).withStyle(ChatFormatting.DARK_GRAY);
    }

    /** From the block to the nearest block of the box. */
    private static double distance(BlockBox box, BlockPos pos) {
        int dx = Math.max(0, Math.max(box.minX() - pos.getX(), pos.getX() - box.maxX()));
        int dy = Math.max(0, Math.max(box.minY() - pos.getY(), pos.getY() - box.maxY()));
        int dz = Math.max(0, Math.max(box.minZ() - pos.getZ(), pos.getZ() - box.maxZ()));
        return Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz);
    }
}
