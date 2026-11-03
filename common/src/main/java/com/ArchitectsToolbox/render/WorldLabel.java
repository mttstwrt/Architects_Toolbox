package com.ArchitectsToolbox.render;

import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** Text that faces the camera at a point in the world, readable through blocks. */
public record WorldLabel(Vec3 pos, Component text, int argb) {}
