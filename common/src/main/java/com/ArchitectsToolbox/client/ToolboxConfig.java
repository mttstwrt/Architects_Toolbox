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
import org.jspecify.annotations.Nullable;

/**
 * The saved settings, from {@code config/architects_toolbox.json}. The file keeps the format 1.0.0 wrote, so a 1.0.0
 * config carries over: {@code ghostBlockRenderMode} (WIREFRAME or SOLID) and {@code ghostBlockOpacity} (0 to 1).
 * 2.0.0 adds {@code placeReplacesBlocks}, which 1.0.0 ignores.
 *
 * @param placeReplaces whether placing a paste in creative (F38) replaces blocks in the way, not only air
 */
public record ToolboxConfig(GhostStyle ghostStyle, float ghostOpacity, boolean placeReplaces) {
    public static final ToolboxConfig DEFAULT = new ToolboxConfig(GhostStyle.SOLID, 0.5f, false);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** How ghosts are drawn: outlines only, or see-through block models inside their outlines. */
    public enum GhostStyle {
        WIREFRAME,
        SOLID
    }

    public ToolboxConfig {
        if (!(ghostOpacity >= 0.0f && ghostOpacity <= 1.0f)) {
            throw new IllegalArgumentException("ghost opacity must be between 0 and 1: " + ghostOpacity);
        }
    }

    /** Reads the file; a missing or unreadable file, or a missing or unknown value, falls back to the default. */
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
            return new ToolboxConfig(style, opacity, replaces);
        } catch (NoSuchFileException e) {
            return DEFAULT;
        } catch (IOException | JsonParseException e) {
            ArchitectsToolbox.LOGGER.warn("Could not read {}, using the default settings", file, e);
            return DEFAULT;
        }
    }

    /** Writes the file; a failure is logged and the settings stay in use. */
    public void save(Path file) {
        Fields fields = new Fields();
        fields.ghostBlockRenderMode = ghostStyle;
        fields.ghostBlockOpacity = ghostOpacity;
        fields.placeReplacesBlocks = placeReplaces;
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
        return new ToolboxConfig(next, ghostOpacity, placeReplaces);
    }

    public ToolboxConfig withGhostOpacity(float next) {
        return new ToolboxConfig(ghostStyle, next, placeReplaces);
    }

    public ToolboxConfig withPlaceReplaces(boolean next) {
        return new ToolboxConfig(ghostStyle, ghostOpacity, next);
    }

    /** The file's fields, the first two under the names 1.0.0 gave them. */
    private static final class Fields {
        @Nullable GhostStyle ghostBlockRenderMode;
        @Nullable Float ghostBlockOpacity;
        @Nullable Boolean placeReplacesBlocks;
    }
}
