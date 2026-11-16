package net.pvsync.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.pvsync.Ctx;
import org.spongepowered.asm.mixin.Mixin;

/** H05: a text made for one receiver (title, tellraw, ...) resolves its selectors as that receiver would see players. */
@Mixin(ComponentUtils.class)
public abstract class ResolveForMixin {
    @WrapMethod(method = "updateForEntity(Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/network/chat/Component;Lnet/minecraft/world/entity/Entity;I)Lnet/minecraft/network/chat/MutableComponent;")
    private static MutableComponent pv$for(CommandSourceStack src, Component c, Entity e, int depth, Operation<MutableComponent> op) {
        if (depth != 0 || !(e instanceof ServerPlayer p) || Ctx.RESOLVE_FOR.get() != null) return op.call(src, c, e, depth);
        Ctx.RESOLVE_FOR.set(p);
        try {
            return op.call(src, c, e, depth);
        } finally {
            Ctx.RESOLVE_FOR.remove();
        }
    }
}
