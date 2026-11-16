package net.pvsync.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.server.ServerInfo;
import net.minecraft.server.network.LegacyQueryHandler;
import net.pvsync.Cfg;
import net.pvsync.Groups;
import net.pvsync.Vis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * H03 for the old (0xFE) server list ping. The 1.6 form carries the host name: answer for that group.
 * Older forms carry none: answered like an unknown host (0 people, the server's own description).
 */
@Mixin(LegacyQueryHandler.class)
public abstract class LegacyQueryMixin {
    @Unique
    private static final ThreadLocal<List<String>> pv$strings = ThreadLocal.withInitial(ArrayList::new);

    @Inject(method = "channelRead", at = @At("HEAD"))
    private void pv$start(ChannelHandlerContext ctx, Object o, CallbackInfo ci) {
        pv$strings.get().clear();
    }

    @WrapOperation(method = "readCustomPayloadPacket", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/network/LegacyProtocolUtils;readLegacyString(Lio/netty/buffer/ByteBuf;)Ljava/lang/String;"))
    private static String pv$read(ByteBuf buf, Operation<String> op) {
        String s = op.call(buf);
        pv$strings.get().add(s);
        return s;
    }

    @Unique
    private static String pv$group() {
        List<String> l = pv$strings.get();
        return l.size() >= 2 ? Vis.groupForHost(l.get(1)) : null;   // "MC|PingHost", then the host name
    }

    @Redirect(method = {"createVersion0Response", "createVersion1Response"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/server/ServerInfo;getPlayerCount()I"))
    private static int pv$count(ServerInfo info) {
        if (Vis.revealed()) return Groups.counted(null).size();
        String g = pv$group();
        return g == null ? 0 : Groups.counted(g).size();
    }

    @Redirect(method = {"createVersion0Response", "createVersion1Response"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/server/ServerInfo;getMaxPlayers()I"))
    private static int pv$max(ServerInfo info) {
        String g = pv$group();
        if (g == null || Vis.revealed() || Vis.server == null) return info.getMaxPlayers();
        return Groups.maxPlayers(g, Vis.server);
    }

    @Redirect(method = {"createVersion0Response", "createVersion1Response"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/server/ServerInfo;getMotd()Ljava/lang/String;"))
    private static String pv$motd(ServerInfo info) {
        String g = pv$group();
        if (g == null || Vis.revealed()) return info.getMotd();
        Cfg.Group c = Cfg.settings.groups.get(g);
        return c == null || c.motd == null ? info.getMotd() : c.motd;
    }
}
