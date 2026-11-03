package com.ArchitectsToolbox.neoforge;

import com.ArchitectsToolbox.ArchitectsToolbox;
import com.ArchitectsToolbox.client.ToolboxClient;
import com.ArchitectsToolbox.client.ToolboxKeys;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

/** NeoForge's entry point, on the client only: makes the client and connects it to NeoForge's events. */
@Mod(value = ArchitectsToolbox.MOD_ID, dist = Dist.CLIENT)
public final class ArchitectsToolboxNeoForge {
    public ArchitectsToolboxNeoForge(IEventBus modBus) {
        ToolboxClient client = new ToolboxClient(FMLPaths.CONFIGDIR.get().resolve("architects_toolbox.json"));
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            //? if >=26.1 {
            event.registerCategory(ToolboxKeys.CATEGORY);
            //?}
            event.register(ToolboxKeys.MEASURE);
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> client.tick(Minecraft.getInstance()));
    }
}
