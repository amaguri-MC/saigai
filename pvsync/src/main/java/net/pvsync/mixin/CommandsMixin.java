package net.pvsync.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.pvsync.Ctx;
import org.spongepowered.asm.mixin.Mixin;

/** Remember who typed the command that runs now (the answers are made for that person's group). */
@Mixin(Commands.class)
public abstract class CommandsMixin {
    @WrapMethod(method = "performCommand")
    private void pv$typist(ParseResults<CommandSourceStack> parse, String cmd, Operation<Void> op) {
        ServerPlayer p = parse.getContext().getSource().getPlayer();
        ServerPlayer prev = Ctx.TYPIST.get();
        if (prev == null && p != null) Ctx.TYPIST.set(p);
        try {
            op.call(parse, cmd);
        } finally {
            if (prev == null) Ctx.TYPIST.remove();
        }
    }
}
