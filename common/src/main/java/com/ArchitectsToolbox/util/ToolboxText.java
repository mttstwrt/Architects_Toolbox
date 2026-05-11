package com.ArchitectsToolbox.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

// ClickEvent/HoverEvent construction changed shape between MC versions
// (enum+ctor in 1.21.1; sealed-record subclasses in 1.21.5+). Routing
// chat-component construction through here keeps the per-branch difference
// localized to this file rather than every call site.
public final class ToolboxText {

    private ToolboxText() {}

    public static MutableComponent clickableCopy(String display, String copyValue, String hoverText, ChatFormatting color) {
        return clickableCopy(display, copyValue, Component.literal(hoverText), color);
    }

    public static MutableComponent clickableCopy(String display, String copyValue, Component hoverText, ChatFormatting color) {
        return Component.literal(display)
            .withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, copyValue))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText))
                .withColor(color));
    }
}
