package net.pvsync.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.pvsync.Ctx;
import org.spongepowered.asm.mixin.Mixin;

/** The "wake up when it is day" check in Player.tick also uses the group's clock. */
@Mixin(Player.class)
public abstract class PlayerTickMixin {
    @WrapMethod(method = "tick")
    private void pv$tick(Operation<Void> op) {
        if (!((Object) this instanceof ServerPlayer sp)) {
            op.call();
            return;
        }
        ServerPlayer prev = Ctx.SLEEPER.get();
        Ctx.SLEEPER.set(sp);
        try {
            op.call();
        } finally {
            if (prev == null) Ctx.SLEEPER.remove(); else Ctx.SLEEPER.set(prev);
        }
    }
}
