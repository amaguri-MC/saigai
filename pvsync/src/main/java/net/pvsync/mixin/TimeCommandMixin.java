package net.pvsync.mixin;

import net.minecraft.server.commands.TimeCommand;
import net.minecraft.server.level.ServerLevel;
import net.pvsync.Cfg;
import net.pvsync.Sky;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** H14: /time typed by a group player reads and changes that group's clock only. */
@Mixin(TimeCommand.class)
public abstract class TimeCommandMixin {
    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getDayTime()J"))
    private static long pv$get(ServerLevel level) {
        String key = Sky.commandKey();
        return key == null ? level.getDayTime() : level.getDayTime() + Sky.offset(key);
    }

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getDayCount()J"))
    private static long pv$day(ServerLevel level) {
        String key = Sky.commandKey();
        return key == null ? level.getDayCount() : (level.getDayTime() + Sky.offset(key)) / 24000L;
    }

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setDayTime(J)V"))
    private static void pv$set(ServerLevel level, long t) {
        String key = Sky.commandKey();
        if (key == null) {
            level.setDayTime(t);
            return;
        }
        Sky.setOffset(key, t - level.getDayTime());
        Cfg.saveState();
    }
}
