package net.pvsync.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.waypoints.WaypointTransmitter;
import net.pvsync.Vis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** H15: no locator bar dot for someone of another group (after Vanish WaypointTransmitterMixin, MIT). */
@Mixin(WaypointTransmitter.class)
public interface WaypointMixin {
    @Inject(method = "doesSourceIgnoreReceiver", at = @At("HEAD"), cancellable = true)
    private static void pv$dot(LivingEntity source, ServerPlayer receiver, CallbackInfoReturnable<Boolean> cir) {
        if (source instanceof ServerPlayer actor && !Vis.real(actor.getUUID(), receiver.getUUID())) cir.setReturnValue(true);
    }
}
