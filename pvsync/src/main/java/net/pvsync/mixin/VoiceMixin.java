package net.pvsync.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.pvsync.Vis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** H12: Simple Voice Chat asks canSee(observer, target) before sending a voice state. Applied only when SVC is present. */
@Pseudo
@Mixin(targets = "de.maxhenkel.voicechat.intercompatibility.FabricCommonCompatibilityManager", remap = false)
public abstract class VoiceMixin {
    @Inject(method = "canSee", at = @At("HEAD"), cancellable = true)
    private void pv$see(ServerPlayer observer, ServerPlayer target, CallbackInfoReturnable<Boolean> cir) {
        if (!Vis.real(target.getUUID(), observer.getUUID())) cir.setReturnValue(false);
    }
}
