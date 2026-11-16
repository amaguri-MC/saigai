package net.pvsync;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/** Which group made a command bossbar / team / objective (H17, H18). Made by the server = everybody's. */
public final class Owners {
    private Owners() {}

    /** bossbar packet uuid -> owner group (rebuilt each start from the bar id map). */
    private static final Map<UUID, String> BAR_UUID = new ConcurrentHashMap<>();

    public static void record(String key) {
        var p = Vis.cmdObserver();
        if (p == null) return;
        String g = Vis.group(p.getUUID());
        if (g == null) g = "u:" + p.getUUID();
        Cfg.state.owners.put(key, g);
        Cfg.saveState();
        Log.info("made " + key + " (group " + g + ")");
    }

    public static void forget(String key) {
        if (Cfg.state.owners.remove(key) != null) Cfg.saveState();
    }

    public static String owner(String key) {
        return Cfg.state.owners.get(key);
    }

    public static boolean visible(String key, UUID observer) {
        String g = owner(key);
        if (g == null) return true;
        if (g.startsWith("u:")) {
            return Vis.isDirector(observer) || Vis.revealed() || g.equals("u:" + observer);
        }
        return Vis.ownedVisible(g, observer);
    }

    public static boolean teamVisible(String team, UUID observer) {
        return team == null || visible("team:" + team, observer);
    }

    public static boolean objVisible(String obj, UUID observer) {
        return obj == null || visible("obj:" + obj, observer);
    }

    public static void barUuid(UUID packetId, String barId) {
        BAR_UUID.put(packetId, barId);
    }

    public static boolean barVisible(UUID packetId, UUID observer) {
        String id = BAR_UUID.get(packetId);
        return id == null || visible("bar:" + id, observer);
    }

    public static boolean barIdVisible(String barId, UUID observer) {
        return visible("bar:" + barId, observer);
    }
}
