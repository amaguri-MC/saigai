package net.pvsync.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.pvsync.Cfg;
import net.pvsync.Ctx;
import net.pvsync.Groups;
import net.pvsync.HostHolder;
import net.pvsync.Log;
import net.pvsync.PvMod;
import net.pvsync.Sky;
import net.pvsync.Vis;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.SocketAddress;
import java.util.List;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Shadow @Final private MinecraftServer server;

    /** [G] group from the members table or the host; H19 first spawn of the group. */
    @Inject(method = "placeNewPlayer", at = @At("HEAD"))
    private void pv$join(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        String host = ((HostHolder) connection).pv$host();
        PvMod.rememberHost(player.getUUID(), host);
        boolean first = ((PlayerList) (Object) this).loadPlayerData(player.nameAndId()).isEmpty();
        Groups.onJoin(player, host);
        if (first) {
            Cfg.Spawn sp = Groups.spawnOf(player.getUUID());
            if (sp != null) {
                ServerLevel level = Groups.spawnLevel(this.server, sp);
                if (level != null) {
                    if (level != player.level()) player.setServerLevel(level);
                    player.snapTo(sp.x, sp.y, sp.z, sp.yaw, sp.pitch);
                    Log.info(player.getGameProfile().name() + " first join at the spawn of group " + Vis.group(player.getUUID()));
                }
            }
        }
    }

    /** H01: right after the scoreboard, tell this receiver about the team that never shows names. */
    @Inject(method = "placeNewPlayer", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/players/PlayerList;updateEntireScoreboard(Lnet/minecraft/server/ServerScoreboard;Lnet/minecraft/server/level/ServerPlayer;)V",
        shift = At.Shift.AFTER))
    private void pv$team(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        Groups.sendNamelessTeam(player);
        Groups.sendTabTexts(player);
    }

    /** H28: full is counted per group. */
    @WrapOperation(method = "canPlayerLogin", at = @At(value = "INVOKE", target = "Ljava/util/List;size()I"))
    private int pv$count(List<?> list, Operation<Integer> op, SocketAddress addr, NameAndId who) {
        if (Vis.configDirector(who.id(), who.name())) return 0;
        String g = Vis.resolveGroup(who.id(), who.name(), Ctx.LOGIN_HOST.get());
        if (g == null) return op.call(list);
        return Groups.counted(g).size();
    }

    @WrapOperation(method = "canPlayerLogin", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;getMaxPlayers()I"))
    private int pv$max(PlayerList self, Operation<Integer> op, SocketAddress addr, NameAndId who) {
        String g = Vis.resolveGroup(who.id(), who.name(), Ctx.LOGIN_HOST.get());
        if (g == null) return op.call(self);
        return Groups.maxPlayers(g, this.server);
    }

    /** H14: the group's own weather after the level info (join, respawn, dimension change). */
    @Inject(method = "sendLevelInfo", at = @At("TAIL"))
    private void pv$weather(ServerPlayer player, ServerLevel level, CallbackInfo ci) {
        if (level.canHaveWeather()) Sky.sendWeather(player);
    }
}
