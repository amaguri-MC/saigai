package net.pvsync.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.pvsync.Vis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** H05: selectors and typed names do not find players of another group (same answer as "not online"). */
@Mixin(EntitySelector.class)
public abstract class SelectorMixin {

    @ModifyReturnValue(method = "getPredicate", at = @At("RETURN"))
    private Predicate<Entity> pv$predicate(Predicate<Entity> original) {
        ServerPlayer o = Vis.selectorObserver();
        if (o == null) return original;
        return original.and(e -> !(e instanceof ServerPlayer p) || Vis.real(p.getUUID(), o.getUUID()));
    }

    @ModifyReturnValue(method = "findEntities", at = @At("RETURN"))
    private List<? extends Entity> pv$entities(List<? extends Entity> original, CommandSourceStack src) {
        ServerPlayer o = Vis.selectorObserver();
        if (o == null) return original;
        List<Entity> out = new ArrayList<>(original.size());
        for (Entity e : original) if (!(e instanceof ServerPlayer p) || Vis.real(p.getUUID(), o.getUUID())) out.add(e);
        return out.size() == original.size() ? original : out;
    }

    @ModifyReturnValue(method = "findPlayers", at = @At("RETURN"))
    private List<ServerPlayer> pv$players(List<ServerPlayer> original, CommandSourceStack src) {
        ServerPlayer o = Vis.selectorObserver();
        if (o == null) return original;
        List<ServerPlayer> out = new ArrayList<>(original.size());
        for (ServerPlayer p : original) if (Vis.real(p.getUUID(), o.getUUID())) out.add(p);
        return out.size() == original.size() ? original : out;
    }
}
