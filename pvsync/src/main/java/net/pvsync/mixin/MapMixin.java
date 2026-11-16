package net.pvsync.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.pvsync.Vis;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** H16: a shared map does not show the marker of someone of another group to this holder. */
@Mixin(MapItemSavedData.HoldingPlayer.class)
public abstract class MapMixin {
    @Shadow @Final public Player player;

    @Redirect(method = "nextUpdatePacket", at = @At(value = "INVOKE", target = "Ljava/util/Map;values()Ljava/util/Collection;"))
    private Collection<MapDecoration> pv$markers(Map<String, MapDecoration> all) {
        if (!(this.player instanceof ServerPlayer o)) return all.values();
        Set<String> hidden = Vis.hiddenNames(o.getUUID());
        if (hidden.isEmpty()) return all.values();
        List<MapDecoration> out = new ArrayList<>();
        for (var en : all.entrySet()) if (!hidden.contains(en.getKey().toLowerCase(Locale.ROOT))) out.add(en.getValue());
        return out;
    }
}
