package io.github.kestrelworks.calamities;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Who a disaster is about, when a view-sync mod shares the world. Looked up by reflection (no compile dependency).
 * Without that mod: everybody is a target and the real world weather is used.
 */
final class Pv {

    private Pv() {}

    private static boolean looked, present, broken;
    private static MethodHandle isMember, isDirector, weatherKey, isRaining, isThundering, setWeather, setOverlay;

    private static synchronized void look() {
        if (looked) return;
        looked = true;
        if (!FabricLoader.getInstance().isModLoaded("pvsync")) return;
        present = true;
        try {
            Class<?> api = Class.forName("net.pvsync.Api");
            MethodHandles.Lookup l = MethodHandles.publicLookup();
            isMember = l.findStatic(api, "isMember", MethodType.methodType(boolean.class, UUID.class, String.class));
            isDirector = l.findStatic(api, "isDirector", MethodType.methodType(boolean.class, UUID.class));
            weatherKey = l.findStatic(api, "weatherKey", MethodType.methodType(String.class, String.class));
            isRaining = l.findStatic(api, "isRaining", MethodType.methodType(boolean.class, String.class));
            isThundering = l.findStatic(api, "isThundering", MethodType.methodType(boolean.class, String.class));
            setWeather = l.findStatic(api, "setWeather", MethodType.methodType(void.class, String.class, int.class, int.class, boolean.class, boolean.class));
            setOverlay = l.findStatic(api, "setWeatherOverlay", MethodType.methodType(void.class, ServerPlayer.class, String.class));
            Calamities.LOG.info("view sync found: disasters are for group '{}'", CalamityConfig.get().targetGroup);
        } catch (Throwable t) {
            // present but unusable: nobody is a target (never leak into the other group)
            broken = true;
            Calamities.LOG.warn("view sync found but its api is unusable; no disaster will reach anyone", t);
        }
    }

    /** True when a view-sync mod is in. */
    static boolean active() {
        look();
        return present;
    }

    private static boolean warnedBlank;

    /** The configured group; an empty setting falls back to the default "S" (with one warning). */
    private static String group() {
        String g = CalamityConfig.get().targetGroup;
        if (g != null && !g.isBlank()) return g;
        if (!warnedBlank) {
            warnedBlank = true;
            Calamities.LOG.warn("targetGroup is empty while view sync is in; using S");
        }
        return "S";
    }

    /** Is this player the one disasters are about (camera, freezing, wind, lightning hits, voices, where they start). */
    static boolean isTarget(Player p) {
        if (!active()) return true;
        if (broken) return false;
        if (CalamityConfig.get().directorsAreTargets && director(p)) return true;
        try {
            return (boolean) isMember.invoke(p.getUUID(), group());
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean director(Player p) {
        try {
            return (boolean) isDirector.invoke(p.getUUID());
        } catch (Throwable t) {
            return true;
        }
    }

    /** Players who are not targets, not directors, in the same dimension within the share radius of a target (they may see and hear it). */
    static List<ServerPlayer> nearby(MinecraftServer server, List<ServerPlayer> targets) {
        List<ServerPlayer> out = new ArrayList<>();
        if (!active() || broken) return out;
        double r = CalamityConfig.get().shareRadius;
        if (r <= 0) return out;
        for (ServerPlayer o : server.getPlayerList().getPlayers()) {
            if (o.isSpectator() || isTarget(o) || director(o)) continue;
            for (ServerPlayer t : targets) {
                if (t.level() == o.level() && t.distanceToSqr(o) <= r * r) { out.add(o); break; }
            }
        }
        return out;
    }

    // ---- apparent weather of the target group (the real world is held clear by the view-sync mod)

    static boolean thundering(ServerLevel ow) {
        if (!active()) return ow.getLevelData().isThundering();
        try { return !broken && (boolean) isThundering.invoke((String) weatherKey.invoke(group())); } catch (Throwable t) { return false; }
    }

    static boolean raining(ServerLevel ow) {
        if (!active()) return ow.getLevelData().isRaining();
        try { return !broken && (boolean) isRaining.invoke((String) weatherKey.invoke(group())); } catch (Throwable t) { return false; }
    }

    static void setWeather(ServerLevel ow, int clear, int time, boolean rain, boolean thunder) {
        if (!active()) { ow.setWeatherParameters(clear, time, rain, thunder); return; }
        if (broken) return;
        try { setWeather.invoke((String) weatherKey.invoke(group()), clear, time, rain, thunder); } catch (Throwable t) { Calamities.LOG.warn("weather call failed", t); }
    }

    /** Show this player the target group's apparent weather (on = true) or their own again. */
    static void overlay(ServerPlayer p, boolean on) {
        if (!active() || broken) return;
        try { setOverlay.invoke(p, on ? (String) weatherKey.invoke(group()) : null); } catch (Throwable t) { Calamities.LOG.warn("weather overlay failed", t); }
    }
}
