package net.pvsync.mixin;

import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.commands.TeamCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.pvsync.Owners;
import net.pvsync.Vis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** H06 / H17: /team list answers only with what the typist's group may know. */
@Mixin(TeamCommand.class)
public abstract class TeamCommandMixin {
    @Redirect(method = "listMembers", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/scores/PlayerTeam;getPlayers()Ljava/util/Collection;"))
    private static Collection<String> pv$members(PlayerTeam team) {
        ServerPlayer o = Vis.cmdObserver();
        if (o == null) return team.getPlayers();
        Set<String> hidden = Vis.hiddenNames(o.getUUID());
        List<String> out = new ArrayList<>();
        for (String n : team.getPlayers()) if (!hidden.contains(n.toLowerCase(Locale.ROOT))) out.add(n);
        return out;
    }

    @Redirect(method = "listTeams", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/ServerScoreboard;getPlayerTeams()Ljava/util/Collection;"))
    private static Collection<PlayerTeam> pv$teams(ServerScoreboard sb) {
        ServerPlayer o = Vis.cmdObserver();
        if (o == null) return sb.getPlayerTeams();
        List<PlayerTeam> out = new ArrayList<>();
        for (PlayerTeam t : sb.getPlayerTeams()) if (Owners.teamVisible(t.getName(), o.getUUID())) out.add(t);
        return out;
    }
}
