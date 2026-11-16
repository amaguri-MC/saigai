package net.pvsync.mixin;

import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.pvsync.Owners;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** H17: remember which group made a team / objective with a command. */
@Mixin(Scoreboard.class)
public abstract class ScoreboardOwnerMixin {
    @Inject(method = "addPlayerTeam", at = @At("RETURN"))
    private void pv$team(String name, CallbackInfoReturnable<PlayerTeam> cir) {
        Owners.record("team:" + name);
    }

    @Inject(method = "addObjective", at = @At("RETURN"))
    private void pv$obj(CallbackInfoReturnable<Objective> cir) {
        Objective o = cir.getReturnValue();
        if (o != null) Owners.record("obj:" + o.getName());
    }

    @Inject(method = "removePlayerTeam", at = @At("HEAD"))
    private void pv$teamGone(PlayerTeam team, CallbackInfo ci) {
        Owners.forget("team:" + team.getName());
    }

    @Inject(method = "removeObjective", at = @At("HEAD"))
    private void pv$objGone(Objective obj, CallbackInfo ci) {
        Owners.forget("obj:" + obj.getName());
    }
}
