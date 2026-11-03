package com.ArchitectsToolbox.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Client calls whose names differ between Minecraft versions; each version has its own copy. 26.3. */
public final class ClientCompat {
    private ClientCompat() {
    }

    public static @Nullable Screen screen(Minecraft minecraft) {
        return minecraft.gui.screen();
    }

    public static void setScreen(Minecraft minecraft, @Nullable Screen screen) {
        minecraft.gui.setScreen(screen);
    }

    public static void chat(Minecraft minecraft, Component message) {
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(message);
        }
    }

    public static void actionBar(Minecraft minecraft, Component message) {
        if (minecraft.player != null) {
            minecraft.player.sendOverlayMessage(message);
        }
    }

    public static ClickEvent copyToClipboard(String text) {
        return new ClickEvent.CopyToClipboard(text);
    }
}
