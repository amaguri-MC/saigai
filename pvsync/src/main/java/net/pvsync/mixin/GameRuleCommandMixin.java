package net.pvsync.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.GameRuleCommand;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import net.pvsync.Ctx;
import net.pvsync.Log;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** H29: every /gamerule change goes to the log: who, what, before, after. */
@Mixin(GameRuleCommand.class)
public abstract class GameRuleCommandMixin {
    @WrapOperation(method = "setRule", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/gamerules/GameRules;set(Lnet/minecraft/world/level/gamerules/GameRule;Ljava/lang/Object;Lnet/minecraft/server/MinecraftServer;)V"))
    private static <T> void pv$log(GameRules rules, GameRule<T> rule, T value, MinecraftServer server, Operation<Void> op) {
        T before = rules.get(rule);
        op.call(rules, rule, value, server);
        var p = Ctx.TYPIST.get();
        String who = p == null ? "server" : p.getGameProfile().name();
        Log.info("gamerule " + rule.id() + " " + rule.serialize(before) + " -> " + rule.serialize(value) + " by " + who);
    }
}
