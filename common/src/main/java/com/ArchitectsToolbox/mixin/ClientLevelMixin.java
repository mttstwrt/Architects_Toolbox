package com.ArchitectsToolbox.mixin;

import com.ArchitectsToolbox.client.BlockChanges;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hears of every block the client's world changes, from the server or the player, in
 * {@code ClientLevel.sendBlockUpdated}, so the block count recounts when a counted block changes. Neither loader has
 * a client-side event for it.
 */
@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {
    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void architects_toolbox$blockChanged(BlockPos pos, BlockState old, BlockState current, int flags, CallbackInfo callback) {
        BlockChanges.changed(pos);
    }
}
