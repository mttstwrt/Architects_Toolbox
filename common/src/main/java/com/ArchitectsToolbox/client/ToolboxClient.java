package com.ArchitectsToolbox.client;

import com.ArchitectsToolbox.render.RenderProbe;
import com.ArchitectsToolbox.render.WorldRenderLayer;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;

/**
 * The client side of the mod: owns the settings and decides what is drawn in the world. Made once by each loader's
 * entry point, which ticks it on the client thread.
 */
public final class ToolboxClient {
    private final RenderProbe probe;

    public ToolboxClient(Path configFile) {
        this.probe = new RenderProbe(ToolboxConfig.load(configFile).ghostOpacity());
    }

    public void tick(Minecraft minecraft) {
        WorldRenderLayer.show(probe.update(minecraft));
    }
}
