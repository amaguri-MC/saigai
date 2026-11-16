package net.pvsync;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * H13 / H14: every group has its own clock (= real day time + offset) and its own weather.
 * The real world weather is held clear; each group's weather runs the vanilla cycle on its own.
 */
public final class Sky {
    private Sky() {}

    private static final RandomSource RANDOM = RandomSource.create();
    private static final net.minecraft.util.valueproviders.IntProvider THUNDER_DELAY = net.minecraft.util.valueproviders.UniformInt.of(12000, 180000);

    // ------------------------------------------------------------------ time

    public static long offset(String key) {
        if (key == null) return 0L;
        Long v = Cfg.state.timeOffset.get(key);
        return v == null ? 0L : v;
    }

    public static void setOffset(String key, long v) {
        if (key == null) return;
        Cfg.state.timeOffset.put(key, v);
    }

    public static long realDayTime() {
        MinecraftServer s = Vis.server;
        return s == null ? 0L : s.overworld().getDayTime();
    }

    public static long dayTime(String key) {
        return realDayTime() + offset(key);
    }

    /** Key of the clock a command typed now should read / change. null = real world (everyone). */
    public static String commandKey() {
        ServerPlayer p = Ctx.TYPIST.get();
        if (p == null) return null;
        return Vis.viewKey(p.getUUID());
    }

    public static void sendTime(ServerPlayer p) {
        ServerLevel l = p.level();
        p.connection.send(new ClientboundSetTimePacket(l.getGameTime(), l.getDayTime(), l.getGameRules().get(GameRules.ADVANCE_TIME)));
    }

    public static void sendTimeAll() {
        for (ServerPlayer p : Vis.online()) sendTime(p);
    }

    // ------------------------------------------------------------------ weather

    public static Cfg.Weather weather(String key) {
        if (key == null) return null;
        return Cfg.state.weather.computeIfAbsent(key, k -> {
            Cfg.Weather w = new Cfg.Weather();
            MinecraftServer s = Vis.server;
            if (s != null) {
                ServerLevel o = s.overworld();
                w.raining = o.getLevelData().isRaining();
                w.thundering = o.getLevelData().isThundering();
                w.rainLevel = o.getRainLevel(1.0F);
                w.thunderLevel = o.getThunderLevel(1.0F);
            }
            return w;
        });
    }

    private static Set<String> keys() {
        Set<String> out = new LinkedHashSet<>(Cfg.settings.groups.keySet());
        for (ServerPlayer p : Vis.online()) {
            String k = Vis.viewKey(p.getUUID());
            if (k != null) out.add(k);
        }
        return out;
    }

    /** Players who are for now shown another view key's weather (clock and everything else stay their own). */
    private static final Map<UUID, String> OVERLAY = new ConcurrentHashMap<>();

    /** Key whose weather this player is shown: the overlay if one is set, else the player's own view. */
    public static String weatherKeyOf(UUID id) {
        String o = OVERLAY.get(id);
        return o != null ? o : Vis.viewKey(id);
    }

    /** Show {@code p} the weather of {@code key} (null = back to their own) and send the full weather state now. */
    public static void setOverlay(ServerPlayer p, String key) {
        String before = OVERLAY.get(p.getUUID());
        if (java.util.Objects.equals(before, key)) return;
        if (key == null) OVERLAY.remove(p.getUUID()); else OVERLAY.put(p.getUUID(), key);
        sendWeather(p);
    }

    public static void dropOverlay(UUID id) {
        OVERLAY.remove(id);
    }

