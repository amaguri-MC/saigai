package net.pvsync;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Turns every receiver's screen from one visibility state to another in one go (reveal, back, group change,
 * director on/off). Per receiver everything is sent as ONE bundle, which the game applies within a single frame,
 * so a body is never missing for a frame (03 [R]).
 */
public final class Resync {
    private Resync() {}

    public record Seen(Vis.See state, UUID uuid) {}

    public static final class Snapshot {
        final Map<UUID, Map<UUID, Seen>> pairs = new HashMap<>();
        final Map<UUID, String> view = new HashMap<>();
        final Map<UUID, Set<String>> owned = new HashMap<>();
        final Map<UUID, Boolean> director = new HashMap<>();
    }

    public static Snapshot snapshot() {
        Snapshot s = new Snapshot();
        List<ServerPlayer> all = Vis.online();
        for (ServerPlayer o : all) {
            Map<UUID, Seen> m = new HashMap<>();
            for (ServerPlayer a : all) {
                if (a == o) continue;
                m.put(a.getUUID(), new Seen(Vis.see(a.getUUID(), o.getUUID()), Alias.shownUuid(a.getUUID(), o.getUUID())));
            }
            s.pairs.put(o.getUUID(), m);
            s.view.put(o.getUUID(), Vis.viewKey(o.getUUID()));
            s.owned.put(o.getUUID(), visibleOwned(o.getUUID()));
            s.director.put(o.getUUID(), Vis.isDirector(o));
        }
        return s;
    }

    private static Set<String> visibleOwned(UUID o) {
        Set<String> out = new HashSet<>();
        for (String k : Cfg.state.owners.keySet()) if (Owners.visible(k, o)) out.add(k);
        return out;
    }

    public static void apply(Snapshot before) {
        MinecraftServer server = Vis.server;
        if (server == null) return;
        List<ServerPlayer> all = Vis.online();
        for (ServerPlayer o : all) {
            try {
                applyOne(server, before, o, all);
            } catch (Throwable t) {
                Log.warn("resync failed for " + o.getGameProfile().name() + ": " + t);
            }
        }
        // locator bar: rebuild every connection with the new rules
        for (ServerPlayer a : all) {
            try {
                a.level().getWaypointManager().remakeConnections(a);
            } catch (Throwable t) {
                Log.warn("waypoint resync: " + t);
            }
        }
        VoiceWire.resync(before);
        Cfg.saveState();
    }

    private static void applyOne(MinecraftServer server, Snapshot before, ServerPlayer o, List<ServerPlayer> all) {
        UUID ou = o.getUUID();
        Map<UUID, Seen> old = before.pairs.getOrDefault(ou, Map.of());
        List<Packet<?>> out = new ArrayList<>();
        ServerScoreboard sb = server.getScoreboard();

        for (ServerPlayer a : all) {
            if (a == o) continue;
            Seen was = old.get(a.getUUID());
            if (was == null) continue;
            Seen now = new Seen(Vis.see(a.getUUID(), ou), Alias.shownUuid(a.getUUID(), ou));
            if (was.equals(now)) continue;
            transition(o, a, was, now, out, sb);
        }

        // objects made by a group (teams / objectives / command bossbars)
        Set<String> wasOwned = before.owned.getOrDefault(ou, Set.of());
        Set<String> nowOwned = visibleOwned(ou);
        for (String k : Cfg.state.owners.keySet()) {
            boolean w = wasOwned.contains(k), n = nowOwned.contains(k);
            if (w == n) continue;
            ownedChange(server, sb, o, k, n, out);
        }

        if (!out.isEmpty()) sendBundle(o, out);

        // commands tree (director on / off)
        boolean wasDir = before.director.getOrDefault(ou, false);
        if (wasDir != Vis.isDirector(o)) server.getCommands().sendCommands(o);

        // clock and sky
        if (!Objects.equals(before.view.get(ou), Vis.viewKey(ou))) {
            Sky.sendTime(o);
            Sky.sendWeather(o);
        }
    }

