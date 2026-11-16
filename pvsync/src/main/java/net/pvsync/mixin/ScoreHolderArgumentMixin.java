package net.pvsync.mixin;

import net.minecraft.commands.arguments.ScoreHolderArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.ScoreHolder;
import net.pvsync.Vis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/** H06: the score holder wildcard "*" leaves out people of another group. */
@Mixin(ScoreHolderArgument.class)
public abstract class ScoreHolderArgumentMixin {
    @ModifyArg(method = "getNamesWithDefaultWildcard", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/commands/arguments/ScoreHolderArgument;getNames(Lcom/mojang/brigadier/context/CommandContext;Ljava/lang/String;Ljava/util/function/Supplier;)Ljava/util/Collection;"),
        index = 2)
    private static Supplier<Collection<ScoreHolder>> pv$wild(Supplier<Collection<ScoreHolder>> all) {
        ServerPlayer o = Vis.cmdObserver();
        if (o == null) return all;
        return () -> {
            List<ScoreHolder> out = new ArrayList<>();
            for (ScoreHolder h : all.get()) if (!Vis.nameHidden(h.getScoreboardName(), o.getUUID())) out.add(h);
            return out;
        };
    }
}
