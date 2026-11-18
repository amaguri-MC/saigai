package io.github.kestrelworks.calamities;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import io.github.kestrelworks.calamities.net.BlizzardFx;
import io.github.kestrelworks.calamities.net.QuakeFx;
import io.github.kestrelworks.calamities.net.ThunderVoice;
import io.github.kestrelworks.calamities.net.WindFx;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Server side of every disaster. */
public final class Disasters {

    private Disasters() {}

    public static final int THUNDER_VARIANTS = 4;
    /** Voice stages: 0 = heavily processed … 3 = plainly the real voice. A sound's variant is clip + stage * THUNDER_VARIANTS. */
    public static final int VOICE_STAGES = 4;
    private static final RandomSource RNG = RandomSource.create();

    // ---------------------------------------------------------------- state

    private record Strike(ServerLevel level, Vec3 pos, int variant, boolean fire, int atTick) {}

    private static final List<Strike> strikes = new ArrayList<>();
    private static int tick;
    private static int quakeLeft, quakeTotal;
    private static float quakeMag;
    private static int windLeft, windTotal;
    private static float windX, windZ, windPush;
    private static int stormCount, galeCount;

    // fissure of the running quake: planned at the start, torn open when the shaking peaks
    private static int fissureAt = -1;
    private static boolean fissureDone;
    private static Fissure fissure;

    // the ongoing thunderstorm (overworld)
    private static int stormLeft, stormRun;
    private static boolean weatherOurs, wasRaining;

    public static void reset() {
        stormCount = galeCount = 0;
        reveal = -1;
        spiritSeen = false;
        strikes.clear();
        quakeLeft = windLeft = 0;
        fissure = null;
        fissureAt = -1;
        stormLeft = 0;
        weatherOurs = false;
        blizzardLeft = blizzardTotal = 0;
        lastCrack.clear();
        coldTicks.clear();
        coldHurtAt.clear();
        quakeNext.clear();
        rocks.clear();
        windLast.clear();
        windAlong.clear();
        windHurtAt.clear();
        blizzardShown.clear();
        stormShown.clear();
    }

    public static boolean stormOn() { return stormLeft > 0; }

    /** Middle of the fissure tearing open right now, or null. */
    public static Vec3 fissureCenter() { return fissure == null ? null : fissure.center(); }

