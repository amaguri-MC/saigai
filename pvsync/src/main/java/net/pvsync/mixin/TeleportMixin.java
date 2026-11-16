package net.pvsync.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.pvsync.Alias;
import net.pvsync.Vis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.UUID;

/** H21: spectator "teleport to player" understands stand-in uuids; a real uuid of a hidden person finds nobody. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class TeleportMixin {
    @Shadow public ServerPlayer player;

    @WrapOperation(method = "handleTeleportToEntityPacket", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/network/protocol/game/ServerboundTeleportToEntityPacket;getEntity(Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/world/entity/Entity;"))
    private Entity pv$target(ServerboundTeleportToEntityPacket packet, ServerLevel level, Operation<Entity> op) {
        UUID id = packet.uuid;
        UUID real = Alias.realFor(id, this.player.getUUID());
        if (real != null) {
            Entity e = level.getEntity(real);
            return e instanceof ServerPlayer p && Vis.see(p.getUUID(), this.player.getUUID()) == Vis.See.ALIAS ? e : null;
        }
        Entity e = op.call(packet, level);
        if (e instanceof ServerPlayer p && !Vis.real(p.getUUID(), this.player.getUUID())) return null;
        return e;
    }
}
