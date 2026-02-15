package com.ArchitectsToolbox.fabric.client;

import com.ArchitectsToolbox.ArchitectsToolbox;
import com.ArchitectsToolbox.ArchitectsToolboxClient;
import net.fabricmc.api.ClientModInitializer;

public final class ArchitectsToolboxFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ArchitectsToolbox.init();
        ArchitectsToolboxClient.initClient();
    }
}
