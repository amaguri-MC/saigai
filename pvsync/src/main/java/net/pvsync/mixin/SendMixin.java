package net.pvsync.mixin;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.pvsync.Ctx;
import net.pvsync.Rewriter;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** The one place every packet to a player passes: rewrite it for that receiver (03 [V]). */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class SendMixin {

    @Shadow
    public abstract void send(Packet<?> packet, @Nullable ChannelFutureListener listener);

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"), cancellable = true)
    private void pv$send(Packet<?> packet, @Nullable ChannelFutureListener listener, CallbackInfo ci) {
        if (!((Object) this instanceof ServerGamePacketListenerImpl game)) return;
        Ctx.Capture cap = Ctx.CAPTURE.get();
        if (cap != null && cap.player == game.player) {
            cap.packets.add(packet);
            ci.cancel();
            return;
        }
        if (Ctx.RAW.get()) return;
        List<Packet<?>> out = Rewriter.rewrite(packet, game.player);
        if (out == null) return;
        ci.cancel();
        Ctx.RAW.set(true);
        try {
            boolean first = true;
            for (Packet<?> p : out) {
                this.send(p, first ? listener : null);
                first = false;
            }
        } finally {
            Ctx.RAW.set(false);
        }
    }
}
