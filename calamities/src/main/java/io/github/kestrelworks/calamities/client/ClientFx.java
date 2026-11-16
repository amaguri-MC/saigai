package io.github.kestrelworks.calamities.client;

import io.github.kestrelworks.calamities.Calamities;
import io.github.kestrelworks.calamities.Disasters;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** What a player feels and hears: the shaking camera, the rumble, the gale pushing them and its howl. */
public final class ClientFx {

    private ClientFx() {}

    private static final RandomSource RNG = RandomSource.create();

    // Both disasters run on the world's clock (start game time + length), not on a countdown:
    // a paused world holds them, and jumping around a recording never leaves one running forever.

    // earthquake
    private static float quakeMag;
    private static int quakeTotal;
    private static long quakeStart;
    private static LoopSound rumble;
    private static float ph1, ph2, ph3, ph4;

    // gale
    private static float windX, windZ, windPush, windEnd;
    private static int windTotal;
    private static long windStart;

    // blizzard
    private static float blizX, blizZ;
    private static int blizTotal;
    private static long blizStart;
    private static float blizOpen, blizOpenPrev;   // how much of the open sky the player is under (smoothed), 0..1
    private static float blizNow, blizPrev;        // strength felt right now (envelope × open sky)
    private static LoopSound howl;

    private static long now() {
        ClientLevel l = Minecraft.getInstance().level;
        return l == null ? 0 : l.getGameTime();
    }

    /** Ticks since it started, or -1 when it is not running right now. */
    private static int elapsed(long start, int total, int tail) {
        long e = now() - start;
        return total > 0 && e >= 0 && e < total + tail ? (int) e : -1;
    }

    private static boolean quakeOn() { return elapsed(quakeStart, quakeTotal, 0) >= 0; }
    private static boolean windOn() { return elapsed(windStart, windTotal, 0) >= 0; }

