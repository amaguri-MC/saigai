package net.pvsync.mixin;

import net.minecraft.server.commands.BanListCommands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.BanListEntry;
import net.pvsync.Vis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** H06: /banlist without the people of another group (as banned name or as the one who banned). */
@Mixin(BanListCommands.class)
public abstract class BanListMixin {
    @ModifyVariable(method = "showList", at = @At("HEAD"), argsOnly = true)
    private static Collection<? extends BanListEntry<?>> pv$entries(Collection<? extends BanListEntry<?>> all) {
        ServerPlayer o = Vis.cmdObserver();
        if (o == null) return all;
        Set<String> hidden = Vis.hiddenNames(o.getUUID());
        if (hidden.isEmpty()) return all;
        List<BanListEntry<?>> out = new ArrayList<>();
        for (BanListEntry<?> e : all) {
            String who = e.getDisplayName().getString().toLowerCase(Locale.ROOT);
            String by = e.getSource() == null ? "" : e.getSource().toLowerCase(Locale.ROOT);
            if (hidden.contains(who) || hidden.contains(by)) continue;
            out.add(e);
        }
        return out;
    }
}
