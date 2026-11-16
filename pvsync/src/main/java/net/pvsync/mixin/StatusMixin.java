package net.pvsync.mixin;

import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.network.ServerStatusPacketListenerImpl;
import net.pvsync.Groups;
import net.pvsync.HostHolder;
import net.pvsync.Vis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** H03: answer the server list per host name. */
@Mixin(ServerStatusPacketListenerImpl.class)
public abstract class StatusMixin {
    @Redirect(method = "handleStatusRequest", at = @At(value = "FIELD", target = "Lnet/minecraft/server/network/ServerStatusPacketListenerImpl;status:Lnet/minecraft/network/protocol/status/ServerStatus;"))
    private ServerStatus pv$status(ServerStatusPacketListenerImpl self) {
        ServerStatus base = ((StatusAccessor) self).pv$baseStatus();
        if (Vis.server == null) return base;
        String host = ((HostHolder) self.connection).pv$host();
        return Groups.status(Vis.server, base, host);
    }
}
