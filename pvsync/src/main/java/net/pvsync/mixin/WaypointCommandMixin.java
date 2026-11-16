package net.pvsync.mixin;

import net.minecraft.server.commands.WaypointCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.waypoints.ServerWaypointManager;
import net.minecraft.world.waypoints.WaypointTransmitter;
import net.pvsync.Vis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.LinkedHashSet;
import java.util.Set;

/** H06: /waypoint list leaves out people of another group. */
@Mixin(WaypointCommand.class)
public abstract class WaypointCommandMixin {
    @Redirect(method = "listWaypoints", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/waypoints/ServerWaypointManager;transmitters()Ljava/util/Set;"))
    private static Set<WaypointTransmitter> pv$list(ServerWaypointManager m) {
        ServerPlayer o = Vis.cmdObserver();
        if (o == null) return m.transmitters();
        Set<WaypointTransmitter> out = new LinkedHashSet<>();
        for (WaypointTransmitter t : m.transmitters()) if (!(t instanceof ServerPlayer p) || Vis.real(p.getUUID(), o.getUUID())) out.add(t);
        return out;
    }
}
