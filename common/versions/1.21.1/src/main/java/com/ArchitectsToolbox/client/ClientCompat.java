package com.ArchitectsToolbox.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jspecify.annotations.Nullable;

/**
 * Calls whose names differ between Minecraft versions, for the client and for what it reads of the integrated server;
 * each version has its own copy. 1.21.1.
 */
public final class ClientCompat {
    private ClientCompat() {
    }

    public static @Nullable Screen screen(Minecraft minecraft) {
        return minecraft.screen;
    }

    public static void setScreen(Minecraft minecraft, @Nullable Screen screen) {
        minecraft.setScreen(screen);
    }

    public static void chat(Minecraft minecraft, Component message) {
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(message, false);
        }
    }

    public static void actionBar(Minecraft minecraft, Component message) {
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(message, true);
        }
    }

    /** Whether the server lets the player run {@code /setblock} and {@code /fill}. */
    public static boolean canUseGameMasterCommands(LocalPlayer player) {
        return player.hasPermissions(2);
    }

    public static ClickEvent copyToClipboard(String text) {
        return new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, text);
    }

    /** The lowest block Y of the level. */
    public static int minY(LevelHeightAccessor level) {
        return level.getMinBuildHeight();
    }

    /** The key's id, as "namespace:path". */
    public static String id(ResourceKey<?> key) {
        return key.location().toString();
    }

    /** The structure's id, as "namespace:path", or "?" if it is not registered. */
    public static String structureId(RegistryAccess registries, Structure structure) {
        var id = registries.registryOrThrow(Registries.STRUCTURE).getKey(structure);
        return id != null ? id.toString() : "?";
    }
}