    // The start time comes from the server, so a recording jumped through quickly still knows when each one began.
    public static void startQuake(float mag, int ticks, long start) {
        if (ticks <= 0) { quakeTotal = 0; return; }
        if (quakeOn()) mag = Math.max(mag, quakeMag);
        quakeMag = mag;
        quakeTotal = ticks;
        quakeStart = start;
        ph1 = RNG.nextFloat() * 6.283f; ph2 = RNG.nextFloat() * 6.283f; ph3 = RNG.nextFloat() * 6.283f; ph4 = RNG.nextFloat() * 6.283f;
        Minecraft mc = Minecraft.getInstance();
        // the rumble is one recording whose length and swell match the quake (short / medium / long)
        if (rumble != null && !rumble.isStopped()) mc.getSoundManager().stop(rumble);
        String len = ticks < 110 ? "s" : ticks < 155 ? "m" : "l";
        float vol = 0.45f + 0.75f * mag;
        rumble = new LoopSound(SoundEvent.createVariableRangeEvent(Calamities.id("quake." + len)), SoundSource.AMBIENT,
                () -> elapsed(quakeStart, quakeTotal, 20) >= 0 ? vol : -1f, () -> 1.0f - 0.12f * quakeMag).once(vol);
        mc.getSoundManager().play(rumble);
        // the first jolt
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                SoundEvent.createVariableRangeEvent(Calamities.id("quake.jolt")), 0.9f + RNG.nextFloat() * 0.15f, 0.4f + 0.6f * mag));
    }

    public static void startWind(float dx, float dz, float push, float endFactor, int ticks, int variant, long start) {
        if (ticks <= 0) { windTotal = 0; return; }
        windX = dx; windZ = dz; windPush = push; windEnd = endFactor;
        windTotal = ticks;
        windStart = start;
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(new LoopSound(SoundEvent.createVariableRangeEvent(Calamities.id(io.github.kestrelworks.calamities.Disasters.voiceSound("gale.", variant))),
                SoundSource.WEATHER, () -> elapsed(windStart, windTotal, 60) >= 0 ? 1.0f : -1f, () -> 1f).once());
    }

    /** Start (or lengthen: same start, more ticks) the blizzard. 0 ticks = it stops. */
    public static void startBlizzard(float dx, float dz, int ticks, long start) {
        if (ticks <= 0) { blizTotal = 0; return; }
        blizX = dx; blizZ = dz;
        blizTotal = ticks;
        blizStart = start;
        Minecraft mc = Minecraft.getInstance();
        if (howl == null || howl.isStopped() || !mc.getSoundManager().isActive(howl)) {
            // the howl follows how hard it blows where you stand (quieter under a roof)
            howl = new LoopSound(SoundEvent.createVariableRangeEvent(Calamities.id("blizzard.loop")), SoundSource.WEATHER,
                    () -> elapsed(blizStart, blizTotal, 20) >= 0 ? 0.15f + 0.85f * blizNow * (0.35f + 0.65f * blizOpen) : -1f,
                    () -> 0.96f + 0.08f * blizNow);
            mc.getSoundManager().play(howl);
        }
    }

    /** Blizzard strength by the clock alone (0..1). */
    private static float blizzardEnv() {
        int e = elapsed(blizStart, blizTotal, 0);
        return e < 0 ? 0 : Disasters.blizzardEnvelope(e, blizTotal);
    }

    /** How thick the snow and fog are for this player right now (0..1), smooth between ticks. */
    public static float blizzardFog(float partial) {
        return Mth.lerp(partial, blizPrev, blizNow);
    }

    public static void stopAll() {
        quakeTotal = 0;
        windTotal = 0;
        blizTotal = 0;
        blizNow = blizPrev = blizOpen = blizOpenPrev = 0;
    }

    /** 0..1 shaking strength right now (partial tick for smoothness). */
    public static float quakeAmp(float partial) {
        int e = elapsed(quakeStart, quakeTotal, 0);
        if (e < 0) return 0;
        return Disasters.envelope(e, quakeTotal) * quakeMag;
    }

    public static float windAmp() {
        int e = elapsed(windStart, windTotal, 0);
        if (e < 0) return 0;
        return Disasters.windEnvelope(e, windTotal);
    }

    public static boolean shaking() { return quakeOn() || windOn() || blizNow > 0.01f; }

    /** Camera offsets: {yaw, pitch, dx, dy, dz} in degrees / blocks. */
    public static float[] cameraOffset(Level level, float partial) {
        float t = (level.getGameTime() % 100000L) + partial;
        float q = quakeAmp(partial);
        float yaw = 0, pitch = 0, dx = 0, dy = 0, dz = 0;
        if (q > 0) {
            // a few unrelated sines = an irregular, physical-looking shake (~3–9 Hz)
            float a = (float) (Math.sin(t * 1.13 + ph1) * 0.55 + Math.sin(t * 2.31 + ph2) * 0.3 + Math.sin(t * 2.87 + ph3) * 0.15);
            float b = (float) (Math.sin(t * 1.41 + ph3) * 0.5 + Math.sin(t * 2.63 + ph4) * 0.35 + Math.sin(t * 1.97 + ph1) * 0.15);
            float c = (float) (Math.sin(t * 1.77 + ph2) * 0.6 + Math.sin(t * 2.97 + ph4) * 0.4);
            yaw += a * 2.4f * q;
            pitch += b * 1.7f * q;
            dx += c * 0.07f * q;
            dy += a * 0.05f * q;
            dz += b * 0.07f * q;
        }
        float w = windAmp();
        if (w > 0) {
            yaw += (float) (Math.sin(t * 0.21 + ph1) * 0.35 + Math.sin(t * 0.57) * 0.15) * w;
            pitch += (float) (Math.sin(t * 0.33 + ph2) * 0.2) * w;
        }
        float b = blizzardFog(partial);
        if (b > 0) {
            // buffeted, weaker than the gale
            yaw += (float) (Math.sin(t * 0.27 + ph3) * 0.22 + Math.sin(t * 0.71 + ph4) * 0.1) * b;
            pitch += (float) (Math.sin(t * 0.39 + ph1) * 0.12) * b;
        }
        return new float[]{yaw, pitch, dx, dy, dz};
    }

    public static void tick(Minecraft mc) {
        if (mc.isPaused()) return;
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null) return;
        // the world's own clock: a frozen or paused world (or a paused replay) freezes the disaster too
        if (!level.tickRateManager().runsNormally()) return;

        // the ground moving under your feet
        float q = quakeAmp(0);
        if (q > 0.3f && p.onGround() && !p.getAbilities().flying) {
            float t = level.getGameTime();
            double k = 0.018 * q;
            p.push(Math.sin(t * 0.9 + ph1) * k, 0, Math.cos(t * 0.77 + ph2) * k);
        }

        float w = windAmp();
        if (w > 0) {
            boolean sky = level.canSeeSky(p.blockPosition().above());
            if (!p.getAbilities().flying && !p.isSpectator()) {
                double k = windPush * w;
                if (level.dimension() == Level.END) k *= windEnd;
                if (p.isShiftKeyDown()) k *= 0.35;
                if (p.isInWater()) k *= 0.5;
                if (!sky) k *= 0.2;
                p.push(windX * k, 0, windZ * k);
            }
            particles(level, p, w);
        }

        // blizzard: thick in the open, thin under a roof (sky light where you stand, smoothed)
        blizPrev = blizNow;
        blizOpenPrev = blizOpen;
        float be = level.dimension() == Level.OVERWORLD ? blizzardEnv() : 0f;
        if (be > 0 || blizNow > 0) {
            BlockPos eye = BlockPos.containing(p.getEyePosition());
            float sky = level.getBrightness(net.minecraft.world.level.LightLayer.SKY, eye) / 15f;
            float target = clamp01((sky - 0.35f) / 0.6f);
            blizOpen += (target - blizOpen) * 0.08f;
            blizNow = be * (0.12f + 0.88f * blizOpen);
            if (blizNow < 0.002f && be <= 0) blizNow = 0;
            if (be > 0) snow(level, p, be);
        }
    }

    private static void snow(ClientLevel level, LocalPlayer p, float e) {
        // driving snow, spawned upwind and all around, blown past the player
        int n = Math.round(85 * e);
        for (int i = 0; i < n; i++) {
            double side = (RNG.nextDouble() - 0.5) * 32;
            double back = -6 + RNG.nextDouble() * 22;
            double x = p.getX() - blizX * back - blizZ * side;
            double z = p.getZ() - blizZ * back + blizX * side;
            double y = p.getY() + RNG.nextDouble() * 9 - 2.5;
            BlockPos bp = BlockPos.containing(x, y, z);
            if (!level.getBlockState(bp).isAir() || !level.canSeeSky(bp)) continue;
            double v = 0.45 + RNG.nextDouble() * 0.45;
            level.addParticle(Calamities.FLAKE, x, y, z, blizX * v, -0.06 - RNG.nextDouble() * 0.1, blizZ * v);
        }
        // a few gusts of fine powder
        int s = Math.round(5 * e);
        for (int i = 0; i < s; i++) {
            double side = (RNG.nextDouble() - 0.5) * 22;
            double back = -4 + RNG.nextDouble() * 16;
            double x = p.getX() - blizX * back - blizZ * side;
            double z = p.getZ() - blizZ * back + blizX * side;
            double y = p.getY() + RNG.nextDouble() * 4 - 0.6;
            BlockPos bp = BlockPos.containing(x, y, z);
            if (!level.getBlockState(bp).isAir() || !level.canSeeSky(bp)) continue;
            double v = 0.8 + RNG.nextDouble() * 0.5;
            level.addParticle(Calamities.STREAK, x, y, z, blizX * v, (RNG.nextDouble() - 0.5) * 0.03, blizZ * v);
        }
    }

    /** Fog color: pulled toward a cold grey-white by the blizzard, keeping the darkness of night. */
    public static int blizzardFogColor(int argb, float partial) {
        float b = blizzardFog(partial);
        if (b <= 0) return argb;
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, bl = argb & 255;
        float lum = (0.299f * r + 0.587f * g + 0.114f * bl) / 255f;
        // daylight sky ~0.6–0.75 → full white-grey; night ~0.05 → stays dark
        float k = Mth.clamp(lum / 0.6f, 0.08f, 1f);
        float tr = 200 * k, tg = 205 * k, tb = 212 * k;
        float m = b * 0.9f;
        int nr = Math.round(Mth.lerp(m, r, tr)), ng = Math.round(Mth.lerp(m, g, tg)), nb = Math.round(Mth.lerp(m, bl, tb));
        return (argb & 0xFF000000) | (Mth.clamp(nr, 0, 255) << 16) | (Mth.clamp(ng, 0, 255) << 8) | Mth.clamp(nb, 0, 255);
    }

    private static void particles(ClientLevel level, LocalPlayer p, float w) {
        int n = Math.round(15 * w);
        for (int i = 0; i < n; i++) {
            // thin streaks of air, spawned upwind and all around, flying past the player
            double side = (RNG.nextDouble() - 0.5) * 20;
            double back = -4 + RNG.nextDouble() * 16;
            double x = p.getX() - windX * back - windZ * side;
            double z = p.getZ() - windZ * back + windX * side;
            double y = p.getY() + RNG.nextDouble() * 4.5 - 0.6;
            BlockPos bp = BlockPos.containing(x, y, z);
            if (!level.getBlockState(bp).isAir() || !level.canSeeSky(bp)) continue;
            double v = 0.75 + RNG.nextDouble() * 0.6;
            level.addParticle(Calamities.STREAK, x, y, z, windX * v, (RNG.nextDouble() - 0.5) * 0.03, windZ * v);
        }
        if (RNG.nextFloat() < 0.12f * w) {
            double x = p.getX() + (RNG.nextDouble() - 0.5) * 14, z = p.getZ() + (RNG.nextDouble() - 0.5) * 14;
            double y = p.getY() + RNG.nextDouble() * 3;
            if (level.canSeeSky(BlockPos.containing(x, y, z))) level.addParticle(ParticleTypes.SMALL_GUST, x, y, z, 0, 0, 0);
        }
        // leaves torn off nearby trees
        for (int i = 0; i < 6; i++) {
            BlockPos q = p.blockPosition().offset(RNG.nextInt(17) - 8, RNG.nextInt(9) - 1, RNG.nextInt(17) - 8);
            if (level.getBlockState(q).is(BlockTags.LEAVES) && level.getBlockState(q.below()).isAir() && RNG.nextFloat() < 0.5f * w) {
                int col = level.getBlockTint(q, net.minecraft.client.renderer.BiomeColors.FOLIAGE_COLOR_RESOLVER);
                double v = 0.35 + RNG.nextDouble() * 0.3;
                level.addParticle(ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, col | 0xFF000000),
                        q.getX() + RNG.nextDouble(), q.getY() - 0.05, q.getZ() + RNG.nextDouble(), windX * v, -0.02, windZ * v);
            }
        }
    }

    static Vec3 wind() { return new Vec3(windX, 0, windZ); }

    static float clamp01(float f) { return Mth.clamp(f, 0, 1); }
}
