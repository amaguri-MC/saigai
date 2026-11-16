package net.pvsync.mixin;

import net.minecraft.server.commands.WeatherCommand;
import net.minecraft.server.level.ServerLevel;
import net.pvsync.Sky;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** H14: /weather typed by a group player changes that group's sky only (from the server: every group). */
@Mixin(WeatherCommand.class)
public abstract class WeatherCommandMixin {
    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setWeatherParameters(IIZZ)V"))
    private static void pv$set(ServerLevel level, int clear, int time, boolean rain, boolean thunder) {
        Sky.setWeather(clear, time, rain, thunder);
    }
}
