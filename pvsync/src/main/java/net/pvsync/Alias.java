package net.pvsync;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 03 [V]: stand-in UUID / name for a player seen by another group. Random v4 UUIDs, new every server start,
 * one per (player, group of the viewer). The name is empty: it is never drawn and never offered as a suggestion
 * (Brigadier drops a suggestion equal to what was typed, and "" only ever equals the empty input).
 */
public final class Alias {
    private Alias() {}

    public static final String NAME = "";

    private static final Map<String, UUID> FAKE = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> REAL_OF = new ConcurrentHashMap<>();
    private static final Map<UUID, String> KEY_OF = new ConcurrentHashMap<>();

    public static UUID fake(UUID real, String aliasKey) {
        return FAKE.computeIfAbsent(real + "|" + aliasKey, k -> {
            UUID f = UUID.randomUUID();
            REAL_OF.put(f, real);
            KEY_OF.put(f, aliasKey);
            return f;
        });
    }

    public static UUID fakeFor(UUID real, UUID observer) {
        return fake(real, Vis.aliasKey(observer));
    }

    /** Real uuid for a stand-in uuid, only if the stand-in was made for this observer's key. */
    public static UUID realFor(UUID fake, UUID observer) {
        UUID r = REAL_OF.get(fake);
        if (r == null) return null;
        return Vis.aliasKey(observer).equals(KEY_OF.get(fake)) ? r : null;
    }

    public static boolean isFake(UUID id) {
        return REAL_OF.containsKey(id);
    }

    /** The uuid observer's game currently knows actor by (null when the actor is not shown at all). */
    public static UUID shownUuid(UUID actor, UUID observer) {
        return switch (Vis.see(actor, observer)) {
            case REAL -> actor;
            case ALIAS -> fakeFor(actor, observer);
            case HIDDEN -> null;
        };
    }
}
