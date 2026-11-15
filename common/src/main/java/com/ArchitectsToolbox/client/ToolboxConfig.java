package com.ArchitectsToolbox.client;

import com.ArchitectsToolbox.ArchitectsToolbox;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The saved settings, from {@code config/architects_toolbox.json}. The file keeps the format 1.0.0 wrote for
 * {@code ghostBlockRenderMode} and {@code ghostBlockOpacity}; settings added in 2.0.0 get defaults when absent.
 * The palette contains opaque ARGB colours and is saved as named colours or #RRGGBB strings.
 *
 * @param placeReplaces whether placing a paste in creative replaces blocks in the way, not only air
 */
public record ToolboxConfig(GhostStyle ghostStyle, float ghostOpacity, boolean placeReplaces,
                            List<Integer> palette, int measurementLimit) {
    /** The wheel and config screen stay usable with a bounded number of palette entries. */
    public static final int MAX_PALETTE_SIZE = 16;
    public static final int MAX_MEASUREMENTS = 256;
    public static final ToolboxConfig DEFAULT = new ToolboxConfig(GhostStyle.SOLID, 0.5f, false,
            MeasurementColours.DEFAULT_PALETTE, 16);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public enum GhostStyle {
        WIREFRAME,
        SOLID
    }

    public ToolboxConfig {
        if (!(ghostOpacity >= 0.0f && ghostOpacity <= 1.0f)) {
            throw new IllegalArgumentException("ghost opacity must be between 0 and 1: " + ghostOpacity);
        }
        palette = List.copyOf(palette);
        if (palette.isEmpty() || palette.size() > MAX_PALETTE_SIZE || palette.stream().anyMatch(c -> (c >>> 24) != 0xFF)) {
            throw new IllegalArgumentException("palette must contain 1 to " + MAX_PALETTE_SIZE + " opaque colours");
        }
        if (measurementLimit < 1 || measurementLimit > MAX_MEASUREMENTS) {
            throw new IllegalArgumentException("measurement limit must be between 1 and " + MAX_MEASUREMENTS);
        }
    }

    /** Reads the file; missing or invalid settings fall back independently to their defaults. */
    public static ToolboxConfig load(Path file) {
        try (Reader reader = Files.newBufferedReader(file)) {
            Fields fields = GSON.fromJson(reader, Fields.class);
            if (fields == null) {
                return DEFAULT;
            }
            GhostStyle style = fields.ghostBlockRenderMode != null ? fields.ghostBlockRenderMode : DEFAULT.ghostStyle;
            float opacity = fields.ghostBlockOpacity != null && !fields.ghostBlockOpacity.isNaN()
                    ? Math.clamp(fields.ghostBlockOpacity, 0.0f, 1.0f) : DEFAULT.ghostOpacity;
            boolean replaces = fields.placeReplacesBlocks != null ? fields.placeReplacesBlocks : DEFAULT.placeReplaces;
            List<Integer> colours = fields.measurementPalette == null ? null : parsePalette(fields.measurementPalette);
            int limit = fields.measurementLimit != null && fields.measurementLimit >= 1 && fields.measurementLimit <= MAX_MEASUREMENTS
                    ? fields.measurementLimit : DEFAULT.measurementLimit;
            return new ToolboxConfig(style, opacity, replaces, colours != null ? colours : DEFAULT.palette, limit);
        } catch (NoSuchFileException e) {
            return DEFAULT;
        } catch (IOException | JsonParseException e) {
            ArchitectsToolbox.LOGGER.warn("Could not read {}, using the default settings", file, e);
            return DEFAULT;
        }
    }

    /** Parses the list used by the config screen and JSON file; null means invalid. */
    public static @Nullable List<Integer> parsePalette(List<String> entries) {
        if (entries.isEmpty() || entries.size() > MAX_PALETTE_SIZE) {
            return null;
        }
        List<Integer> colours = new ArrayList<>(entries.size());
        for (String entry : entries) {
            Integer colour = entry == null ? null : MeasurementColours.parse(entry);
            if (colour == null) {
                return null;
            }
            colours.add(colour);
        }
        return List.copyOf(colours);
    }

    /** Writes the file; a failure is logged and the settings stay in use. */
    public void save(Path file) {
        Fields fields = new Fields();
        fields.ghostBlockRenderMode = ghostStyle;
        fields.ghostBlockOpacity = ghostOpacity;
        fields.placeReplacesBlocks = placeReplaces;
        fields.measurementPalette = palette.stream().map(MeasurementColours::hex).toList();
        fields.measurementLimit = measurementLimit;
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(fields, writer);
            }
        } catch (IOException e) {
            ArchitectsToolbox.LOGGER.warn("Could not save {}", file, e);
        }
    }

    public ToolboxConfig withGhostStyle(GhostStyle next) {
        return new ToolboxConfig(next, ghostOpacity, placeReplaces, palette, measurementLimit);
    }

    public ToolboxConfig withGhostOpacity(float next) {
        return new ToolboxConfig(ghostStyle, next, placeReplaces, palette, measurementLimit);
    }

    public ToolboxConfig withPlaceReplaces(boolean next) {
        return new ToolboxConfig(ghostStyle, ghostOpacity, next, palette, measurementLimit);
    }

    public ToolboxConfig withPalette(List<Integer> next) {
        return new ToolboxConfig(ghostStyle, ghostOpacity, placeReplaces, next, measurementLimit);
    }

    public ToolboxConfig withMeasurementLimit(int next) {
        return new ToolboxConfig(ghostStyle, ghostOpacity, placeReplaces, palette, next);
    }

    /** The file's fields, the first two under the names 1.0.0 gave them. */
    private static final class Fields {
        @Nullable GhostStyle ghostBlockRenderMode;
        @Nullable Float ghostBlockOpacity;
        @Nullable Boolean placeReplacesBlocks;
        @Nullable List<String> measurementPalette;
        @Nullable Integer measurementLimit;
    }
}
