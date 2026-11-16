package net.pvsync;

import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** Per-thread context used by the mixins. */
public final class Ctx {
    private Ctx() {}

    /** Player whose typed command is running on this thread. */
    public static final ThreadLocal<ServerPlayer> TYPIST = new ThreadLocal<>();
    /** Player a text with selectors is being made for (title / tellraw / sign ... for this one receiver). */
    public static final ThreadLocal<ServerPlayer> RESOLVE_FOR = new ThreadLocal<>();
    /** Player whose death is being handled on this thread. */
    public static final ThreadLocal<ServerPlayer> DYING = new ThreadLocal<>();
    /** Owner of the thing (wolf, arrow...) that killed the dying player, if a player. */
    public static final ThreadLocal<java.util.UUID> KILLER_OWNER = new ThreadLocal<>();
    /** Player whose bed / sleep check is running on this thread. */
    public static final ThreadLocal<ServerPlayer> SLEEPER = new ThreadLocal<>();
    /** Send without rewriting (packets already made for this one receiver). */
    public static final ThreadLocal<Boolean> RAW = ThreadLocal.withInitial(() -> false);
    /** Weather packets we made ourselves for one group. */
    public static final ThreadLocal<Boolean> OWN_WEATHER = ThreadLocal.withInitial(() -> false);
    /** Inside a resync: removals of things the receiver knew until now must go through. */
    public static final ThreadLocal<Boolean> RESYNC = ThreadLocal.withInitial(() -> false);
    /** Host name of the login being checked. */
    public static final ThreadLocal<String> LOGIN_HOST = new ThreadLocal<>();

    public static final class Capture {
        public final ServerPlayer player;
        public final List<Packet<?>> packets = new ArrayList<>();

        public Capture(ServerPlayer player) { this.player = player; }
    }

    /** Collect (instead of send) packets going to one player, to send them later in one bundle. */
    public static final ThreadLocal<Capture> CAPTURE = new ThreadLocal<>();
}