    /** Replaces ServerLevel.advanceWeatherCycle for the overworld. */
    public static void tickWeather(ServerLevel level) {
        boolean advance = level.getGameRules().get(GameRules.ADVANCE_WEATHER);
        for (String key : keys()) {
            Cfg.Weather w = weather(key);
            boolean wasRaining = w.rainLevel > 0.2F;
            if (advance) {
                int i = w.clearTime, j = w.thunderTime, k = w.rainTime;
                boolean th = w.thundering, ra = w.raining;
                if (i > 0) {
                    i--;
                    j = th ? 0 : 1;
                    k = ra ? 0 : 1;
                    th = false;
                    ra = false;
                } else {
                    if (j > 0) {
                        if (--j == 0) th = !th;
                    } else if (th) {
                        j = ServerLevel.THUNDER_DURATION.sample(RANDOM);
                    } else {
                        j = THUNDER_DELAY.sample(RANDOM);
                    }
                    if (k > 0) {
                        if (--k == 0) ra = !ra;
                    } else if (ra) {
                        k = ServerLevel.RAIN_DURATION.sample(RANDOM);
                    } else {
                        k = ServerLevel.RAIN_DELAY.sample(RANDOM);
                    }
                }
                w.thunderTime = j;
                w.rainTime = k;
                w.clearTime = i;
                w.thundering = th;
                w.raining = ra;
            }
            float oT = w.thunderLevel, oR = w.rainLevel;
            w.thunderLevel = Mth.clamp(w.thunderLevel + (w.thundering ? 0.01F : -0.01F), 0.0F, 1.0F);
            w.rainLevel = Mth.clamp(w.rainLevel + (w.raining ? 0.01F : -0.01F), 0.0F, 1.0F);
            boolean isRaining = w.rainLevel > 0.2F;
            List<ServerPlayer> all = viewers(key);
            if (oR != w.rainLevel) send(all, level, ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, w.rainLevel, true);
            if (oT != w.thunderLevel) send(all, level, ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, w.thunderLevel, true);
            if (wasRaining != isRaining) {
                send(all, level, wasRaining ? ClientboundGameEventPacket.STOP_RAINING : ClientboundGameEventPacket.START_RAINING, 0.0F, false);
                send(all, level, ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, w.rainLevel, false);
                send(all, level, ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, w.thunderLevel, false);
            }
        }
        // the real world stays clear
        if (level.getLevelData().isRaining() || level.getLevelData().isThundering()) {
            level.setWeatherParameters(0, 0, false, false);
        }
        level.setRainLevel(0.0F);
        level.setThunderLevel(0.0F);
    }

