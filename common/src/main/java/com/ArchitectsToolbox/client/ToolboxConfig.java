package com.ArchitectsToolbox.client;

import com.ArchitectsToolbox.ArchitectsToolbox;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/**
 * The saved settings, from {@code config/architects_toolbox.json}. The file keeps the format 1.0.0 wrote, so a 1.0.0
 * config carries over: {@code ghostBlockRenderMode} (WIREFRAME or SOLID) and {@code ghostBlockOpacity} (0 to 1).
 */
public record ToolboxConfig(GhostStyle ghostStyle, float ghostOpacity) {
    public static final ToolboxConfig DEFAULT = new ToolboxConfig(GhostStyle.WIREFRAME, 0.5f);
    private static final Gson GSON = new Gson();

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
            return new ToolboxConfig(style, opacity);
        } catch (NoSuchFileException e) {
            return DEFAULT;
        } catch (IOException | JsonParseException e) {
            ArchitectsToolbox.LOGGER.warn("Could not read {}, using the default settings", file, e);
            return DEFAULT;
        }
    }

    /** The file's fields under the names 1.0.0 gave them. */
    private static final class Fields {
        @Nullable GhostStyle ghostBlockRenderMode;
        @Nullable Float ghostBlockOpacity;
    }
}
