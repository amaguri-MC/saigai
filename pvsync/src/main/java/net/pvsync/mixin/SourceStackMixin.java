package net.pvsync.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.pvsync.Vis;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;

@Mixin(CommandSourceStack.class)
public abstract class SourceStackMixin {
    @Shadow @Final private Entity entity;

    /** H04: names offered by the server for completion. */
    @ModifyReturnValue(method = "getOnlinePlayerNames", at = @At("RETURN"))
    private Collection<String> pv$names(Collection<String> original) {
        if (!(this.entity instanceof ServerPlayer o) || Vis.isDirector(o)) return original;
        Set<String> hidden = Vis.hiddenNames(o.getUUID());
        if (hidden.isEmpty()) return original;
        Collection<String> out = new ArrayList<>();
        for (String n : original) if (!hidden.contains(n.toLowerCase(Locale.ROOT))) out.add(n);
        return out;
    }

    /** H07: the grey "[name: ...]" goes only to ops who may see the person who typed. */
    @WrapOperation(method = "broadcastToAdmins", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;sendSystemMessage(Lnet/minecraft/network/chat/Component;)V"))
    private void pv$admins(ServerPlayer observer, Component c, Operation<Void> op) {
        if (this.entity instanceof ServerPlayer actor && !Vis.real(actor.getUUID(), observer.getUUID())) return;
        ServerPlayer typist = Vis.cmdObserver();
        if (typist != null && !Vis.real(typist.getUUID(), observer.getUUID())) return;
        op.call(observer, c);
    }
}
