package com.ArchitectsToolbox.fabric;

import com.ArchitectsToolbox.client.ToolboxClient;
import com.ArchitectsToolbox.client.ToolboxKeys;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
//? if >=26.1 {
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
//?} else {
/*import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
*///?}
import net.fabricmc.loader.api.FabricLoader;

/** Fabric's entry point: makes the client and connects it to Fabric's events. */
public final class ArchitectsToolboxFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ToolboxClient client = new ToolboxClient(FabricLoader.getInstance().getConfigDir().resolve("architects_toolbox.json"));
        //? if >=26.1 {
        KeyMappingHelper.registerKeyMapping(ToolboxKeys.MEASURE);
        //?} else {
        /*KeyBindingHelper.registerKeyBinding(ToolboxKeys.MEASURE);
        *///?}
        ClientTickEvents.END_CLIENT_TICK.register(client::tick);
    }
}
