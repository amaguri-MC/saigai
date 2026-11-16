package net.pvsync.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.pvsync.Heads;
import org.spongepowered.asm.mixin.Mixin;

import java.util.UUID;

/** Remember who the packet being written is for (player heads are written per receiver). */
@Mixin(PacketEncoder.class)
public abstract class EncodeForMixin {
    @WrapMethod(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Lio/netty/buffer/ByteBuf;)V")
    private void pv$for(ChannelHandlerContext ctx, Packet<?> packet, ByteBuf buf, Operation<Void> op) {
        UUID who = null;
        if (ctx.pipeline().get("packet_handler") instanceof Connection c && c.getPacketListener() instanceof ServerGamePacketListenerImpl g) {
            who = g.player.getUUID();
        }
        UUID prev = Heads.ENCODING_FOR.get();
        Heads.ENCODING_FOR.set(who);
        try {
            op.call(ctx, packet, buf);
        } finally {
            if (prev == null) Heads.ENCODING_FOR.remove(); else Heads.ENCODING_FOR.set(prev);
        }
    }
}
