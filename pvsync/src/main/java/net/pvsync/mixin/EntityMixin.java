package net.pvsync.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.pvsync.Vis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** [M]: a director's body is not sent to group players (every other body is sent as usual). */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
    private void pv$body(ServerPlayer observer, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayer actor && Vis.see(actor.getUUID(), observer.getUUID()) == Vis.See.HIDDEN) {
            cir.setReturnValue(false);
        }
    }
}
