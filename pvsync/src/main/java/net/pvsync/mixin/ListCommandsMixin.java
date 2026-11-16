package net.pvsync.mixin;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.ListPlayersCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.pvsync.Groups;
import net.pvsync.Vis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;

/** H05: /list counts and names only people the typist may know; the max is the group's. */
@Mixin(ListPlayersCommand.class)
public abstract class ListCommandsMixin {
    @Redirect(method = "format", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;getPlayers()Ljava/util/List;"))
    private static List<ServerPlayer> pv$players(PlayerList list) {
        ServerPlayer o = Vis.cmdObserver();
        if (o == null) return list.getPlayers();
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : list.getPlayers()) if (Vis.real(p.getUUID(), o.getUUID())) out.add(p);
        return out;
    }

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;getMaxPlayers()I"))
    private static int pv$max(PlayerList list) {
        ServerPlayer o = Vis.cmdObserver();
        if (o == null || Vis.revealed()) return list.getMaxPlayers();
        return Groups.maxPlayers(Vis.group(o.getUUID()), list.getServer());
    }
}
