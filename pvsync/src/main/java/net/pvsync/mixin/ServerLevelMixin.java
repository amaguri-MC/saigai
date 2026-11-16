package net.pvsync.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.pvsync.Sky;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/** H13 / H14: sleeping and weather per group. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void pv$sleep(BooleanSupplier hasTime, CallbackInfo ci) {
        Sky.tickSleep((ServerLevel) (Object) this);
    }

    @Inject(method = "updateSleepingPlayerList", at = @At("HEAD"), cancellable = true)
    private void pv$sleepers(CallbackInfo ci) {
        Sky.updateSleep((ServerLevel) (Object) this);
        ci.cancel();
    }

    @Inject(method = "advanceWeatherCycle", at = @At("HEAD"), cancellable = true)
    private void pv$weather(CallbackInfo ci) {
        ServerLevel self = (ServerLevel) (Object) this;
        if (self.dimension() == Level.OVERWORLD) {
            Sky.tickWeather(self);
            ci.cancel();
        }
    }
}
