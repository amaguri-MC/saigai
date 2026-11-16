package net.pvsync;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Small public surface for other server mods (called by reflection, so they need no compile-time dependency).
 * Every answer comes from the same functions the rest of this mod uses ({@link Vis#group}, {@link Vis#isDirector}).
 */
public final class Api {
    private Api() {}

    /** Bumped when a method here changes meaning. */
    public static int version() {
        return 1;
    }

    /** Is this player (not a director) in the group with this key? Same table {@link Vis#see} reads. */
    public static boolean isMember(UUID id, String group) {
        if (id == null || group == null) return false;
        if (Vis.isDirector(id)) return false;
        return group.equals(Vis.group(id));
    }

    public static boolean isDirector(UUID id) {
        return Vis.isDirector(id);
    }

    /** Key of the apparent weather the members of this group are shown (after the reveal everybody shares one). */
    public static String weatherKey(String group) {
        return Vis.revealed() ? Cfg.settings.revealTimeFrom : group;
    }

    public static boolean isRaining(String key) {
        Cfg.Weather w = Sky.weather(key);
        return w != null && w.raining;
    }

    public static boolean isThundering(String key) {
        Cfg.Weather w = Sky.weather(key);
        return w != null && w.thundering;
    }

    /** Same fields and meaning as ServerLevel.setWeatherParameters, for this group's apparent weather only. */
    public static void setWeather(String key, int clear, int time, boolean rain, boolean thunder) {
        Cfg.Weather w = Sky.weather(key);
        if (w == null) return;
        w.clearTime = clear;
        w.rainTime = time;
        w.thunderTime = time;
        w.raining = rain;
        w.thundering = thunder;
        Cfg.saveState();
    }

    /** Show this player the apparent weather of {@code key} for now (null = their own again). */
    public static void setWeatherOverlay(ServerPlayer p, String key) {
        Sky.setOverlay(p, key);
    }
}
