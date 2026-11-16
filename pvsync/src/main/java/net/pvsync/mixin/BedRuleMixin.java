package net.pvsync.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.level.Level;
import net.pvsync.Ctx;
import net.pvsync.Sky;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BedRule.Rule.class)
public abstract class BedRuleMixin {
    @Inject(method = "test", at = @At("HEAD"), cancellable = true)
    private void pv$dark(Level level, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this != BedRule.Rule.WHEN_DARK || level.isClientSide()) return;
        ServerPlayer p = Ctx.SLEEPER.get();
        if (p == null || p.level() != level) return;
        cir.setReturnValue(Sky.darkFor(p));
    }
}
