package net.pvsync.mixin;

import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.network.ServerStatusPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerStatusPacketListenerImpl.class)
public interface StatusAccessor {
    @Accessor("status")
    ServerStatus pv$baseStatus();
}
