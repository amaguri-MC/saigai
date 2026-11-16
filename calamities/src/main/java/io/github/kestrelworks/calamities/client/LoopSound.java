package io.github.kestrelworks.calamities.client;

import java.util.function.Supplier;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** A sound heard "everywhere around you" whose volume follows a supplier; a negative volume ends it. */
public class LoopSound extends AbstractTickableSoundInstance {

    private final Supplier<Float> vol, pit;
    private float smooth = 0;

    public LoopSound(SoundEvent event, SoundSource source, Supplier<Float> volume, Supplier<Float> pitch) {
        super(event, source, RandomSource.create());
        this.vol = volume;
        this.pit = pitch;
        this.looping = true;
        this.delay = 0;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.x = this.y = this.z = 0;
        this.volume = 0.001f;
        this.pitch = pitch.get();
    }

    /** Play once instead of looping. */
    public LoopSound once() { return once(1.0f); }

    public LoopSound once(float v) {
        this.looping = false;
        this.volume = v;
        this.smooth = v;
        return this;
    }

    @Override
    public boolean canStartSilent() { return true; }

    @Override
    public void tick() {
        float v = vol.get();
        if (v < 0) { stop(); return; }
        smooth += (v - smooth) * 0.25f;
        this.volume = Math.max(0.001f, smooth);
        this.pitch = pit.get();
    }
}