    /** Everyone who is up and about: used for safety distances (nothing may land on anybody). */
    static List<ServerPlayer> everyone(MinecraftServer server) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isAlive() && !p.isSpectator()) out.add(p);
        }
        return out;
    }

    /** The ones disasters are about: where they start, camera, freezing, wind, lightning hits, voices. */
    static List<ServerPlayer> targets(MinecraftServer server) {
        List<ServerPlayer> out = everyone(server);
        out.removeIf(p -> !Pv.isTarget(p));
        return out;
    }

    // ---------------------------------------------------------------- harshness (damage to the targets)

    /** Overall harshness, 0 (nothing hurts, everything as before) .. 4. */
    static float harsh() {
        float h = CalamityConfig.get().harshness;
        return Float.isNaN(h) ? 0f : Mth.clamp(h, 0f, 4f);
    }

    /**
     * The one way disasters hurt anybody. Only a target who is alive, not a spectator and not in creative is hurt
     * (a group-P player, a spectator or a creative player is never touched). Damage is of a vanilla kind, so the death
     * message reads like an ordinary death. With harshLethal off it stops at half a heart.
     */
    static boolean harm(ServerPlayer p, DamageSource src, float amount) {
        if (harsh() <= 0f || amount <= 0f) return false;
        if (!p.isAlive() || p.isSpectator() || p.isCreative() || !Pv.isTarget(p)) return false;
        if (!CalamityConfig.get().harshLethal) {
            amount = Math.min(amount, p.getHealth() - 1.0f);
            if (amount <= 0f) return false;
        }
        return p.hurtServer(p.level(), src, amount);
    }

    // ---------------------------------------------------------------- earthquake

    public static void quake(MinecraftServer server, float magnitude, String src) {
        float mag = Mth.clamp(magnitude * CalamityConfig.get().intensity, 0.05f, 1.0f);
        int ticks = Math.round(50 + mag * 130);
        if (quakeLeft > 0) {
            // an aftershock on top of a running quake: keep the stronger one going
            mag = Math.max(mag, quakeMag);
            ticks = Math.max(ticks, quakeLeft);
        } else {
            fissureDone = false;
        }
        // a strong one tears the ground open once the shaking is at its height (one fissure per quake)
        if (!fissureDone && fissureAt < 0 && fissure == null && CalamityConfig.get().quakeFissure
                && mag >= CalamityConfig.get().fissureMinMagnitude) {
            fissureAt = tick + Math.max(20, Math.round(ticks * 0.2f));
        }
        quakeMag = mag;
        quakeLeft = quakeTotal = ticks;
        Calamities.LOG.info("earthquake mag={} ticks={} ({})", String.format("%.2f", mag), ticks, src);
        QuakeFx fx = new QuakeFx(mag, ticks, server.overworld().getGameTime());
        for (ServerPlayer p : targets(server)) ServerPlayNetworking.send(p, fx);
    }

    /** Loose natural blocks that may come down in a quake. Built blocks (planks, cobblestone, bricks…) never fall. */
    static boolean loose(BlockState s) {
        return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.BASE_STONE_NETHER) || s.is(Blocks.GRAVEL)
                || s.is(Blocks.SAND) || s.is(Blocks.RED_SAND) || s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT)
                || s.is(BlockTags.LEAVES) || s.is(Blocks.CLAY) || s.is(Blocks.CALCITE) || s.is(Blocks.DRIPSTONE_BLOCK);
    }

    private static void fissureTick(MinecraftServer server) {
        if (fissure != null && !fissure.tick()) {
            Calamities.LOG.info("fissure done");
            fissure = null;
        }
        if (fissureAt < 0 || tick < fissureAt) return;
        fissureAt = -1;
        fissureDone = true;
        // near someone standing on (or close under) open ground in the overworld
        List<ServerPlayer> all = targets(server), cand = new ArrayList<>();
        for (ServerPlayer p : all) {
            if (p.level().dimension() != Level.OVERWORLD) continue;
            int top = p.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.blockPosition()).getY();
            if (top - p.getY() < 10) cand.add(p);
        }
        if (cand.isEmpty()) { Calamities.LOG.info("fissure: nobody on open ground"); return; }
        ServerPlayer p = cand.get(RNG.nextInt(cand.size()));
        fissure = Fissure.plan(p.level(), p, quakeMag, everyone(server));
        Calamities.LOG.info(fissure == null ? "fissure: no safe spot near {}" : "fissure opens near {} at {} ({} columns)",
                p.getScoreboardName(), fissure == null ? "" : fissure.center(), fissure == null ? 0 : fissure.size());
    }

    private static void quakeTick(MinecraftServer server) {
        fissureTick(server);
        if (quakeLeft <= 0) {
            if (!quakeNext.isEmpty()) quakeNext.clear();
            return;
        }
        quakeLeft--;
        float env = envelope(quakeTotal - quakeLeft, quakeTotal);
        // the first sign: dirt and grit jumping at everyone's feet
        if (tick % 3 == 0) {
            for (ServerPlayer p : targets(server)) {
                if (!p.onGround() || p.level().dimension() == Level.END) continue;
                BlockState under = p.level().getBlockState(p.blockPosition().below());
                if (under.isAir() || !under.getFluidState().isEmpty()) continue;
                int n = Math.round(2 + 10 * Math.max(0.25f, env) * quakeMag);
                p.level().sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, under),
                        p.getX(), p.getY() + 0.05, p.getZ(), n, 1.6, 0.02, 1.6, 0.12);
            }
        }
        quakeHarm(server, env);
        quakeRocks(server, env);
        if (!CalamityConfig.get().quakeLooseBlocks || quakeMag < 0.25f) return;
        // about mag^1.5 * 14 blocks per player over the whole quake, most of them at the peak
        float perTick = (float) (Math.pow(quakeMag, 1.5) * 14.0 / quakeTotal) * env * 1.6f;
        List<ServerPlayer> ps = targets(server);
        for (ServerPlayer p : ps) {
            ServerLevel level = p.level();
            if (level.dimension() == Level.END) continue;
            if (RNG.nextFloat() >= perTick) continue;
            for (int attempt = 0; attempt < 24; attempt++) {
                int dx = RNG.nextInt(21) - 10, dz = RNG.nextInt(21) - 10;
                if (dx * dx + dz * dz < 4) continue; // never right on top of someone
                BlockPos base = p.blockPosition().offset(dx, -1, dz);
                BlockPos hit = null;
                for (int dy = 0; dy < 12; dy++) {
                    BlockPos q = base.above(dy);
                    BlockState s = level.getBlockState(q);
                    if (!s.isAir() && loose(s) && level.getBlockState(q.below()).isAir()
                            && level.getBlockState(q.below(2)).isAir()) { hit = q; break; }
                }
                if (hit == null) continue;
                final BlockPos h = hit;
                boolean nearAnyone = everyone(server).stream().anyMatch(o -> o.level() == level
                        && Math.abs(o.getX() - (h.getX() + 0.5)) < 1.6 && Math.abs(o.getZ() - (h.getZ() + 0.5)) < 1.6);
                if (nearAnyone) continue;
                FallingBlockEntity.fall(level, hit, level.getBlockState(hit));
                break;
            }
        }
    }

    /** Tick at which a target standing on the ground is next shaken hard enough to be hurt. */
    private static final java.util.Map<UUID, Integer> quakeNext = new java.util.HashMap<>();

    /** While it shakes, a target standing on the ground is thrown about: a jolt every 1-2 s, stronger with the magnitude (damage "fall"). */
    private static void quakeHarm(MinecraftServer server, float env) {
        CalamityConfig c = CalamityConfig.get();
        float h = harsh();
        if (h <= 0f || c.quakeDamage <= 0f || quakeMag < c.quakeDamageMinMag || env < 0.3f) return;
        for (ServerPlayer p : targets(server)) {
            if (p.isCreative() || !p.onGround() || p.getAbilities().flying || p.level().dimension() == Level.END) continue;
            BlockState under = p.level().getBlockState(p.blockPosition().below());
            if (under.isAir() || !under.getFluidState().isEmpty()) continue;
            Integer next = quakeNext.get(p.getUUID());
            if (next == null) { quakeNext.put(p.getUUID(), tick + 10 + RNG.nextInt(20)); continue; }
            if (tick < next) continue;
            quakeNext.put(p.getUUID(), tick + Math.max(8, Math.round(c.quakeDamageInterval * (0.67f + 0.67f * RNG.nextFloat()) / Mth.clamp(h, 0.5f, 2f))));
            float dmg = c.quakeDamage * (0.5f + 0.5f * RNG.nextFloat()) * quakeMag * Math.max(0.5f, env) * h;
            harm(p, p.level().damageSources().fall(), dmg);
        }
    }

    private static final class Rock {
        final FallingBlockEntity e;
        boolean hit;
        Rock(FallingBlockEntity e) { this.e = e; }
    }

    private static final List<Rock> rocks = new ArrayList<>();

    /** Rocks that came loose from the ceiling or a cliff (only where there is a hollow below them), watched until they land. */
    private static void quakeRocks(MinecraftServer server, float env) {
        CalamityConfig c = CalamityConfig.get();
        float h = harsh();
        if (h <= 0f || c.quakeRockRate <= 0f || quakeMag < c.quakeDamageMinMag || env < 0.3f) return;
        float chance = c.quakeRockRate * quakeMag * env * h;
        for (ServerPlayer p : targets(server)) {
            if (rocks.size() >= c.quakeRockMax) return;
            ServerLevel level = p.level();
            if (p.isCreative() || level.dimension() == Level.END || RNG.nextFloat() >= chance) continue;
            int r = Math.max(2, c.quakeRockRadius);
            for (int attempt = 0; attempt < 14; attempt++) {
                BlockPos base = p.blockPosition().offset(RNG.nextInt(2 * r + 1) - r, 0, RNG.nextInt(2 * r + 1) - r);
                BlockPos hit = null;
                for (int dy = 2; dy <= 8; dy++) {
                    BlockPos q = base.above(dy);
                    BlockState s = level.getBlockState(q);
                    if (s.isAir() || !s.getFluidState().isEmpty() || s.is(BlockTags.LEAVES) || !loose(s)) continue;
                    if (Built.is(level, q)) continue;
                    // a ceiling or an overhang: air under it (a hollow)
                    if (level.getBlockState(q.below()).isAir() && level.getBlockState(q.below(2)).isAir()) { hit = q; break; }
                }
                if (hit == null) continue;
                final BlockPos hp = hit;
                boolean onOther = everyone(server).stream().anyMatch(o -> o.level() == level && !Pv.isTarget(o)
                        && Math.abs(o.getX() - (hp.getX() + 0.5)) < 1.6 && Math.abs(o.getZ() - (hp.getZ() + 0.5)) < 1.6);
                if (onOther) continue;
                BlockState rock = RNG.nextFloat() < 0.7f ? Blocks.GRAVEL.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
                FallingBlockEntity f = FallingBlockEntity.fall(level, hit, rock);
                f.dropItem = false;
                rocks.add(new Rock(f));
                level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, rock),
                        hit.getX() + 0.5, hit.getY() + 0.2, hit.getZ() + 0.5, 8, 0.4, 0.2, 0.4, 0.05);
                break;
            }
        }
    }

    /** A rock that passes through a target hurts them once (damage "falling block"). Others are never hurt. */
    private static void rocksTick(MinecraftServer server) {
        if (rocks.isEmpty()) return;
        float h = harsh();
        List<ServerPlayer> ts = h > 0f ? targets(server) : List.of();
        for (Iterator<Rock> it = rocks.iterator(); it.hasNext(); ) {
            Rock r = it.next();
            if (!r.e.isAlive()) { it.remove(); continue; }
            if (r.hit || h <= 0f) continue;
            AABB box = r.e.getBoundingBox().inflate(0.1);
            for (ServerPlayer p : ts) {
                if (p.level() != r.e.level() || !box.intersects(p.getBoundingBox())) continue;
                r.hit = true;
                harm(p, p.level().damageSources().fallingBlock(r.e), CalamityConfig.get().quakeRockDamage * h);
                break;
            }
        }
    }

    /** 0→1 over the first 15 %, 1 until 60 %, then back to 0. */
    public static float envelope(int t, int total) {
        if (total <= 0) return 0;
        float x = t / (float) total;
        if (x < 0.15f) return x / 0.15f;
        if (x < 0.6f) return 1f;
        return Math.max(0f, 1f - (x - 0.6f) / 0.4f);
    }

    // ---------------------------------------------------------------- lightning storm

    public static void thunder(MinecraftServer server, String src) {
        List<ServerPlayer> ps = targets(server);
        int stage = voiceStage(stormCount++);
        Calamities.LOG.info("lightning storm for {} players, voice stage {} ({})", ps.size(), stage, src);
        // a press starts (or lengthens) a real thunderstorm: dark sky, rain, bolts all around for a while
        if (CalamityConfig.get().stormRain) {
            ServerLevel ow = server.overworld();
            int add = Math.max(1, CalamityConfig.get().stormSeconds) * 20;
            if (stormLeft <= 0) {
                // the weather flags, not the sky levels (those fade in over ~4 s and would read "clear" at first)
                weatherOurs = !Pv.thundering(ow);
                wasRaining = Pv.raining(ow);
                stormRun = 0;
            }
            stormLeft = Math.min(stormLeft + add, add * 3);
            Pv.setWeather(ow, 0, stormLeft + 20 * 20, true, true);
            shareStorm(server);
        }
        for (ServerPlayer p : ps) {
            int n = 2 + RNG.nextInt(2);
            for (int i = 0; i < n; i++) {
                double dist = i == 0 ? 4.5 + RNG.nextDouble() * 3.0 : 7 + RNG.nextDouble() * 9;
                double ang = RNG.nextDouble() * Math.PI * 2;
                Vec3 at = ground(p.level(), p, p.getX() + Math.cos(ang) * dist, p.getZ() + Math.sin(ang) * dist);
                int when = tick + (i == 0 ? 0 : 6 + RNG.nextInt(26));
                strikes.add(new Strike(p.level(), at, RNG.nextInt(THUNDER_VARIANTS) + stage * THUNDER_VARIANTS, i == 0 && RNG.nextFloat() < 0.5f, when));
            }
        }
    }

    /** The voice comes closer to the real one the more often it has been heard. */
    public static int voiceStage(int count) {
        return Math.min(VOICE_STAGES - 1, count / Math.max(1, CalamityConfig.get().voicePressesPerStep));
    }

    /** Sets both voices to a stage outright (0 = back to the start). */
    public static void voice(int stage) {
        stormCount = galeCount = Mth.clamp(stage, 0, VOICE_STAGES - 1) * Math.max(1, CalamityConfig.get().voicePressesPerStep);
        Calamities.LOG.info("voices -> stage {}", stage);
    }

    /** Sound id of a storm or gale ("storm.thunder." / "gale.") from its variant. */
    public static String voiceSound(String base, int variant) {
        int stage = variant / THUNDER_VARIANTS, clip = variant % THUNDER_VARIANTS + 1;
        return stage == 0 ? base + clip : base + "v" + stage + "." + clip;
    }

    /** Where a bolt lands near (x, z): the open sky surface, or — under a roof or in the Nether — the floor near the player. */
    static Vec3 ground(ServerLevel level, ServerPlayer p, double x, double z) {
        BlockPos col = BlockPos.containing(x, p.getY(), z);
        boolean ceiling = level.dimensionType().hasCeiling();
        if (!ceiling) {
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, col);
            if (p.level().canSeeSky(p.blockPosition()) || top.getY() - p.getY() < 12) return Vec3.atBottomCenterOf(top);
        }
        for (int dy = 4; dy > -10; dy--) {
            BlockPos q = col.atY(p.getBlockY() + dy);
            if (level.getBlockState(q).isAir() && !level.getBlockState(q.below()).isAir()) return Vec3.atBottomCenterOf(q);
        }
        return new Vec3(x, p.getY(), z);
    }

    private static void strikeTick() {
        for (Iterator<Strike> it = strikes.iterator(); it.hasNext(); ) {
            Strike s = it.next();
            if (s.atTick > tick) continue;
            it.remove();
            strike(s.level, s.pos, s.variant, s.fire);
        }
    }

    public static void strike(ServerLevel level, Vec3 pos, int variant, boolean fire) {
        strike(level, pos, variant, fire, false);
    }

    /** harm: whatever stands right where it lands out in the open is struck (ordinary lightning damage, "struck by lightning"). */
    public static void strike(ServerLevel level, Vec3 pos, int variant, boolean fire, boolean harm) {
        StormBolt b = Calamities.STORM_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (b == null) return;
        b.snapTo(pos);
        b.setVisualOnly(true);
        b.setVariant(-1);   // seen and heard by anyone near as an ordinary bolt; the voice goes to the targets only
        level.addFreshEntity(b);
        if (variant >= 0) {
            ThunderVoice tv = new ThunderVoice(pos.x, pos.y, pos.z, variant);
            // everyone of the target group in this level, spectators and the dead included (the camera crew hears it too)
            for (ServerPlayer t : level.players()) {
                if (Pv.isTarget(t) && t.distanceToSqr(pos) < 256.0 * 256.0) ServerPlayNetworking.send(t, tv);
            }
        }
        if (harm) {
            AABB box = new AABB(pos.x - 1.4, pos.y - 1.5, pos.z - 1.4, pos.x + 1.4, pos.y + 3.5, pos.z + 1.4);
            for (Entity e : level.getEntities((Entity) null, box, e -> e instanceof LivingEntity && e.isAlive() && !(e instanceof Wraith))) {
                if (!level.canSeeSky(e.blockPosition().above())) continue;
                if (e instanceof Player pl && !Pv.isTarget(pl)) continue;   // seen by anyone near, struck only the target
                if (e instanceof ServerPlayer sp && harsh() > 0f) { lightningHit(sp); continue; }
                e.thunderHit(level, b);
            }
        }
        if (fire && CalamityConfig.get().stormFire && level.dimension() == Level.OVERWORLD) {
            BlockPos f = BlockPos.containing(pos);
            if (level.getBlockState(f).isAir()) {
                BlockState st = BaseFireBlock.getState(level, f);
                if (st.canSurvive(level, f)) level.setBlockAndUpdate(f, st);
            }
        }
    }

    /** A direct hit on a target: vanilla lightning damage ("struck by lightning") and they burn. */
    private static void lightningHit(ServerPlayer p) {
        CalamityConfig c = CalamityConfig.get();
        float h = harsh();
        if (harm(p, p.level().damageSources().lightningBolt(), c.stormHitDamage * (0.5f + 0.5f * h))) {
            p.setRemainingFireTicks(Math.max(p.getRemainingFireTicks(), c.stormHitFireSeconds * 20));
        }
    }

    /** An extra bolt a few blocks from a target who is out in the open (never within 2 blocks of anyone). */
    private static void nearBolt(ServerLevel ow, ServerPlayer p, List<ServerPlayer> all, CalamityConfig c) {
        for (int i = 0; i < 6; i++) {
            double dist = c.stormNearMin + RNG.nextDouble() * Math.max(0.1f, c.stormNearMax - c.stormNearMin), ang = RNG.nextDouble() * Math.PI * 2;
            Vec3 g = ground(ow, p, p.getX() + Math.cos(ang) * dist, p.getZ() + Math.sin(ang) * dist);
            boolean near = false;
            for (ServerPlayer o : all) if (o.level() == ow && o.position().multiply(1, 0, 1).distanceTo(g.multiply(1, 0, 1)) < 2.0) near = true;
            if (near) continue;
            strike(ow, g, -1, RNG.nextFloat() < c.stormNearFireChance, true);
            return;
        }
    }

    /** The ongoing thunderstorm: bolts all around everyone out in the overworld, now and then one right on them. */
    private static void stormTick(MinecraftServer server) {
        if (stormLeft <= 0) return;
        stormLeft--;
        stormRun++;
        ServerLevel ow = server.overworld();
        // sleeping it off (like the original thunderstorms), or the weather was cleared by hand
        // like vanilla: only when enough of everyone sleeps (the playersSleepingPercentage rule) — the night is
        // skipped and vanilla clears the weather, which the flag below picks up as well
        boolean slept = false;
        int sleeping = 0, awake = 0;
        for (ServerPlayer p : ow.players()) {
            if (p.isSpectator() || !Pv.isTarget(p)) continue;
            if (p.isSleepingLongEnough()) sleeping++; else awake++;
        }
        if (sleeping > 0) {
            int pct = ow.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.PLAYERS_SLEEPING_PERCENTAGE);
            slept = sleeping >= Math.max(1, (int) Math.ceil((sleeping + awake) * pct / 100.0));
        }
        boolean cleared = !Pv.thundering(ow);   // /weather clear flips the flag at once
        if (slept || (stormRun > 2 && cleared)) {
            endStorm(server, slept ? "slept" : "weather cleared", !slept && cleared);
            return;
        }
        if (stormLeft == 0) { endStorm(server, "over", false); return; }
        if (tick % 20 == 0) shareStorm(server);
        CalamityConfig c = CalamityConfig.get();
        List<ServerPlayer> ps = targets(server);
        List<ServerPlayer> all = everyone(server);
        float h = harsh();
        for (ServerPlayer p : ps) {
            if (p.level() != ow) continue;
            // harshness: extra bolts a few blocks from a target who is out in the open
            if (h > 0f && c.stormNearEvery > 0 && !p.isCreative() && RNG.nextFloat() < h / c.stormNearEvery
                    && ow.canSeeSky(p.blockPosition().above())) nearBolt(ow, p, all, c);
            if (RNG.nextInt(Math.max(1, c.stormBoltEvery)) != 0) continue;
            boolean open = ow.canSeeSky(p.blockPosition().above());
            Vec3 at = null;
            if (open && RNG.nextFloat() < Math.min(0.9f, c.stormHitChance * (1f + h * c.stormHitBoost))) {
                at = Vec3.atBottomCenterOf(ow.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, p.blockPosition()));
                if (at.y > p.getY() + 3) at = null;
                else at = new Vec3(p.getX(), at.y, p.getZ());
            }
            if (at == null) {
                // somewhere around them, never within 4.5 blocks of anyone
                for (int i = 0; i < 8 && at == null; i++) {
                    double dist = 6 + RNG.nextDouble() * 26, ang = RNG.nextDouble() * Math.PI * 2;
                    Vec3 g = ground(ow, p, p.getX() + Math.cos(ang) * dist, p.getZ() + Math.sin(ang) * dist);
                    boolean near = false;
                    for (ServerPlayer o : all) if (o.level() == ow && o.position().multiply(1, 0, 1).distanceTo(g.multiply(1, 0, 1)) < 4.5) near = true;
                    if (!near) at = g;
                }
            }
            if (at == null) continue;
            strike(ow, at, -1, RNG.nextFloat() < c.stormFireChance, true);
        }
    }

    /** Players (not targets) shown the storm sky because they stand near a target; kept up to date while the storm lasts. */
    private static final java.util.Set<UUID> stormShown = new java.util.HashSet<>();

    private static void shareStorm(MinecraftServer server) {
        java.util.Set<UUID> want = new java.util.HashSet<>();
        List<ServerPlayer> near = new java.util.ArrayList<>(stormLeft > 0 ? Pv.nearby(server, targets(server)) : List.of());
        // a director who gets the disasters sees the target group's storm sky too (their own sky follows the director view)
        if (stormLeft > 0 && Pv.active()) for (ServerPlayer d : targets(server)) if (Pv.director(d)) near.add(d);
        for (ServerPlayer o : near) {
            want.add(o.getUUID());
            if (stormShown.add(o.getUUID())) Pv.overlay(o, true);
        }
        for (java.util.Iterator<UUID> it = stormShown.iterator(); it.hasNext(); ) {
            UUID id = it.next();
            if (want.contains(id)) continue;
            it.remove();
            ServerPlayer o = server.getPlayerList().getPlayer(id);
            if (o != null) Pv.overlay(o, false);
        }
    }

    private static void endStorm(MinecraftServer server, String why, boolean alreadyClear) {
        stormLeft = 0;
        ServerLevel ow = server.overworld();
        if (!alreadyClear) {
            if (weatherOurs && !wasRaining) Pv.setWeather(ow, 20 * 60 * 10, 0, false, false);
            else Pv.setWeather(ow, 0, 20 * 60 * 3, true, false);
        }
        weatherOurs = false;
        shareStorm(server);   // stormLeft is 0 now: everyone shown the storm sky goes back to their own
        Calamities.LOG.info("thunderstorm ends ({})", why);
    }

    // ---------------------------------------------------------------- windstorm

    public static void wind(MinecraftServer server, String src) {
        double ang = RNG.nextDouble() * Math.PI * 2;
        windX = (float) Math.cos(ang);
        windZ = (float) Math.sin(ang);
        windTotal = windLeft = Math.round(170 * Math.max(0.6f, CalamityConfig.get().intensity));
        windPush = CalamityConfig.get().galePush * CalamityConfig.get().intensity * (1f + harsh() * CalamityConfig.get().windPushBoost);
        int stage = voiceStage(galeCount++);
        Calamities.LOG.info("windstorm dir=({}, {}) ticks={} voice stage {} ({})", String.format("%.2f", windX), String.format("%.2f", windZ), windTotal, stage, src);
        WindFx fx = new WindFx(windX, windZ, windPush, CalamityConfig.get().galeEndFactor, windTotal,
                RNG.nextInt(THUNDER_VARIANTS) + stage * THUNDER_VARIANTS, server.overworld().getGameTime());
        for (ServerPlayer p : targets(server)) ServerPlayNetworking.send(p, fx);
    }

    private static final java.util.Map<UUID, Vec3> windLast = new java.util.HashMap<>();
    private static final java.util.Map<UUID, Double> windAlong = new java.util.HashMap<>();
    private static final java.util.Map<UUID, Integer> windHurtAt = new java.util.HashMap<>();

    /**
     * Harshness in a gale, for a target out in the open: pushed along the wind and stopped short by a wall
     * ("experienced kinetic energy"), and now and then something flying hits them (same kind of damage).
     * Their speed is read from where the server sees them from tick to tick.
     */
    private static void windHarm(ServerPlayer p, float env) {
        CalamityConfig c = CalamityConfig.get();
        float h = harsh();
        if (h <= 0f) return;
        UUID id = p.getUUID();
        Vec3 now = p.position(), last = windLast.put(id, now);
        double prevAlong = windAlong.getOrDefault(id, 0.0);
        double along = last == null ? 0 : (now.x - last.x) * windX + (now.z - last.z) * windZ;
        windAlong.put(id, along);
        if (h <= 0f || last == null || env < 0.3f) return;
        ServerLevel level = p.level();
        if (p.isCreative() || p.isSpectator() || p.getAbilities().flying || p.isFallFlying() || p.isPassenger() || p.isInWater()) return;
        if (!level.canSeeSky(p.blockPosition().above())) return;
        if (tick < windHurtAt.getOrDefault(id, 0)) return;
        // pushed hard, then stopped dead with a solid block right ahead
        if (prevAlong >= c.windSlamSpeed && along < prevAlong * 0.35) {
            BlockPos a = BlockPos.containing(now.x + windX * 0.7, now.y + 0.5, now.z + windZ * 0.7);
            boolean wall = false;
            for (BlockPos q : new BlockPos[]{a, a.above()}) {
                if (!level.getBlockState(q).getCollisionShape(level, q).isEmpty()) wall = true;
            }
            if (wall) {
                float dmg = (float) Math.min(c.windSlamMax, 1.0 + (prevAlong - c.windSlamSpeed) * c.windSlamScale) * h;
                windHurtAt.put(id, tick + 20);
                harm(p, level.damageSources().flyIntoWall(), dmg);
                return;
            }
        }
        // something blown along hits them
        if (c.windDebrisEvery > 0 && RNG.nextFloat() < h / c.windDebrisEvery) {
            windHurtAt.put(id, tick + 20);
            harm(p, level.damageSources().flyIntoWall(), c.windDebrisDamage * h);
        }
    }

    private static void windTick(MinecraftServer server) {
        if (windLeft <= 0) {
            if (!windLast.isEmpty()) { windLast.clear(); windAlong.clear(); windHurtAt.clear(); }
            return;
        }
        windLeft--;
        float env = windEnvelope(windTotal - windLeft, windTotal);
        // players push themselves on their own side (smooth); here: dropped items and mobs near players
        for (ServerPlayer p : targets(server)) {
            ServerLevel level = p.level();
            float f = level.dimension() == Level.END ? CalamityConfig.get().galeEndFactor : 1f;
            AABB box = p.getBoundingBox().inflate(32, 12, 32);
            for (Entity e : level.getEntities((Entity) null, box, e -> (e instanceof ItemEntity
                    || (e instanceof LivingEntity && !(e instanceof Player) && !(e instanceof Wraith))) && !e.isPassenger())) {
                if (!level.canSeeSky(e.blockPosition().above())) continue;
                double k = windPush * env * f * (e instanceof ItemEntity ? 0.9 : 0.45);
                e.push(windX * k, 0, windZ * k);
                e.hurtMarked = true;
            }
            windHarm(p, env);
        }
    }

    public static float windEnvelope(int t, int total) {
        if (total <= 0) return 0;
        float up = Math.min(30, total * 0.2f), down = Math.min(45, total * 0.3f);
        if (t < up) return t / up;
        if (t > total - down) return Math.max(0, (total - t) / down);
        return 1f;
    }

    // ---------------------------------------------------------------- blizzard

    // the ongoing blizzard (overworld): runs from blizzardStart for blizzardTotal ticks of world time
    private static int blizzardLeft, blizzardTotal;
    private static long blizzardStart;
    private static float blizzardX, blizzardZ;
    private static final java.util.Map<UUID, Integer> lastCrack = new java.util.HashMap<>();

    public static boolean blizzardOn() { return blizzardLeft > 0; }
    public static int blizzardLeft() { return blizzardLeft; }

    /** A press starts a blizzard, or makes the running one last longer (up to 3 presses' worth). */
    public static void blizzard(MinecraftServer server, String src) {
        ServerLevel ow = server.overworld();
        int add = Math.max(1, CalamityConfig.get().blizzardSeconds) * 20;
        if (blizzardLeft <= 0) {
            double ang = RNG.nextDouble() * Math.PI * 2;
            blizzardX = (float) Math.cos(ang);
            blizzardZ = (float) Math.sin(ang);
            blizzardStart = ow.getGameTime();
            blizzardTotal = 0;
            blizzardLeft = 0;
        }
        int left = Math.min(blizzardLeft + add, add * 3);
        blizzardTotal += left - blizzardLeft;
        blizzardLeft = left;
        Calamities.LOG.info("blizzard dir=({}, {}) left={} total={} ({})", String.format("%.2f", blizzardX),
                String.format("%.2f", blizzardZ), blizzardLeft, blizzardTotal, src);
        sendBlizzard(server);
    }

    private static void sendBlizzard(MinecraftServer server) { sendBlizzard(server, true); }

    /** everyone = a press changed it (tell all who should have it); false = only those who have not been told yet. */
    private static void sendBlizzard(MinecraftServer server, boolean everyone) {
        BlizzardFx fx = blizzardLeft > 0 ? new BlizzardFx(blizzardX, blizzardZ, blizzardTotal, blizzardStart) : new BlizzardFx(0, 0, 0, 0);
        // the targets, and anyone who stands near one (they can see and hear it, nothing else of it reaches them)
        List<ServerPlayer> ts = targets(server);
        java.util.Set<UUID> want = new java.util.HashSet<>();
        for (ServerPlayer p : ts) want.add(p.getUUID());
        for (ServerPlayer p : Pv.nearby(server, ts)) want.add(p.getUUID());
        if (!Pv.active()) for (ServerPlayer p : server.getPlayerList().getPlayers()) want.add(p.getUUID());   // no view sync: all, as always
        for (UUID id : want) {
            if (!everyone && blizzardShown.contains(id)) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) ServerPlayNetworking.send(p, fx);
        }
        // someone who left the circle (or the group) is told it stopped
        for (java.util.Iterator<UUID> it = blizzardShown.iterator(); it.hasNext(); ) {
            UUID id = it.next();
            if (want.contains(id)) continue;
            it.remove();
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) ServerPlayNetworking.send(p, new BlizzardFx(0, 0, 0, 0));
        }
        blizzardShown.addAll(want);
    }

    /** Who has been sent the running blizzard. */
    private static final java.util.Set<UUID> blizzardShown = new java.util.HashSet<>();

    /** A player who left: forget what they were shown, so coming back shows it again. */
    public static void left(UUID id) {
        blizzardShown.remove(id);
        stormShown.remove(id);
    }

    /** A player who joins while it blows sees it too (if they are a target; others are picked up by the once-a-second check). */
    public static void joined(ServerPlayer p) {
        if (blizzardLeft > 0 && Pv.isTarget(p) && blizzardShown.add(p.getUUID()))
            ServerPlayNetworking.send(p, new BlizzardFx(blizzardX, blizzardZ, blizzardTotal, blizzardStart));
    }

    /** 0→1 over the first 6 s, 1, then down to 0 over the last 6 s. */
    public static float blizzardEnvelope(int t, int total) {
        if (total <= 0 || t < 0 || t >= total) return 0;
        float ramp = Math.min(120, total * 0.3f);
        return Mth.clamp(Math.min(t / ramp, (total - t) / ramp), 0f, 1f);
    }

    private static void blizzardTick(MinecraftServer server) {
        if (blizzardLeft <= 0) return;
        blizzardLeft--;
        ServerLevel ow = server.overworld();
        int t = blizzardTotal - blizzardLeft;
        float env = blizzardEnvelope(t, blizzardTotal);
        if (blizzardLeft == 0) {
            Calamities.LOG.info("blizzard ends");
            blizzardTotal = 0;
            blizzardShown.clear();   // clients stop by their own clock
            lastCrack.clear();
            coldTicks.clear();
            coldHurtAt.clear();
            return;
        }
        if (tick % 20 == 0) sendBlizzard(server, false);
        CalamityConfig c = CalamityConfig.get();
        List<ServerPlayer> ps = targets(server);
        for (ServerPlayer p : ps) {
            if (p.level() != ow) continue;
            // out in it you slowly go stiff with cold (the vanilla frost: frosted screen edge, heavy legs)
            float h = harsh();
            if (env > 0.2f && p.canFreeze() && !p.isCreative() && ow.canSeeSky(p.blockPosition().above())) {
                // harshness: stay out in the cold long enough and the frost starts to hurt (vanilla freezing damage, "froze to death")
                int cold = h > 0f ? cold(p.getUUID(), 1) : 0;
                boolean hurting = h > 0f && c.blizzardDamage > 0f
                        && cold >= Math.round(c.blizzardColdSeconds * 20 / Mth.clamp(h, 0.5f, 2f));
                if (hurting && tick >= coldHurtAt.getOrDefault(p.getUUID(), 0)) {
                    coldHurtAt.put(p.getUUID(), tick + Math.max(10, c.blizzardDamageEvery));
                    harm(p, ow.damageSources().freeze(), c.blizzardDamage * h);
                }
                int cap = c.blizzardHarm ? Integer.MAX_VALUE : Math.round(p.getTicksRequiredToFreeze() * (hurting ? 0.95f : Mth.clamp(c.blizzardFreeze, 0f, 0.95f)));
                // vanilla thaws 2 a tick outside powder snow: +2..3 here = slowly colder
                int add = 2 + (RNG.nextFloat() < 0.45f * env ? 1 : 0);
                int now = p.getTicksFrozen();
                if (now < cap) p.setTicksFrozen(Math.min(cap, now + add));
            } else if (h > 0f) {
                cold(p.getUUID(), -2);   // under a roof (or armoured against it) the cold lets go
            }
            if (env < 0.15f) continue;
            BlockPos ice = null;
            int tries = Math.round(6 * env * (1f + 0.5f * h));
            for (int i = 0; i < tries; i++) {
                int dx = RNG.nextInt(29) - 14, dz = RNG.nextInt(29) - 14;
                if (dx * dx + dz * dz > 14 * 14) continue;
                BlockPos col = p.blockPosition().offset(dx, 0, dz);
                BlockPos top = ow.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, col);
                if (Math.abs(top.getY() - p.getY()) > 24) continue;
                BlockPos below = top.below();
                BlockState under = ow.getBlockState(below);
                // still water out under the sky freezes over (never right next to someone: no one gets locked in)
                if (c.blizzardIce && under.is(Blocks.WATER) && ow.getFluidState(below).isSource() && ow.getBlockState(top).isAir()) {
                    boolean near = false;
                    for (ServerPlayer o : everyone(server)) if (o.level() == ow && o.position().distanceTo(Vec3.atCenterOf(below)) < 2.5) near = true;
                    if (near) continue;
                    ow.setBlockAndUpdate(below, Blocks.ICE.defaultBlockState());
                    ice = below;
                    continue;
                }
                if (!c.blizzardSnow) continue;
                BlockState at = ow.getBlockState(top);
                if (at.is(Blocks.SNOW)) {
                    int layers = at.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS);
                    if (layers < Math.min(8, 2 + Math.round(h * c.blizzardThick)) && RNG.nextFloat() < 0.08f * (1f + h)) ow.setBlockAndUpdate(top, at.setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, layers + 1));
                } else if (at.isAir() && RNG.nextFloat() < 0.6f) {
                    BlockState snow = Blocks.SNOW.defaultBlockState();
                    if (snow.canSurvive(ow, top) && !under.is(Blocks.ICE)) ow.setBlockAndUpdate(top, snow);
                }
            }
            // a sharp crack where the water froze (at most every 1.5 s per player)
            if (ice != null) {
                int last = lastCrack.getOrDefault(p.getUUID(), -1000);
                if (tick - last >= 30) {
                    lastCrack.put(p.getUUID(), tick);
                    ow.playSound(null, ice.getX() + 0.5, ice.getY() + 1, ice.getZ() + 0.5, sound("blizzard.crack"),
                            SoundSource.WEATHER, 0.9f, 0.9f + RNG.nextFloat() * 0.25f);
                }
            }
        }
    }

    /** Ticks each target has spent out in the blizzard (it drains when they are sheltered). */
    private static final java.util.Map<UUID, Integer> coldTicks = new java.util.HashMap<>();
    private static final java.util.Map<UUID, Integer> coldHurtAt = new java.util.HashMap<>();

    private static int cold(UUID id, int by) {
        int v = coldTicks.getOrDefault(id, 0) + by;
        if (v <= 0) { coldTicks.remove(id); return 0; }
        coldTicks.put(id, v);
        return v;
    }

    private static void endBlizzard(MinecraftServer server) {
        if (blizzardLeft <= 0 && blizzardTotal <= 0) return;
        blizzardLeft = 0;
        blizzardTotal = 0;
        lastCrack.clear();
        coldTicks.clear();
        coldHurtAt.clear();
        blizzardShown.clear();   // calm() sends the stop to everyone who had it, first
        Calamities.LOG.info("blizzard stopped");
    }

    // ---------------------------------------------------------------- restless spirit

    public static void ghost(MinecraftServer server, String id, String name, String tex, String sig) {
        List<ServerPlayer> ps = targets(server);
        if (ps.isEmpty()) { Calamities.LOG.info("spirit: nobody to haunt"); return; }
        ServerPlayer p = ps.get(RNG.nextInt(ps.size()));
        UUID uuid;
        try { uuid = id == null || id.isEmpty() ? UUID.randomUUID() : UUID.fromString(id); } catch (Exception e) { uuid = UUID.randomUUID(); }
        String n = name == null || name.isEmpty() ? "Spirit" : name;
        ImmutableMultimap.Builder<String, Property> props = ImmutableMultimap.builder();
        if (tex != null && !tex.isEmpty()) props.put("textures", new Property("textures", tex, sig == null || sig.isEmpty() ? null : sig));
        GameProfile profile = new GameProfile(uuid, n, new PropertyMap(props.build()));
        boolean hasSkin = (tex != null && !tex.isEmpty()) || (name != null && !name.isEmpty());

        Wraith w = Calamities.WRAITH.create(p.level(), EntitySpawnReason.TRIGGERED);
        if (w == null) return;
        Vec3 at = spiritSpot(p);
        w.snapTo(at.x, at.y, at.z, p.getYRot() + 180f, 0f);
        ResolvableProfile rp = tex != null && !tex.isEmpty() ? ResolvableProfile.createResolved(profile)
                : name != null && !name.isEmpty() ? ResolvableProfile.createUnresolved(name) : null;
        w.setup(rp, !hasSkin, revealTarget(), CalamityConfig.get().spiritSeconds * 20, p.getUUID());
        p.level().addFreshEntity(w);
        spiritSeen = true;
        p.level().playSound(null, at.x, at.y + 1, at.z, sound("spirit.appear"), SoundSource.HOSTILE, 1.6f, 1f);
        Calamities.LOG.info("spirit '{}' rises near {} at {}", n, p.getScoreboardName(), at);
    }

    /** How much a spirit shows of the face it was given (0 = pale and faceless, 1 = fully). -1 = not set yet (from config). */
    private static float reveal = -1;

    /** A spirit just left: when none are left, the next one starts as a pale figure again. */
    private static boolean spiritSeen;

    public static boolean spiritGone(MinecraftServer server) {
        long limit = CalamityConfig.get().spiritSeconds * 20L + 200;
        for (ServerLevel l : server.getAllLevels()) {
            // 読み込みが止まって動けない亡霊（遠くに置き去り）は、寿命を過ぎたら片付ける
            for (Wraith w : l.getEntities(Calamities.WRAITH, e -> e.isAlive())) {
                if (w.spawned() >= 0 && l.getGameTime() - w.spawned() > limit) w.discard();
            }
            if (!l.getEntities(Calamities.WRAITH, e -> e.isAlive()).isEmpty()) return false;
        }
        reveal = -1;
        return true;
    }

    public static float revealTarget() {
        if (reveal < 0) reveal = "pale".equals(CalamityConfig.get().spiritLook) ? 0f : 1f;
        return reveal;
    }

    /** Spirits slowly take on their face: step > 0 adds steps of 1/revealSteps, to >= 0 sets the level outright. */
    public static void reveal(float step, float to) {
        float r = revealTarget();
        r = to >= 0 ? to : r + step / Math.max(1, CalamityConfig.get().revealSteps);
        reveal = Mth.clamp(r, 0f, 1f);
        Calamities.LOG.info("spirits reveal -> {}", String.format("%.2f", reveal));
    }

    /** 7–10 blocks from the player, somewhere they are facing, with two blocks of air. */
    static Vec3 spiritSpot(ServerPlayer p) {
        ServerLevel level = p.level();
        for (int i = 0; i < 20; i++) {
            double yaw = Math.toRadians(p.getYRot() + (RNG.nextDouble() - 0.5) * 140);
            double d = 7 + RNG.nextDouble() * 3;
            double x = p.getX() - Math.sin(yaw) * d, z = p.getZ() + Math.cos(yaw) * d;
            for (int dy = 2; dy >= -3; dy--) {
                BlockPos q = BlockPos.containing(x, p.getY() + dy, z);
                if (level.getBlockState(q).isAir() && level.getBlockState(q.above()).isAir()
                        && !level.getBlockState(q.below()).isAir()) return new Vec3(x, q.getY() + 0.2, z);
            }
        }
        return p.position().add(p.getLookAngle().multiply(6, 0, 6)).add(0, 0.2, 0);
    }

    public static Holder<SoundEvent> sound(String path) {
        return Holder.direct(SoundEvent.createVariableRangeEvent(Calamities.id(path)));
    }

    // ---------------------------------------------------------------- misc

    public static void calm(MinecraftServer server) {
        strikes.clear();
        quakeLeft = windLeft = 0;
        fissureAt = -1;   // a fissure already tearing open finishes (half a crack would look broken)
        if (stormLeft > 0) endStorm(server, "calm", false);
        java.util.Set<UUID> hadBlizzard = new java.util.HashSet<>(blizzardShown);
        endBlizzard(server);   // snow and ice stay where they fell
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            boolean target = Pv.isTarget(p);
            if (target) {
                ServerPlayNetworking.send(p, new QuakeFx(0, 0, 0));
                ServerPlayNetworking.send(p, new WindFx(0, 0, 0, 0, 0, 0, 0));
            }
            // the stop goes only to those who were sent the start (a target, or someone who stood near one)
            if (target || hadBlizzard.contains(p.getUUID())) ServerPlayNetworking.send(p, new BlizzardFx(0, 0, 0, 0));
        }
        for (ServerLevel l : server.getAllLevels()) {
            for (Entity e : l.getEntities(Calamities.WRAITH, e -> true)) e.discard();
        }
        reveal = -1;
        Calamities.LOG.info("all calm");
    }

    public static void tick(MinecraftServer server) {
        // /tick freeze: the world stands still, so do the disasters (a storm or quake must not run out while frozen)
        if (!server.tickRateManager().runsNormally()) return;
        tick++;
        // 亡霊が全部いなくなったら（時間切れ・別の次元へ行った・遠く・退出）次は白い姿から
        if (tick % 20 == 0 && spiritSeen) {
            if (spiritGone(server)) spiritSeen = false;
        }
        strikeTick();
        stormTick(server);
        quakeTick(server);
        windTick(server);
        blizzardTick(server);
        rocksTick(server);
        spareTheLast(server);
    }

    /** harshLethal off: a target who is burning at their last hearts is put out (damage itself already stops at half a heart). */
    private static void spareTheLast(MinecraftServer server) {
        if (CalamityConfig.get().harshLethal || harsh() <= 0f || tick % 5 != 0) return;
        for (ServerPlayer p : targets(server)) {
            if (p.isOnFire() && p.getHealth() <= 2.5f) p.clearFire();
        }
    }
}