    private static void transition(ServerPlayer o, ServerPlayer a, Seen was, Seen now, List<Packet<?>> out, ServerScoreboard sb) {
        ChunkMap.TrackedEntity te = a.level().getChunkSource().chunkMap.entityMap.get(a.getId());
        String name = a.getGameProfile().name();
        if (was.state() != Vis.See.HIDDEN) {
            if (te != null && te.seenBy.contains(o.connection)) out.addAll(capture(o, () -> te.removePlayer(o), false));
            out.add(new ClientboundPlayerInfoRemovePacket(List.of(was.uuid())));
            if (was.state() == Vis.See.REAL) {
                PlayerTeam team = a.getTeam();
                if (team != null) out.add(ClientboundSetPlayerTeamPacket.createPlayerPacket(team, name, ClientboundSetPlayerTeamPacket.Action.REMOVE));
                out.add(new ClientboundResetScorePacket(name, null));
            }
        }
        if (now.state() != Vis.See.HIDDEN) {
            List<Packet<?>> add = rewritten(o, ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(a)));
            out.addAll(add);
            if (now.state() == Vis.See.REAL) {
                PlayerTeam team = a.getTeam();
                if (team != null && Owners.teamVisible(team.getName(), o.getUUID())) {
                    out.add(ClientboundSetPlayerTeamPacket.createPlayerPacket(team, name, ClientboundSetPlayerTeamPacket.Action.ADD));
                }
                for (Objective obj : displayed(sb)) {
                    if (!Owners.objVisible(obj.getName(), o.getUUID())) continue;
                    ReadOnlyScoreInfo info = sb.getPlayerScoreInfo(ScoreHolder.forNameOnly(name), obj);
                    if (info != null) {
                        out.add(new ClientboundSetScorePacket(name, obj.getName(), info.value(),
                            java.util.Optional.ofNullable(info instanceof net.minecraft.world.scores.Score sc ? sc.display() : null), java.util.Optional.ofNullable(info.numberFormat())));
                    }
                }
            }
            if (te != null) out.addAll(capture(o, () -> te.updatePlayer(o), true));
        }
    }

    private static List<Objective> displayed(ServerScoreboard sb) {
        List<Objective> out = new ArrayList<>();
        for (DisplaySlot s : DisplaySlot.values()) {
            Objective obj = sb.getDisplayObjective(s);
            if (obj != null && !out.contains(obj)) out.add(obj);
        }
        return out;
    }

    private static void ownedChange(MinecraftServer server, ServerScoreboard sb, ServerPlayer o, String key, boolean show, List<Packet<?>> out) {
        if (key.startsWith("team:")) {
            PlayerTeam t = sb.getPlayerTeam(key.substring(5));
            if (t == null) return;
            out.add(show ? ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(t, true) : ClientboundSetPlayerTeamPacket.createRemovePacket(t));
            if (show) out.addAll(rewritten(o, out.remove(out.size() - 1)));
        } else if (key.startsWith("obj:")) {
            Objective obj = sb.getObjective(key.substring(4));
            if (obj == null) return;
            if (show) {
                if (displayed(sb).contains(obj)) {
                    for (Packet<?> p : sb.getStartTrackingPackets(obj)) out.addAll(rewritten(o, p));
                }
            } else {
                out.add(new ClientboundSetObjectivePacket(obj, ClientboundSetObjectivePacket.METHOD_REMOVE));
            }
        } else if (key.startsWith("bar:")) {
            var id = net.minecraft.resources.Identifier.tryParse(key.substring(4));
            CustomBossEvent bar = id == null ? null : server.getCustomBossEvents().get(id);
            if (bar == null || !bar.getPlayers().contains(o) || !bar.isVisible()) return;
            out.add(show ? ClientboundBossEventPacket.createAddPacket(bar) : ClientboundBossEventPacket.createRemovePacket(bar.getId()));
        }
    }

    /** Packets the game would send to o while running r. */
    private static List<Packet<?>> capture(ServerPlayer o, Runnable r, boolean rewrite) {
        Ctx.Capture cap = new Ctx.Capture(o);
        Ctx.Capture prev = Ctx.CAPTURE.get();
        Ctx.CAPTURE.set(cap);
        try {
            r.run();
        } finally {
            Ctx.CAPTURE.set(prev);
        }
        List<Packet<?>> out = new ArrayList<>();
        for (Packet<?> p : cap.packets) {
            if (p instanceof ClientboundBundlePacket b) {
                for (Packet<?> sub : b.subPackets()) out.addAll(rewrite ? rewritten(o, sub) : List.of(sub));
            } else {
                out.addAll(rewrite ? rewritten(o, p) : List.of(p));
            }
        }
        return out;
    }

    private static List<Packet<?>> rewritten(ServerPlayer o, Packet<?> p) {
        List<Packet<?>> r = Rewriter.rewrite(p, o);
        if (r == null) return List.of(p);
        List<Packet<?>> flat = new ArrayList<>();
        for (Packet<?> x : r) {
            if (x instanceof ClientboundBundlePacket b) for (Packet<?> sub : b.subPackets()) flat.add(sub);
            else flat.add(x);
        }
        return flat;
    }

    @SuppressWarnings("unchecked")
    private static void sendBundle(ServerPlayer o, List<Packet<?>> packets) {
        Ctx.RAW.set(true);
        try {
            for (int i = 0; i < packets.size(); i += 4000) {
                List<Packet<? super ClientGamePacketListener>> part = new ArrayList<>();
                for (Packet<?> p : packets.subList(i, Math.min(packets.size(), i + 4000))) part.add((Packet<? super ClientGamePacketListener>) p);
                o.connection.send(new ClientboundBundlePacket(part));
            }
        } finally {
            Ctx.RAW.set(false);
        }
    }
}
