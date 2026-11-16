package io.github.kestrelworks.calamities.client;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.QuadParticleRenderState;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** A thin, fast streak of air: a short chain of tiny soft dots along its own motion, fading toward the tail. */
public class StreakParticle extends SingleQuadParticle {

    private static final int SEGMENTS = 64;
    private final float maxAlpha;

    protected StreakParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, RandomSource r) {
        super(level, x, y, z, sprites.get(r));
        this.xd = vx; this.yd = vy; this.zd = vz;
        this.friction = 1.0f;
        this.gravity = 0f;
        this.hasPhysics = false;
        this.lifetime = 16 + r.nextInt(14);
        this.quadSize = 0.04f + r.nextFloat() * 0.02f;
        this.maxAlpha = 0.34f + r.nextFloat() * 0.26f;
        float g = 0.88f + r.nextFloat() * 0.12f;
        this.setColor(g, g, g * 1.02f);
        this.alpha = 0f;
    }

    @Override
    public void tick() {
        super.tick();
        float t = age / (float) lifetime;
        this.alpha = maxAlpha * Mth.clamp(Math.min(t / 0.2f, (1 - t) / 0.35f), 0f, 1f);
        // a little flutter so the lines are not ruler-straight
        this.yd += (random.nextFloat() - 0.5f) * 0.006f;
    }

    @Override
    public void extract(QuadParticleRenderState state, Camera camera, float partial) {
        Quaternionf q = new Quaternionf();
        this.getFacingCameraMode().setRotation(q, camera, partial);
        Vec3 cam = camera.position();
        double x = Mth.lerp(partial, xo, this.x), y = Mth.lerp(partial, yo, this.y), z = Mth.lerp(partial, zo, this.z);
        float base = this.alpha;
        double len = Math.sqrt(xd * xd + yd * yd + zd * zd);
        if (len < 1e-4) return;
        // dots closer than their own size so the trail reads as one continuous line, ~2.6 blocks long
        double step = quadSize * 0.7;
        int n = (int) Math.min(SEGMENTS, 2.6 / step);
        double dx = xd / len * step, dy = yd / len * step, dz = zd / len * step;
        for (int i = 0; i < n; i++) {
            float u = i / (float) n;
            float a = base * (1f - u) * Math.min(1f, (i + 1) / 4f);
            if (a <= 0.004f) continue;
            this.alpha = a;
            extractRotatedQuad(state, q, (float) (x - dx * i - cam.x), (float) (y - dy * i - cam.y), (float) (z - dz * i - cam.z), partial);
        }
        this.alpha = base;
    }

    @Override
    protected Layer getLayer() { return Layer.TRANSLUCENT; }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz, RandomSource r) {
            return new StreakParticle(level, x, y, z, vx, vy, vz, sprites, r);
        }
    }
}
