package net.pvsync;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Simple Voice Chat parts that must load even when SVC is not installed (no SVC classes referenced directly). */
public final class VoiceWire {
    private VoiceWire() {}

    /** voice group id -> our group of the person who made it. */
    static final Map<UUID, String> GROUP_OWNER = new ConcurrentHashMap<>();

    static boolean present() {
        return FabricLoader.getInstance().isModLoaded("voicechat");
    }

    static boolean groupVisible(UUID voiceGroup, UUID observer) {
        String g = GROUP_OWNER.get(voiceGroup);
        if (g == null) return true;
        if (g.startsWith("u:")) return Vis.isDirector(observer) || Vis.revealed() || g.equals("u:" + observer);
        return Vis.ownedVisible(g, observer);
    }



    private static final String NET = "de.maxhenkel.voicechat.net.";
    private static final Map<String, Method> M = new ConcurrentHashMap<>();

    private static Object call(Object o, String name) throws ReflectiveOperationException {
        Method m = M.get(o.getClass().getName() + "#" + name);
        if (m == null) {
            m = o.getClass().getMethod(name);
            m.setAccessible(true);
            M.put(o.getClass().getName() + "#" + name, m);
        }
        return m.invoke(o);
    }

    /** Safety net on the wire: SVC state / group packets about someone the receiver may not know. */
    public static List<Packet<?>> rewritePayload(ClientboundCustomPayloadPacket p, ServerPlayer to) {
        Object pl = p.payload();
        String cn = pl.getClass().getName();
        if (!cn.startsWith(NET)) return null;
        try {
            UUID o = to.getUUID();
            switch (cn.substring(NET.length())) {
                case "PlayerStatePacket" -> {
                    Object st = call(pl, "getPlayerState");
                    UUID id = (UUID) call(st, "getUuid");
                    return Vis.isKnownPlayer(id) && !Vis.real(id, o) ? List.of() : null;
                }
                case "PlayerStatesPacket" -> {
                    @SuppressWarnings("unchecked")
                    Collection<Object> all = (Collection<Object>) call(pl, "getPlayerStates");
                    List<Object> keep = new ArrayList<>();
                    for (Object st : all) {
                        UUID id = (UUID) call(st, "getUuid");
                        if (!Vis.isKnownPlayer(id) || Vis.real(id, o)) keep.add(st);
                    }
                    if (keep.size() == all.size()) return null;
                    Constructor<?> c = pl.getClass().getConstructor(Collection.class);
                    Object np = c.newInstance(keep);
                    return List.of(new ClientboundCustomPayloadPacket((net.minecraft.network.protocol.common.custom.CustomPacketPayload) np));
                }
                case "RemovePlayerStatePacket" -> {
                    if (Ctx.RESYNC.get()) return null;
                    UUID id = (UUID) call(pl, "getId");
                    return Vis.isKnownPlayer(id) && !Vis.real(id, o) ? List.of() : null;
                }
                case "AddGroupPacket" -> {
                    Object g = call(pl, "getGroup");
                    UUID id = (UUID) call(g, "getId");
                    return groupVisible(id, o) ? null : List.of();
                }
                case "RemoveGroupPacket" -> {
                    if (Ctx.RESYNC.get()) return null;
                    UUID id = (UUID) call(pl, "getGroupId");
                    return groupVisible(id, o) ? null : List.of();
                }
                default -> {
                    return null;
                }
            }
        } catch (Throwable t) {
            Log.warn("voice packet check failed (" + cn + "): " + t);
            return null;
        }
    }

    // ------------------------------------------------------------------ resync after reveal / back / group change

    /** SVC method names, joined at run time. */
    private static String svcName(String a, String b) {
        return a + b;
    }

    static void resync(Resync.Snapshot before) {
        if (!present()) return;
        Ctx.RESYNC.set(true);
        try {
            Class<?> vc = Class.forName("de.maxhenkel.voicechat.Voicechat");
            Object events = vc.getField("SERVER").get(null);
            if (events == null) return;
            Object vs = events.getClass().getMethod("getServer").invoke(events);
            if (vs == null) return;
            Object psm = vs.getClass().getMethod("getPlayerStateManager").invoke(vs);
            Object gm = vs.getClass().getMethod("getGroupManager").invoke(vs);
            if (psm == null) return;
            Method off = psm.getClass().getMethod(svcName("onPlayerH", "ide"), ServerPlayer.class, ServerPlayer.class);
            Method on = psm.getClass().getMethod(svcName("onPlayerS", "how"), ServerPlayer.class, ServerPlayer.class);
            List<ServerPlayer> all = Vis.online();
            for (ServerPlayer o : all) {
                Map<UUID, Resync.Seen> old = before.pairs.getOrDefault(o.getUUID(), Map.of());
                for (ServerPlayer a : all) {
                    if (a == o) continue;
                    Resync.Seen w = old.get(a.getUUID());
                    if (w == null) continue;
                    boolean was = w.state() == Vis.See.REAL;
                    boolean now = Vis.real(a.getUUID(), o.getUUID());
                    if (was == now) continue;
                    // SVC: (target, observer) sends / removes target's voice state for observer
                    if (now) on.invoke(psm, a, o); else off.invoke(psm, a, o);
                }
            }
            if (gm != null) {
                @SuppressWarnings("unchecked")
                Map<UUID, Object> groups = (Map<UUID, Object>) gm.getClass().getMethod("getGroups").invoke(gm);
                Class<?> net = Class.forName(NET + "NetManager");
                Method send = null;
                for (Method m : net.getMethods()) {
                    if (m.getName().equals("sendToClient") && java.lang.reflect.Modifier.isStatic(m.getModifiers())) send = m;
                }
                Class<?> add = Class.forName(NET + "AddGroupPacket");
                Class<?> rem = Class.forName(NET + "RemoveGroupPacket");
                for (var en : groups.entrySet()) {
                    String g = GROUP_OWNER.get(en.getKey());
                    if (g == null || send == null) continue;
                    Object clientGroup = en.getValue().getClass().getMethod("toClientGroup").invoke(en.getValue());
                    for (ServerPlayer o : all) {
                        boolean now = groupVisible(en.getKey(), o.getUUID());
                        Object pk = now
                            ? add.getConstructor(clientGroup.getClass()).newInstance(clientGroup)
                            : rem.getConstructor(UUID.class).newInstance(en.getKey());
                        send.invoke(null, o, pk);
                    }
                }
            }
        } catch (Throwable t) {
            Log.warn("voice resync failed: " + t);
        } finally {
            Ctx.RESYNC.set(false);
        }
    }
}
