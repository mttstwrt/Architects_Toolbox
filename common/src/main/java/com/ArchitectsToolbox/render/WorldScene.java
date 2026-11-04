package com.ArchitectsToolbox.render;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the mod draws in the world. Immutable: whoever owns what is shown builds a new scene when it changes and
 * hands it to {@code WorldRenderLayer.show}, which draws it every frame until the next one.
 */
public record WorldScene(List<LineMesh> lines, List<GhostMesh> ghosts, List<WorldLabel> labels) {
    public static final WorldScene EMPTY = new WorldScene(List.of(), List.of(), List.of());

    public WorldScene {
        lines = List.copyOf(lines);
        ghosts = List.copyOf(ghosts);
        labels = List.copyOf(labels);
    }

    /** One scene holding everything in {@code scenes}. */
    public static WorldScene join(List<WorldScene> scenes) {
        List<LineMesh> lines = new ArrayList<>();
        List<GhostMesh> ghosts = new ArrayList<>();
        List<WorldLabel> labels = new ArrayList<>();
        for (WorldScene scene : scenes) {
            lines.addAll(scene.lines);
            ghosts.addAll(scene.ghosts);
            labels.addAll(scene.labels);
        }
        return new WorldScene(lines, ghosts, labels);
    }

    public boolean isEmpty() {
        return lines.isEmpty() && ghosts.isEmpty() && labels.isEmpty();
    }
}
