package io.github.kestrelworks.calamities;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;

/** A storm lightning bolt. Looks like vanilla lightning; its rolling thunder is the mod's own (LightningBoltMixin). */
public class StormBolt extends LightningBolt {

    static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(StormBolt.class, EntityDataSerializers.INT);

    public StormBolt(EntityType<? extends LightningBolt> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(VARIANT, 0);
    }

    public int variant() { return entityData.get(VARIANT); }

    public void setVariant(int v) { entityData.set(VARIANT, v); }
}
