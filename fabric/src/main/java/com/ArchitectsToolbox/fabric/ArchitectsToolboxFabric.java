package com.ArchitectsToolbox.fabric;

import com.ArchitectsToolbox.ArchitectsToolbox;
import com.ArchitectsToolbox.client.ToolboxClient;
import com.ArchitectsToolbox.client.ToolboxKeys;
import com.ArchitectsToolbox.gui.GameCanvas;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
//? if >=26.1 {
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
//?} else {
/*import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
*///?}
import net.fabricmc.loader.api.FabricLoader;

/** Fabric's entry point: makes the client and connects it to Fabric's events. */
public final class ArchitectsToolboxFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ToolboxClient client = new ToolboxClient(FabricLoader.getInstance().getConfigDir().resolve("architects_toolbox.json"));
        //? if >=26.1 {
        ToolboxKeys.ALL.forEach(KeyMappingHelper::registerKeyMapping);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(ArchitectsToolbox.MOD_ID, "block_count"),
                (graphics, deltaTracker) -> client.drawHud(new GameCanvas(graphics)));
        //?} else {
        /*ToolboxKeys.ALL.forEach(KeyBindingHelper::registerKeyBinding);
        HudRenderCallback.EVENT.register((graphics, deltaTracker) -> client.drawHud(new GameCanvas(graphics)));
        *///?}
        ClientTickEvents.END_CLIENT_TICK.register(client::tick);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
                dispatcher.register(client.<FabricClientCommandSource>commands()));
    }
}
