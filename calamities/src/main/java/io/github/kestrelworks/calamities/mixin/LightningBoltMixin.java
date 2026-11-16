package io.github.kestrelworks.calamities.mixin;

import io.github.kestrelworks.calamities.Calamities;
import io.github.kestrelworks.calamities.Disasters;
import io.github.kestrelworks.calamities.StormBolt;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Storm bolts roll their own thunder instead of the vanilla one (the crack of the impact stays vanilla). */
@Mixin(LightningBolt.class)
public abstract class LightningBoltMixin {

    @Redirect(method = "tick", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/world/level/Level;playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V"))
    private void calamities$thunder(Level level, double x, double y, double z, SoundEvent event, SoundSource source,
                                    float volume, float pitch, boolean distanceDelay) {
        // variant < 0: a bolt of the ongoing thunderstorm, which rolls the ordinary thunder (the voice is only on the key-press bolts)
        if ((Object) this instanceof StormBolt bolt && bolt.variant() >= 0) {
            SoundEvent own = SoundEvent.createVariableRangeEvent(Calamities.id(Disasters.voiceSound("storm.thunder.", bolt.variant())));
            level.playLocalSound(x, y, z, own, source, volume, 1.0f, distanceDelay);
            return;
        }
        level.playLocalSound(x, y, z, event, source, volume, pitch, distanceDelay);
    }
}
