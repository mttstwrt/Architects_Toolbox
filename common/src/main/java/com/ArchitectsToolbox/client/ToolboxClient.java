package com.ArchitectsToolbox.client;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;

/**
 * The client side of the mod: owns the settings and decides what is drawn in the world. Made once by each loader's
 * entry point, which ticks it on the client thread.
 */
public final class ToolboxClient {
    private final ToolboxConfig config;

    public ToolboxClient(Path configFile) {
        this.config = ToolboxConfig.load(configFile);
    }

    public ToolboxConfig config() {
        return config;
    }

    /** Nothing to do until the measurements arrive in ATB-013. */
    public void tick(Minecraft minecraft) {
    }
}
