package io.github.kestrelworks.calamities.mixin.client;

import io.github.kestrelworks.calamities.client.ClientFx;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The view shakes during an earthquake and sways a little in a gale. */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow private float xRot;
    @Shadow private float yRot;
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow protected abstract void setPosition(Vec3 pos);
    @Shadow public abstract Vec3 position();

    @Inject(method = "setup", at = @At("TAIL"))
    private void calamities$shake(Level level, Entity entity, boolean detached, boolean mirror, float partial, CallbackInfo ci) {
        if (!ClientFx.shaking()) return;
        float[] o = ClientFx.cameraOffset(level, partial);
        setRotation(yRot + o[0], xRot + o[1]);
        setPosition(position().add(o[2], o[3], o[4]));
    }
}
