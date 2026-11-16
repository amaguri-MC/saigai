package io.github.kestrelworks.calamities;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A restless spirit. Drifts through walls, keeps a few blocks away from the one it haunts and stares at them,
 * then fades. It cannot be hurt and never hurts anyone.
 */
public class Wraith extends Mob {

    static final EntityDataAccessor<ResolvableProfile> PROFILE = SynchedEntityData.defineId(Wraith.class, EntityDataSerializers.RESOLVABLE_PROFILE);
    static final EntityDataAccessor<Boolean> PALE = SynchedEntityData.defineId(Wraith.class, EntityDataSerializers.BOOLEAN);
    /** level game time at which it appeared (fades are timed by the world clock, so a replay jump keeps them right) */
    static final EntityDataAccessor<Integer> SPAWNED = SynchedEntityData.defineId(Wraith.class, EntityDataSerializers.INT);
    /** level game time at which it starts to fade away (-1 = not yet) */
    static final EntityDataAccessor<Integer> FADE_AT = SynchedEntityData.defineId(Wraith.class, EntityDataSerializers.INT);

    /** 0 = a faceless pale figure … 1 = wears the face it was given. Eased on the server toward Disasters.revealTarget(). */
    static final EntityDataAccessor<Float> REVEAL = SynchedEntityData.defineId(Wraith.class, EntityDataSerializers.FLOAT);

    public static final int FADE_IN = 40, FADE_OUT = 30;
    /** reveal change per tick (one quarter step takes 3 s) */
    static final float REVEAL_SPEED = 0.25f / 60f;

    private int lifeTicks = 1200;
    private UUID haunting;
    private Vec3 side = null;

    public Wraith(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(PROFILE, ResolvableProfile.Static.EMPTY);
        b.define(PALE, false);
        b.define(SPAWNED, -1);
        b.define(FADE_AT, -1);
        b.define(REVEAL, 0f);
    }

    public void setup(ResolvableProfile profile, boolean pale, float reveal, int lifeTicks, UUID haunting) {
        if (profile != null) entityData.set(PROFILE, profile);
        entityData.set(PALE, pale);
        entityData.set(REVEAL, reveal);
        entityData.set(SPAWNED, (int) level().getGameTime());
        this.lifeTicks = lifeTicks;
        this.haunting = haunting;
    }

    public ResolvableProfile profile() { return entityData.get(PROFILE); }
    public boolean pale() { return entityData.get(PALE); }
    /** level game time it appeared (-1 = unknown) */
    public int spawned() { return entityData.get(SPAWNED); }
    public int fadeAt() { return entityData.get(FADE_AT); }
    public float reveal() { return entityData.get(REVEAL); }

    /** 0..1 how visible it is at this moment (from the world clock, so a paused replay still shows it). */
    public float visibility(float partial) {
        float t = level().getGameTime() + partial;
        int sp = entityData.get(SPAWNED);
        float a = sp < 0 ? 1f : Mth.clamp((t - sp) / FADE_IN, 0f, 1f);
        int f = fadeAt();
        if (f >= 0) a = Math.min(a, Mth.clamp(1f - (t - f) / FADE_OUT, 0f, 1f));
        return a;
    }

    private void beginFade() {
        if (fadeAt() < 0) {
            entityData.set(FADE_AT, (int) level().getGameTime());
            level().playSound(null, getX(), getY() + 1, getZ(), Disasters.sound("spirit.fade"), SoundSource.HOSTILE, 1.0f, 1f);
        }
    }

    @Override
    public void tick() {
        this.noPhysics = true;
        this.setNoGravity(true);
        super.tick();
        if (level().isClientSide() || Calamities.isReplay(((ServerLevel) level()).getServer())) return;
        float r = reveal(), goalR = Disasters.revealTarget();
        if (r != goalR) entityData.set(REVEAL, r < goalR ? Math.min(goalR, r + REVEAL_SPEED) : Math.max(goalR, r - REVEAL_SPEED));
        if (fadeAt() >= 0 && level().getGameTime() - fadeAt() > FADE_OUT) { discard(); Disasters.spiritGone(((ServerLevel) level()).getServer()); return; }
        if (tickCount > lifeTicks) beginFade();

        Player target = haunting == null ? null : level().getPlayerByUUID(haunting);
        if (target == null || !target.isAlive() || target.isSpectator() || target.level() != level()
                || target.distanceToSqr(this) > 48 * 48) {
            target = level().getNearestPlayer(this, 48);
            if (target != null && target.isSpectator()) target = null;
        }
        if (target == null) { beginFade(); setDeltaMovement(Vec3.ZERO); return; }

        double dist = Math.sqrt(target.distanceToSqr(this));
        if (dist < 2.2 && tickCount > FADE_IN) beginFade();

        // keep about 4.5 blocks off, slowly circling to the side the player is not looking at
        if (side == null || tickCount % 160 == 0) side = new Vec3(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5).normalize();
        Vec3 away = position().subtract(target.position()).multiply(1, 0, 1);
        if (away.lengthSqr() < 1e-4) away = side;
        away = away.normalize().add(side.scale(0.25)).normalize();
        double bob = Math.sin(tickCount * 0.07) * 0.18;
        Vec3 goal = target.position().add(away.scale(4.5)).add(0, 0.35 + bob, 0);
        Vec3 d = goal.subtract(position());
        double speed = fadeAt() >= 0 ? 0.0 : 0.045;
        Vec3 v = d.lengthSqr() > speed * speed ? d.normalize().scale(Math.min(speed, d.length())) : d;
        setDeltaMovement(v);
        move(net.minecraft.world.entity.MoverType.SELF, v);

        double lx = target.getX() - getX(), lz = target.getZ() - getZ();
        double ly = target.getEyeY() - getEyeY();
        float yaw = (float) (Mth.atan2(lz, lx) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) (-Mth.atan2(ly, Math.sqrt(lx * lx + lz * lz)) * Mth.RAD_TO_DEG);
        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
        setXRot(pitch);

        if (tickCount % 90 == 45 && fadeAt() < 0) {
            level().playSound(null, getX(), getY() + 1, getZ(), Disasters.sound("spirit.whisper"), SoundSource.HOSTILE, 0.8f,
                    0.9f + random.nextFloat() * 0.2f);
        }
    }

    // never hurt, never pushed, never targeted, never despawned by distance, never saved
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean canBeCollidedWith(net.minecraft.world.entity.Entity e) { return false; }
    @Override protected void doPush(net.minecraft.world.entity.Entity e) {}
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean shouldShowName() { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean canUsePortal(boolean b) { return false; }
    @Override protected void playStepSound(net.minecraft.core.BlockPos p, net.minecraft.world.level.block.state.BlockState s) {}
    @Override public boolean isSilent() { return true; }

    public Optional<UUID> haunting() { return Optional.ofNullable(haunting); }
}
