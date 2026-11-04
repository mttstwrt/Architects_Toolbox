package com.ArchitectsToolbox.render;

/**
 * Ghost blocks drawn see-through at one opacity (0 to 1), with their relative positions counted from an origin: a
 * paste preview or a locked placement. Immutable. Moving a mesh is a new origin for the same blocks, which keeps
 * their prepared models.
 */
public record GhostMesh(GhostBlocks blocks, int originX, int originY, int originZ, float opacity) {}
