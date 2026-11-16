package net.pvsync.mixin;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.commands.ScoreboardCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.pvsync.Owners;
import net.pvsync.Vis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** H06 / H17: /scoreboard lists answer only with what the typist's group may know. */
@Mixin(ScoreboardCommand.class)
public abstract class ScoreboardCommandMixin {
    @Redirect(method = "listTrackedPlayers", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/ServerScoreboard;getTrackedPlayers()Ljava/util/Collection;"))
    private static Collection<ScoreHolder> pv$tracked(ServerScoreboard sb) {
        ServerPlayer o = Vis.cmdObserver();
        if (o == null) return sb.getTrackedPlayers();
        List<ScoreHolder> out = new ArrayList<>();
        for (ScoreHolder h : sb.getTrackedPlayers()) if (!Vis.nameHidden(h.getScoreboardName(), o.getUUID())) out.add(h);
        return out;
    }

    @Redirect(method = "listTrackedPlayerScores", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/ServerScoreboard;listPlayerScores(Lnet/minecraft/world/scores/ScoreHolder;)Lit/unimi/dsi/fastutil/objects/Object2IntMap;"))
    private static Object2IntMap<Objective> pv$scores(ServerScoreboard sb, ScoreHolder h) {
        ServerPlayer o = Vis.cmdObserver();
        Object2IntMap<Objective> all = sb.listPlayerScores(h);
        if (o == null) return all;
        if (Vis.nameHidden(h.getScoreboardName(), o.getUUID())) return new Object2IntOpenHashMap<>();
        Object2IntMap<Objective> out = new Object2IntOpenHashMap<>();
        for (var en : all.object2IntEntrySet()) if (Owners.objVisible(en.getKey().getName(), o.getUUID())) out.put(en.getKey(), en.getIntValue());
        return out;
    }

    @Redirect(method = "listObjectives", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/ServerScoreboard;getObjectives()Ljava/util/Collection;"))
    private static Collection<Objective> pv$objectives(ServerScoreboard sb) {
        ServerPlayer o = Vis.cmdObserver();
        if (o == null) return sb.getObjectives();
        List<Objective> out = new ArrayList<>();
        for (Objective obj : sb.getObjectives()) if (Owners.objVisible(obj.getName(), o.getUUID())) out.add(obj);
        return out;
    }
}
