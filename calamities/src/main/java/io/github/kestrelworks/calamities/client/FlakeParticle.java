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

/** A snowflake driven sideways by the blizzard: a soft white dot with a short smear behind it, tumbling a little. */
public class FlakeParticle extends SingleQuadParticle {

    private final float maxAlpha;
    private final float wob, wobPh;

    protected FlakeParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, RandomSource r) {
        super(level, x, y, z, sprites.get(r));
        this.xd = vx; this.yd = vy; this.zd = vz;
        this.friction = 1.0f;
        this.gravity = 0f;
        this.hasPhysics = true;
        this.lifetime = 28 + r.nextInt(26);
        // mostly fine flakes, now and then a big clump
        float s = r.nextFloat();
        this.quadSize = 0.035f + s * s * 0.09f;
        this.maxAlpha = 0.72f + r.nextFloat() * 0.26f;
        float g = 0.93f + r.nextFloat() * 0.07f;
        this.setColor(g, g, Math.min(1f, g * 1.03f));
        this.alpha = 0f;
        this.wob = 0.012f + r.nextFloat() * 0.02f;
        this.wobPh = r.nextFloat() * 6.283f;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.onGround) {
            // landed: lie still and melt into the snow
            this.xd = this.zd = 0;
            this.yd = 0;
            if (age < lifetime - 8) age = lifetime - 8;
        }
        float t = age / (float) lifetime;
        this.alpha = maxAlpha * Mth.clamp(Math.min(t / 0.12f, (1 - t) / 0.25f), 0f, 1f);
        // eddies in the wind
        this.yd += Math.sin(age * 0.45f + wobPh) * wob * 0.35f;
        this.xd += (random.nextFloat() - 0.5f) * wob;
        this.zd += (random.nextFloat() - 0.5f) * wob;
    }

    @Override
    public void extract(QuadParticleRenderState state, Camera camera, float partial) {
        Quaternionf q = new Quaternionf();
        this.getFacingCameraMode().setRotation(q, camera, partial);
        Vec3 cam = camera.position();
        double x = Mth.lerp(partial, xo, this.x), y = Mth.lerp(partial, yo, this.y), z = Mth.lerp(partial, zo, this.z);
        float base = this.alpha;
        double len = Math.sqrt(xd * xd + yd * yd + zd * zd);
        // the flake itself
        extractRotatedQuad(state, q, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z), partial);
        if (len < 0.05) return;
        // a short smear behind it (motion blur), about 1.5 ticks of travel
        double tail = Math.min(1.4, len * 1.5);
        double step = quadSize * 0.6;
        int n = (int) Math.min(14, tail / step);
        double dx = xd / len * step, dy = yd / len * step, dz = zd / len * step;
        float size = quadSize;
        for (int i = 1; i <= n; i++) {
            float u = i / (float) (n + 1);
            this.alpha = base * 0.55f * (1f - u);
            this.quadSize = size * (1f - 0.4f * u);
            if (this.alpha <= 0.004f) continue;
            extractRotatedQuad(state, q, (float) (x - dx * i - cam.x), (float) (y - dy * i - cam.y), (float) (z - dz * i - cam.z), partial);
        }
        this.alpha = base;
        this.quadSize = size;
    }

    @Override
    protected Layer getLayer() { return Layer.TRANSLUCENT; }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz, RandomSource r) {
            return new FlakeParticle(level, x, y, z, vx, vy, vz, sprites, r);
        }
    }
}
