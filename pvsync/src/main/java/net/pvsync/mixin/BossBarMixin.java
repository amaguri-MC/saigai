package net.pvsync.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.pvsync.Owners;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** H18: a bossbar made by a group player is shown to that group only. */
@Mixin(CustomBossEvents.class)
public abstract class BossBarMixin {
    @Inject(method = "create", at = @At("RETURN"))
    private void pv$made(Identifier id, Component name, CallbackInfoReturnable<CustomBossEvent> cir) {
        Owners.barUuid(cir.getReturnValue().getId(), id.toString());
        Owners.record("bar:" + id);
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void pv$gone(CustomBossEvent bar, CallbackInfo ci) {
        Owners.forget("bar:" + bar.getTextId());
    }
}
