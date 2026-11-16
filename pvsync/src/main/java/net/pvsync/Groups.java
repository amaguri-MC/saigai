package net.pvsync;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/** Group bookkeeping that needs the game: join, server list answer, the per-viewer "no name" team, spawn points. */
public final class Groups {
    private Groups() {}

    /** Name of the team each receiver is told about, holding every stand-in name (H01). Random each start. */
    public static final String NAMELESS_TEAM = "t" + HexFormat.of().formatHex(new byte[]{
        (byte) new Random().nextInt(), (byte) new Random().nextInt(), (byte) new Random().nextInt(), (byte) new Random().nextInt()});

    public static void onJoin(ServerPlayer p, String host) {
        UUID id = p.getUUID();
        String name = p.getGameProfile().name();
        String g = Vis.resolveGroup(id, name, host);
        boolean d = Vis.configDirector(id, name);
        Vis.join(id, name, g, d);
        Cfg.saveState();
        if (g == null && !d) Log.warn(name + " joined with host '" + Vis.normHost(host) + "' and belongs to no group (sees only themselves)");
        Log.info(name + " joined: group=" + (g == null ? "none" : g) + (d ? " director" : "") + " host=" + Vis.normHost(host));
    }

    /** Sent to every receiver once: a team that never shows names and holds the stand-in name. */
    public static void sendNamelessTeam(ServerPlayer to) {
        CURRENT_TEAM.put(to.getUUID(), NAMELESS_TEAM);
        PlayerTeam t = namelessTeam(NAMELESS_TEAM);
        Ctx.RAW.set(true);
        try {
            to.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(t, true));
        } finally {
            Ctx.RAW.set(false);
        }
    }

    private static final java.util.Map<UUID, String> CURRENT_TEAM = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.security.SecureRandom RND = new java.security.SecureRandom();

    private static PlayerTeam namelessTeam(String name) {
        Scoreboard sb = new Scoreboard();
        PlayerTeam t = sb.addPlayerTeam(name);
        t.setNameTagVisibility(Team.Visibility.NEVER);
        t.setCollisionRule(Team.CollisionRule.ALWAYS);
        t.setSeeFriendlyInvisibles(false);
        sb.addPlayerToTeam(Alias.NAME, t);
        return t;
    }

    /**
     * Every second the stand-in name moves to a newly made team of the same kind and the old team is removed.
     * The move happens inside one packet, so no frame ever draws a name tag. Why: Flashback rebuilds the world when a
     * replay is opened and drops the teams of the recording's first snapshot; with this, the team is back in the
     * replay at most one second later (otherwise an empty name tag, a thin bar, would be drawn over the other group).
     * A new name each time also keeps the game from writing "team already exists" into the player's own log.
     */
    @SuppressWarnings("unchecked")
    public static void refreshNamelessTeam(ServerPlayer to) {
        String old = CURRENT_TEAM.get(to.getUUID());
        String name = "t" + HexFormat.of().formatHex(new byte[]{(byte) RND.nextInt(), (byte) RND.nextInt(), (byte) RND.nextInt(), (byte) RND.nextInt()});
        PlayerTeam t = namelessTeam(name);
        java.util.List<net.minecraft.network.protocol.Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener>> b = new java.util.ArrayList<>();
        b.add(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(t, true));
        if (old != null) b.add(ClientboundSetPlayerTeamPacket.createRemovePacket(namelessTeam(old)));
        CURRENT_TEAM.put(to.getUUID(), name);
        Ctx.RAW.set(true);
        try {
            to.connection.send(new net.minecraft.network.protocol.game.ClientboundBundlePacket(b));
        } finally {
            Ctx.RAW.set(false);
        }
    }

    public static void sendTabTexts(ServerPlayer to) {
        String g = Vis.group(to.getUUID());
        if (g == null) return;
        Cfg.Group cg = Cfg.settings.groups.get(g);
        if (cg == null || (cg.tabHeader == null && cg.tabFooter == null)) return;
        to.connection.send(new ClientboundTabListPacket(
            Component.literal(cg.tabHeader == null ? "" : cg.tabHeader), Component.literal(cg.tabFooter == null ? "" : cg.tabFooter)));
    }

    public static Component motd(String group) {
        if (group == null) return null;
        Cfg.Group cg = Cfg.settings.groups.get(group);
        if (cg == null || cg.motd == null) return null;
        return Component.literal(cg.motd);
    }

    /** People counted for a group in the server list / full check (directors and no-group people never count). */
    public static List<ServerPlayer> counted(String group) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : Vis.online()) {
            if (Vis.isDirector(p)) continue;
            String g = Vis.group(p.getUUID());
            if (Vis.revealed() ? g != null : (group != null && group.equals(g))) out.add(p);
        }
        out.sort(Comparator.comparing(p -> p.getGameProfile().name().toLowerCase()));
        return out;
    }

    public static int maxPlayers(String group, MinecraftServer s) {
        if (group == null) return s.getMaxPlayers();
        Cfg.Group cg = Cfg.settings.groups.get(group);
        return cg == null ? s.getMaxPlayers() : cg.maxPlayers;
    }

    /** H03: the server list answer for the host name the client used. */
    public static ServerStatus status(MinecraftServer s, ServerStatus base, String host) {
        String g = Vis.groupForHost(host);
        List<ServerPlayer> ps = g == null && !Vis.revealed() ? List.of() : counted(g);
        int max = Vis.revealed() ? s.getMaxPlayers() : maxPlayers(g, s);
        List<NameAndId> sample = new ArrayList<>();
        if (!s.hidesOnlinePlayers()) {
            for (int i = 0; i < Math.min(12, ps.size()); i++) {
                ServerPlayer p = ps.get(i);
                sample.add(p.allowsListing() ? p.nameAndId() : MinecraftServer.ANONYMOUS_PLAYER_PROFILE);
            }
        }
        Component desc = Vis.revealed() ? null : motd(g);
        if (desc == null) desc = base != null ? base.description() : Component.nullToEmpty(s.getMotd());
        return new ServerStatus(desc, Optional.of(new ServerStatus.Players(max, ps.size(), sample)),
            Optional.of(ServerStatus.Version.current()), Optional.ofNullable(s.statusIcon), s.enforceSecureProfile());
    }

    // ------------------------------------------------------------------ H19 spawn

    public static ServerLevel spawnLevel(MinecraftServer s, Cfg.Spawn sp) {
        Identifier id = Identifier.tryParse(sp.dim);
        if (id == null) return null;
        return s.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    public static Cfg.Spawn spawnOf(UUID player) {
        String g = Vis.group(player);
        if (g == null) return null;
        Cfg.Group cg = Cfg.settings.groups.get(g);
        return cg == null ? null : cg.spawn;
    }

    /** Footing check (企画者の決まり: never air / liquid under the feet). */
    public static boolean solidUnder(ServerLevel level, double x, double y, double z) {
        BlockPos below = BlockPos.containing(x, y - 0.01, z);
        BlockState st = level.getBlockState(below);
        return !st.isAir() && st.getFluidState().isEmpty() && !st.getCollisionShape(level, below).isEmpty();
    }

    public static ResourceKey<Level> dimKey(String dim) {
        Identifier id = Identifier.tryParse(dim);
        return id == null ? Level.OVERWORLD : ResourceKey.create(Registries.DIMENSION, id);
    }
}
