package com.ArchitectsToolbox.measure;

import java.util.Locale;
import net.minecraft.network.chat.Component;

/** The measurement shapes. The lower-case name is the {@code /atb shape} argument, as in 1.0.0. */
public enum ShapeKind {
    RECTANGLE,
    CYLINDER,
    ELLIPSOID,
    SPLINE,
    LINE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable("architects_toolbox.shape." + id());
    }
}
