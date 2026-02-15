package com.ArchitectsToolbox.neoforge;

import com.ArchitectsToolbox.ArchitectsToolbox;
import com.ArchitectsToolbox.ArchitectsToolboxClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(ArchitectsToolbox.MOD_ID)
public final class ArchitectsToolboxNeoForge {
    public ArchitectsToolboxNeoForge(IEventBus modEventBus) {
        ArchitectsToolbox.init();

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            modEventBus.addListener(this::onClientSetup);
        }
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        ArchitectsToolboxClient.initClient();
    }
}
