package io.github.kestrelworks.calamities.mixin.client;

import io.github.kestrelworks.calamities.client.ClientFx;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** In a blizzard the world vanishes into a grey-white wall of snow a couple of dozen blocks away. */
@Mixin(AtmosphericFogEnvironment.class)
public class BlizzardFogMixin {

    @Inject(method = "setupFog", at = @At("TAIL"))
    private void calamities$blizzardFog(FogData fog, Camera camera, ClientLevel level, float rd, DeltaTracker dt, CallbackInfo ci) {
        float b = ClientFx.blizzardFog(dt.getGameTimeDeltaPartialTick(false));
        if (b <= 0) return;
        float s = b * b * (3 - 2 * b);
        fog.environmentalStart = Mth.lerp(s, fog.environmentalStart, 1.5f);
        fog.environmentalEnd = Mth.lerp(s, fog.environmentalEnd, 30f);
        fog.skyEnd = Mth.lerp(s, fog.skyEnd, 30f);
        fog.cloudEnd = Mth.lerp(s, fog.cloudEnd, 30f);
    }

    @Inject(method = "getBaseColor", at = @At("RETURN"), cancellable = true)
    private void calamities$blizzardColor(ClientLevel level, Camera camera, int rd, float partial, CallbackInfoReturnable<Integer> cir) {
        int c = cir.getReturnValueI();
        int n = ClientFx.blizzardFogColor(c, partial);
        if (n != c) cir.setReturnValue(n);
    }
}
