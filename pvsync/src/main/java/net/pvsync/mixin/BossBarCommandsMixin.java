package net.pvsync.mixin;

import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.commands.BossBarCommands;
import net.minecraft.server.level.ServerPlayer;
import net.pvsync.Owners;
import net.pvsync.Vis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** H06 / H18: /bossbar get ... players and /bossbar list answer for the typist's group. */
@Mixin(BossBarCommands.class)
public abstract class BossBarCommandsMixin {
    @Redirect(method = "getPlayers", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/bossevents/CustomBossEvent;getPlayers()Ljava/util/Collection;"))
    private static Collection<ServerPlayer> pv$players(CustomBossEvent bar) {
        ServerPlayer o = Vis.cmdObserver();
        if (o == null) return bar.getPlayers();
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : bar.getPlayers()) if (Vis.real(p.getUUID(), o.getUUID())) out.add(p);
        return out;
    }

    @Redirect(method = "listBars", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/bossevents/CustomBossEvents;getEvents()Ljava/util/Collection;"))
    private static Collection<CustomBossEvent> pv$bars(CustomBossEvents all) {
        ServerPlayer o = Vis.cmdObserver();
        if (o == null) return all.getEvents();
        List<CustomBossEvent> out = new ArrayList<>();
        for (CustomBossEvent b : all.getEvents()) if (Owners.barIdVisible(b.getTextId().toString(), o.getUUID())) out.add(b);
        return out;
    }
}
