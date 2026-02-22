package com.ArchitectsToolbox.shapes;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public enum ShapeMode {
    RECTANGLE("rectangle", "architects_toolbox.shape.rectangle"),
    CYLINDER("cylinder", "architects_toolbox.shape.cylinder"),
    ELLIPSOID("ellipsoid", "architects_toolbox.shape.ellipsoid"),
    SPLINE("spline", "architects_toolbox.shape.spline"),
    LINE("line", "architects_toolbox.shape.line");

    private final String id;
    private final String translationKey;

    ShapeMode(String id, String translationKey) {
        this.id = id;
        this.translationKey = translationKey;
    }

    public String getId() {
        return id;
    }

    public Component getDisplayName() {
        return Component.translatable(translationKey);
    }

    public ResourceLocation getIconTexture() {
        return ResourceLocation.fromNamespaceAndPath("architects_toolbox", "textures/gui/icons/" + id + ".png");
    }
}
