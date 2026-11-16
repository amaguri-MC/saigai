package net.pvsync;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who sees what. EVERY decision in this mod goes through {@link #see(UUID, UUID)} or the helpers built on it
 * (04 "全部の判定を 1 か所に通す").
 */
public final class Vis {
    private Vis() {}

    public enum See { HIDDEN, ALIAS, REAL }

    public static volatile MinecraftServer server;

    /** online players: uuid -> group key (missing key = no group). */
    private static final Map<UUID, String> GROUP = new ConcurrentHashMap<>();
    private static final Set<UUID> ONLINE = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> DIRECTORS = ConcurrentHashMap.newKeySet();

    // ------------------------------------------------------------------ resolution

    public static String normHost(String host) {
        if (host == null) return "";
        int z = host.indexOf('\0');
        if (z >= 0) host = host.substring(0, z);
        host = host.trim().toLowerCase(Locale.ROOT);
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        return host;
    }

    static boolean matches(String entry, UUID id, String name) {
        if (entry == null) return false;
        String e = entry.trim();
        if (name != null && e.equalsIgnoreCase(name)) return true;
        String compact = e.replace("-", "").toLowerCase(Locale.ROOT);
        return id != null && compact.equals(id.toString().replace("-", ""));
    }

    /** null = belongs to no group. */
    public static String groupForHost(String host) {
        String h = normHost(host);
        if (h.isEmpty()) return null;
        for (var en : Cfg.settings.groups.entrySet()) {
            for (String x : en.getValue().hosts) {
                if (normHost(x).equals(h)) return en.getKey();
            }
        }
        return null;
    }

    /** 03 [G]: members table first, then the host the player connected with. */
    public static String resolveGroup(UUID id, String name, String host) {
        for (var en : Cfg.settings.groups.entrySet()) {
            for (String m : en.getValue().members) if (matches(m, id, name)) return en.getKey();
        }
        for (String m : Cfg.settings.noGroup) if (matches(m, id, name)) return null;
        return groupForHost(host);
    }

    public static boolean configDirector(UUID id, String name) {
        for (String d : Cfg.settings.directors) if (matches(d, id, name)) return true;
        return false;
    }

    // ------------------------------------------------------------------ runtime table

    public static void join(UUID id, String name, String group, boolean director) {
        ONLINE.add(id);
        if (group == null) GROUP.remove(id); else GROUP.put(id, group);
        if (director) DIRECTORS.add(id); else DIRECTORS.remove(id);
        Cfg.state.known.put(id.toString(), new Cfg.Known(name, group));
    }

    public static void leave(UUID id) {
        ONLINE.remove(id);
        Sky.dropOverlay(id);
    }

    public static void setGroup(UUID id, String name, String group) {
        if (group == null) GROUP.remove(id); else GROUP.put(id, group);
        Cfg.state.known.put(id.toString(), new Cfg.Known(name, group));
    }

    public static void setDirector(UUID id, boolean on) {
        if (on) DIRECTORS.add(id); else DIRECTORS.remove(id);
    }

    public static String group(UUID id) {
        if (id == null) return null;
        if (ONLINE.contains(id)) return GROUP.get(id);
        Cfg.Known k = Cfg.state.known.get(id.toString());
        return k == null ? null : k.group;
    }

    public static boolean isDirector(UUID id) {
        if (id == null) return false;
        if (DIRECTORS.contains(id)) return true;
        if (ONLINE.contains(id)) return false;
        Cfg.Known k = Cfg.state.known.get(id.toString());
        return k != null && configDirector(id, k.name);
    }

    public static boolean isDirector(ServerPlayer p) {
        return p != null && DIRECTORS.contains(p.getUUID());
    }

    /** Is this uuid a player we know about (online now, or seen before)? */
    public static boolean isKnownPlayer(UUID id) {
        return id != null && (ONLINE.contains(id) || Cfg.state.known.containsKey(id.toString()));
    }

    public static boolean revealed() {
        return Cfg.settings.revealed;
    }

    // ------------------------------------------------------------------ THE decision

    /** How {@code observer} sees {@code actor}. */
    public static See see(UUID actor, UUID observer) {
        if (actor == null || observer == null) return See.REAL;
        if (actor.equals(observer)) return See.REAL;
        if (isDirector(observer)) return See.REAL;
        if (isDirector(actor)) return See.HIDDEN;
        if (revealed()) return See.REAL;
        String ga = group(actor);
        String go = group(observer);
        if (ga != null && ga.equals(go)) return See.REAL;
        return See.ALIAS;
    }

    public static See see(ServerPlayer actor, ServerPlayer observer) {
        return see(actor.getUUID(), observer.getUUID());
    }

    public static boolean real(UUID actor, UUID observer) {
        return see(actor, observer) == See.REAL;
    }

    /** Things made by a group (command bossbars, teams, objectives): visible to observer? owner null = everybody. */
    public static boolean ownedVisible(String ownerGroup, UUID observer) {
        if (ownerGroup == null) return true;
        if (isDirector(observer)) return true;
        if (revealed()) return true;
        return ownerGroup.equals(group(observer));
    }

    /** Which group's clock / sky this observer gets. null = the real world. */
    public static String viewKey(UUID observer) {
        if (isDirector(observer)) return Cfg.settings.directorView;
        if (revealed()) return Cfg.settings.revealTimeFrom;
        return group(observer);
    }

    /** Key that aliases are shared by (03 [V] "組ごとの仮の UUID"). */
    public static String aliasKey(UUID observer) {
        String g = group(observer);
        return g != null ? "g:" + g : "u:" + observer;
    }

    // ------------------------------------------------------------------ helpers

    public static List<ServerPlayer> online() {
        MinecraftServer s = server;
        return s == null ? List.of() : new ArrayList<>(s.getPlayerList().getPlayers());
    }

    /** Lower-case names of every known player that observer must not learn about. */
    public static Set<String> hiddenNames(UUID observer) {
        Set<String> out = new HashSet<>();
        for (ServerPlayer p : online()) {
            if (see(p.getUUID(), observer) != See.REAL) out.add(p.getGameProfile().name().toLowerCase(Locale.ROOT));
        }
        for (var en : Cfg.state.known.entrySet()) {
            try {
                UUID id = UUID.fromString(en.getKey());
                if (en.getValue().name != null && see(id, observer) != See.REAL) out.add(en.getValue().name.toLowerCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        // directors written by name in the settings, even before they ever joined
        if (!isDirector(observer)) {
            for (String d : Cfg.settings.directors) {
                if (d != null && !d.isBlank() && !d.contains("-") && d.length() <= 16) out.add(d.trim().toLowerCase(Locale.ROOT));
            }
        }
        // a name that also belongs to someone observer may see stays visible
        for (ServerPlayer p : online()) {
            if (see(p.getUUID(), observer) == See.REAL) out.remove(p.getGameProfile().name().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    public static Set<UUID> hiddenUuids(UUID observer) {
        Set<UUID> out = new HashSet<>();
        for (ServerPlayer p : online()) if (see(p.getUUID(), observer) != See.REAL) out.add(p.getUUID());
        for (String k : Cfg.state.known.keySet()) {
            try {
                UUID id = UUID.fromString(k);
                if (see(id, observer) != See.REAL) out.add(id);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return out;
    }

    public static boolean nameHidden(String name, UUID observer) {
        return name != null && hiddenNames(observer).contains(name.toLowerCase(Locale.ROOT));
    }

    /** The player who typed the command that is running now (null = server / command block / director). */
    public static ServerPlayer cmdObserver() {
        ServerPlayer p = Ctx.TYPIST.get();
        if (p == null || isDirector(p)) return null;
        return p;
    }

    /** Whose view selectors are evaluated for: the receiver of a text being made, else the person who typed. */
    public static ServerPlayer selectorObserver() {
        ServerPlayer r = Ctx.RESOLVE_FOR.get();
        if (r != null) return isDirector(r) ? null : r;
        return cmdObserver();
    }

    public static int groupIndex(String g) {
        int i = 0;
        for (String k : Cfg.settings.groups.keySet()) {
            if (k.equals(g)) return i;
            i++;
        }
        return i;
    }
}
