package com.ArchitectsToolbox.gui;

import net.minecraft.network.chat.Component;

public interface RadialMenuAction {
    Component getName();

    void execute();

    boolean isEnabled();

    default int getColor() {
        return 0xFFFFFF;
    }

    /**
     * Called when the scroll wheel is used while hovering over this action.
     * @param amount positive for scroll up, negative for scroll down
     * @return true if this action handled the scroll, false otherwise
     */
    default boolean onScroll(double amount) {
        return false;
    }
}