    private static List<ServerPlayer> viewers(String key) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : Vis.online()) if (Objects.equals(weatherKeyOf(p.getUUID()), key)) out.add(p);
        return out;
    }

    private static void send(List<ServerPlayer> to, ServerLevel level, ClientboundGameEventPacket.Type type, float v, boolean sameDim) {
        Ctx.OWN_WEATHER.set(true);
        try {
            for (ServerPlayer p : to) {
                if (sameDim && p.level() != level) continue;
                p.connection.send(new ClientboundGameEventPacket(type, v));
            }
        } finally {
            Ctx.OWN_WEATHER.set(false);
        }
    }

    /** Full weather state for one player (join, respawn, group change, reveal). */
    public static void sendWeather(ServerPlayer p) {
        String key = weatherKeyOf(p.getUUID());
        Cfg.Weather w = weather(key);
        boolean rain = w != null && w.rainLevel > 0.2F;
        Ctx.OWN_WEATHER.set(true);
        try {
            p.connection.send(new ClientboundGameEventPacket(rain ? ClientboundGameEventPacket.START_RAINING : ClientboundGameEventPacket.STOP_RAINING, 0.0F));
            p.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, w == null ? 0.0F : w.rainLevel));
            p.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, w == null ? 0.0F : w.thunderLevel));
        } finally {
            Ctx.OWN_WEATHER.set(false);
        }
    }

    /** /weather typed by a player: only that player's group. From the server: every group. */
    public static void setWeather(int clear, int time, boolean rain, boolean thunder) {
        String key = commandKey();
        List<String> targets = new ArrayList<>();
        if (key != null) targets.add(key); else targets.addAll(keys());
        for (String k : targets) {
            Cfg.Weather w = weather(k);
            w.clearTime = clear;
            w.rainTime = time;
            w.thunderTime = time;
            w.raining = rain;
            w.thundering = thunder;
        }
        Cfg.saveState();
    }

    // ------------------------------------------------------------------ beds

    /** Can someone of this view sleep now (classic thresholds: clear 12542..23459, rain 12010..23991, thunder always). */
    public static boolean darkFor(ServerPlayer p) {
        String key = Vis.viewKey(p.getUUID());
        Level level = p.level();
        if (level.dimensionType().hasFixedTime()) return false;
        long t = Math.floorMod(level.getDayTime() + offset(key), 24000L);
        Cfg.Weather w = weather(key);
        boolean thunder = w != null && w.thunderLevel > 0.9F;
        boolean rain = w != null && w.rainLevel > 0.2F;
        if (thunder) return true;
        if (rain) return t >= 12010 && t <= 23991;
        return t >= 12542 && t <= 23459;
    }

    private static final Map<String, int[]> LAST = new HashMap<>();

    /** H13: count sleepers per view key, announce per key (replaces ServerLevel.updateSleepingPlayerList). */
    public static void updateSleep(ServerLevel level) {
        int pct = level.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
        if (pct > 100) return;
        if (level.getServer().isSingleplayer() && !level.getServer().isPublished()) return;
        Map<String, List<ServerPlayer>> byKey = bucket(level);
        for (var en : byKey.entrySet()) {
            int active = 0, sleeping = 0;
            for (ServerPlayer p : en.getValue()) {
                if (p.isSpectator()) continue;
                active++;
                if (p.isSleeping()) sleeping++;
            }
            String mk = level.dimension().identifier() + "|" + en.getKey();
            int[] last = LAST.getOrDefault(mk, new int[]{0, 0});
            boolean changed = (last[1] > 0 || sleeping > 0) && (last[0] != active || last[1] != sleeping);
            LAST.put(mk, new int[]{active, sleeping});
            if (!changed) continue;
            int needed = Math.max(1, Mth.ceil(active * pct / 100.0F));
            Component c = sleeping >= needed
                ? Component.translatable("sleep.skipping_night")
                : Component.translatable("sleep.players_sleeping", sleeping, needed);
            for (ServerPlayer p : en.getValue()) p.displayClientMessage(c, true);
        }
    }

    /** H13: per view key, skip the night of that key only (replaces the check in ServerLevel.tick). */
    public static void tickSleep(ServerLevel level) {
        int pct = level.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
        for (var en : bucket(level).entrySet()) {
            String key = en.getKey();
            if (key.isEmpty()) continue;
            int active = 0, sleeping = 0, deep = 0;
            for (ServerPlayer p : en.getValue()) {
                if (p.isSpectator()) continue;
                active++;
                if (p.isSleeping()) sleeping++;
                if (p.isSleepingLongEnough()) deep++;
            }
            int needed = Math.max(1, Mth.ceil(active * pct / 100.0F));
            if (sleeping < needed || deep < needed) continue;
            if (level.getGameRules().get(GameRules.ADVANCE_TIME)) {
                long t = dayTime(key) + 24000L;
                long target = t - t % 24000L;
                setOffset(key, offset(key) + (target - dayTime(key)));
            }
            String mk = level.dimension().identifier() + "|" + key;
            LAST.remove(mk);
            for (ServerPlayer p : en.getValue()) if (p.isSleeping()) p.stopSleepInBed(false, false);
            Cfg.Weather w = weather(key);
            if (level.getGameRules().get(GameRules.ADVANCE_WEATHER) && w.rainLevel > 0.2F) {
                w.rainTime = 0;
                w.raining = false;
                w.thunderTime = 0;
                w.thundering = false;
            }
            Cfg.saveState();
            for (ServerPlayer p : viewers(key)) sendTime(p);
            Log.info("night skipped for " + key);
        }
    }

    private static Map<String, List<ServerPlayer>> bucket(ServerLevel level) {
        Map<String, List<ServerPlayer>> out = new HashMap<>();
        for (ServerPlayer p : level.players()) {
            if (Vis.isDirector(p)) continue;
            String key = Vis.viewKey(p.getUUID());
            out.computeIfAbsent(key == null ? "" : key, k -> new ArrayList<>()).add(p);
        }
        return out;
    }

}
