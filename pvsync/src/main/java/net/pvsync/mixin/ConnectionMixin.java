package net.pvsync.mixin;

import net.minecraft.network.Connection;
import net.pvsync.HostHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Connection.class)
public abstract class ConnectionMixin implements HostHolder {
    @Unique
    private volatile String pv$host;

    @Override
    public String pv$host() { return pv$host; }

    @Override
    public void pv$setHost(String h) { pv$host = h; }
}
