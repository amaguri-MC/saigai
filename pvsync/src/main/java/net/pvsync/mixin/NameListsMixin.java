package net.pvsync.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.pvsync.Vis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** H06: /whitelist list and the op / deop name lists, during a group player's command. */
@Mixin(PlayerList.class)
public abstract class NameListsMixin {
    @Unique
    private static String[] pv$filter(String[] names) {
        ServerPlayer o = Vis.cmdObserver();
        if (o == null) return names;
        Set<String> hidden = Vis.hiddenNames(o.getUUID());
        if (hidden.isEmpty()) return names;
        List<String> out = new ArrayList<>();
        for (String n : names) if (!hidden.contains(n.toLowerCase(Locale.ROOT))) out.add(n);
        return out.toArray(new String[0]);
    }

    @ModifyReturnValue(method = "getWhiteListNames", at = @At("RETURN"))
    private String[] pv$white(String[] names) { return pv$filter(names); }

    @ModifyReturnValue(method = "getOpNames", at = @At("RETURN"))
    private String[] pv$ops(String[] names) { return pv$filter(names); }
}
