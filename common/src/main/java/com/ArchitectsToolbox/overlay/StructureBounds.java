package com.ArchitectsToolbox.overlay;

import com.ArchitectsToolbox.geometry.BlockBox;
import com.ArchitectsToolbox.render.LineMesh;
import com.ArchitectsToolbox.render.MeasurementScene;
import com.ArchitectsToolbox.render.WorldLabel;
import com.ArchitectsToolbox.render.WorldScene;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * The structure bounds overlay (F42): each structure's whole box, with its id above it, and optionally the boxes of
 * its pieces in a lighter shade. Each structure type has its own colour from a fixed list, the same in every session.
 */
final class StructureBounds {
    private static final int[] COLOURS = {
            0xFFFF9933, 0xFFFF66CC, 0xFFB07CFF, 0xFF40E0D0, 0xFFFFE040, 0xFFA0FF40,
            0xFF60B0FF, 0xFFFF7060, 0xFFE0E0E0, 0xFFD4A017, 0xFFE040FF, 0xFF80FFC0,
    };

    private StructureBounds() {
    }

    static WorldScene build(List<StructureScanner.Found> structures, boolean pieces) {
        List<LineMesh> meshes = new ArrayList<>(structures.size());
        List<WorldLabel> labels = new ArrayList<>(structures.size());
        for (StructureScanner.Found structure : structures) {
            BlockBox bounds = structure.bounds();
            int colour = colour(structure.id());
            LineMesh.Builder lines = LineMesh.builder(bounds.minX(), bounds.minY(), bounds.minZ());
            if (pieces) {
                int pieceColour = MeasurementScene.lighter(colour);
                for (BlockBox piece : structure.pieces()) {
                    box(lines, piece, pieceColour);
                }
            }
            box(lines, bounds, colour);
            meshes.add(lines.build());
            Vec3 top = new Vec3((bounds.minX() + bounds.maxX() + 1) / 2.0, bounds.maxY() + 1.5, (bounds.minZ() + bounds.maxZ() + 1) / 2.0);
            labels.add(new WorldLabel(top, Component.literal(structure.id()), colour));
        }
        return new WorldScene(meshes, List.of(), labels);
    }

    private static void box(LineMesh.Builder lines, BlockBox box, int argb) {
        lines.box(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1, argb);
    }

    private static int colour(String id) {
        return COLOURS[Math.floorMod(id.hashCode(), COLOURS.length)];
    }
}
