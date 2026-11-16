package net.pvsync;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundServerDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Rewrites one packet for one receiver, right before it is sent (04 "送る直前で書き換える").
 * Returns null when the packet goes out unchanged, an empty list to drop it, or the packets to send instead.
 */
public final class Rewriter {
    private Rewriter() {}

    public static List<Packet<?>> rewrite(Packet<?> packet, ServerPlayer to) {
        try {
            return rewrite0(packet, to);
        } catch (Throwable t) {
            Log.warn("rewrite failed for " + packet.getClass().getSimpleName() + ": " + t);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Packet<?>> rewrite0(Packet<?> packet, ServerPlayer to) {
        UUID o = to.getUUID();
        if (packet instanceof ClientboundBundlePacket bundle) {
            List<Packet<? super ClientGamePacketListener>> out = new ArrayList<>();
            boolean changed = false;
            for (Packet<? super ClientGamePacketListener> sub : bundle.subPackets()) {
                List<Packet<?>> r = rewrite0(sub, to);
                if (r == null) {
                    out.add(sub);
                } else {
                    changed = true;
                    for (Packet<?> x : r) out.add((Packet<? super ClientGamePacketListener>) x);
                }
            }
            if (!changed) return null;
            if (out.isEmpty()) return List.of();
            return List.of(new ClientboundBundlePacket(out));
        }
        if (packet instanceof ClientboundPlayerInfoUpdatePacket p) return info(p, to);
        if (packet instanceof ClientboundPlayerInfoRemovePacket p) {
            List<UUID> ids = new ArrayList<>();
            boolean changed = false;
            for (UUID id : p.profileIds()) {
                if (!Vis.isKnownPlayer(id)) { ids.add(id); continue; }
                UUID shown = Alias.shownUuid(id, o);
                if (shown == null) { changed = true; continue; }
                if (!shown.equals(id)) changed = true;
                ids.add(shown);
            }
            if (!changed) return null;
            return ids.isEmpty() ? List.of() : List.of(new ClientboundPlayerInfoRemovePacket(ids));
        }
        if (packet instanceof ClientboundAddEntityPacket p) {
            if (p.getType() != EntityType.PLAYER && !Vis.isKnownPlayer(p.getUUID())) return null;
            Vis.See s = Vis.see(p.getUUID(), o);
            if (s == Vis.See.REAL) return null;
            if (s == Vis.See.HIDDEN) return List.of();
            return List.of(new ClientboundAddEntityPacket(p.getId(), Alias.fakeFor(p.getUUID(), o), p.getX(), p.getY(), p.getZ(),
                p.getXRot(), p.getYRot(), p.getType(), p.getData(), p.getMovement(), p.getYHeadRot()));
        }
        if (packet instanceof ClientboundSetEntityDataPacket p) return entityData(p, o);
        if (packet instanceof ClientboundSetPlayerTeamPacket p) {
            if (!Owners.teamVisible(p.getName(), o)) return List.of();
            if (p.getPlayers().isEmpty()) return null;
            Set<String> hidden = Vis.hiddenNames(o);
            List<String> keep = new ArrayList<>();
            for (String n : p.getPlayers()) if (!hidden.contains(n.toLowerCase(Locale.ROOT))) keep.add(n);
            if (keep.size() == p.getPlayers().size()) return null;
            if (keep.isEmpty() && (p.method == 3 || p.method == 4)) return List.of();
            return List.of(new ClientboundSetPlayerTeamPacket(p.getName(), p.method, p.getParameters(), keep));
        }
        if (packet instanceof ClientboundSetScorePacket p) {
            return (!Owners.objVisible(p.objectiveName(), o) || Vis.nameHidden(p.owner(), o)) ? List.of() : null;
        }
        if (packet instanceof ClientboundResetScorePacket p) {
            return (!Owners.objVisible(p.objectiveName(), o) || Vis.nameHidden(p.owner(), o)) ? List.of() : null;
        }
        if (packet instanceof ClientboundSetObjectivePacket p) return Owners.objVisible(p.getObjectiveName(), o) ? null : List.of();
        if (packet instanceof ClientboundSetDisplayObjectivePacket p) return Owners.objVisible(p.getObjectiveName(), o) ? null : List.of();
        if (packet instanceof ClientboundBossEventPacket p) {
            if (!Owners.barVisible(p.id, o)) return List.of();
            return bossName(p, o);
        }
        if (packet instanceof net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket p) {
            Component t = Strip.strip(p.text(), Vis.hiddenUuids(o), Vis.hiddenNames(o));
            return t == null ? null : List.of(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(t));
        }
        if (packet instanceof net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket p) {
            Component t = Strip.strip(p.text(), Vis.hiddenUuids(o), Vis.hiddenNames(o));
            return t == null ? null : List.of(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(t));
        }
        if (packet instanceof net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket p) {
            Component t = Strip.strip(p.text(), Vis.hiddenUuids(o), Vis.hiddenNames(o));
            return t == null ? null : List.of(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(t));
        }
        if (packet instanceof ClientboundTrackedWaypointPacket p) {
            UUID id = p.waypoint().id().left().orElse(null);
            if (id != null && Vis.isKnownPlayer(id) && !Vis.real(id, o)) return List.of();
            return null;
        }
        if (packet instanceof ClientboundSetTimePacket p) {
            long off = Sky.offset(Vis.viewKey(o));
            if (off == 0L) return null;
            return List.of(new ClientboundSetTimePacket(p.gameTime(), p.dayTime() + off, p.tickDayTime()));
        }
        if (packet instanceof ClientboundGameEventPacket p) {
            var t = p.getEvent();
            boolean weather = t == ClientboundGameEventPacket.START_RAINING || t == ClientboundGameEventPacket.STOP_RAINING
                || t == ClientboundGameEventPacket.RAIN_LEVEL_CHANGE || t == ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE;
            if (weather && !Ctx.OWN_WEATHER.get()) return List.of();
            return null;
        }
        if (packet instanceof ClientboundSystemChatPacket p) {
            Set<UUID> hu = Vis.hiddenUuids(o);
            Set<String> hn = Vis.hiddenNames(o);
            if (killedByHidden(o) && !p.overlay()) {
                ServerPlayer dying = Ctx.DYING.get();
                return Vis.real(dying.getUUID(), o) ? List.of(new ClientboundSystemChatPacket(generic(dying), false)) : List.of();
            }
            if (refs(p.content(), hu, hn, 0)) {
                ServerPlayer dying = Ctx.DYING.get();
                if (dying != null && !p.overlay() && Vis.real(dying.getUUID(), o)) {
                    return List.of(new ClientboundSystemChatPacket(generic(dying), false));
                }
                return List.of();
            }
            ServerPlayer typist = Ctx.TYPIST.get();
            if (typist != null && !Vis.isDirector(typist) && !Vis.real(typist.getUUID(), o)) return List.of();
            return null;
        }
        if (packet instanceof net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket p) {
            Set<UUID> hu = Vis.hiddenUuids(o);
            Set<String> hn = Vis.hiddenNames(o);
            if (refs(p.message(), hu, hn, 0) || refs(p.chatType().name(), hu, hn, 0)
                || (p.chatType().targetName().isPresent() && refs(p.chatType().targetName().get(), hu, hn, 0))) return List.of();
            ServerPlayer typist = Ctx.TYPIST.get();
            if (typist != null && !Vis.isDirector(typist) && !Vis.real(typist.getUUID(), o)) return List.of();
            return null;
        }
        if (packet instanceof ClientboundPlayerCombatKillPacket p) {
            if (killedByHidden(o) || refs(p.message(), Vis.hiddenUuids(o), Vis.hiddenNames(o), 0)) {
                return List.of(new ClientboundPlayerCombatKillPacket(p.playerId(), generic(to)));
            }
            return null;
        }
        if (packet instanceof ClientboundCommandSuggestionsPacket p) {
            Set<String> hn = Vis.hiddenNames(o);
            List<ClientboundCommandSuggestionsPacket.Entry> keep = new ArrayList<>();
            for (var e : p.suggestions()) {
                String t = e.text().toLowerCase(Locale.ROOT);
                if (hn.contains(t)) continue;
                if (!barSuggestionVisible(e.text(), o)) continue;
                keep.add(e);
            }
            if (keep.size() == p.suggestions().size()) return null;
            return List.of(new ClientboundCommandSuggestionsPacket(p.id(), p.start(), p.length(), keep));
        }
        if (packet instanceof ClientboundServerDataPacket p) {
            Component motd = Groups.motd(Vis.revealed() ? null : Vis.group(o));
            if (motd == null) return null;
            return List.of(new ClientboundServerDataPacket(motd, p.iconBytes()));
        }
        if (packet instanceof ClientboundCustomPayloadPacket p) {
            return VoiceWire.rewritePayload(p, to);
        }
        return null;
    }

    /** Bossbar title with names of people the receiver may not know taken out (one title is sent to everybody). */
    private static List<Packet<?>> bossName(ClientboundBossEventPacket p, UUID o) {
        Object op = p.operation;
        if (op instanceof ClientboundBossEventPacket.AddOperation a) {
            Component n = Strip.strip(a.name, Vis.hiddenUuids(o), Vis.hiddenNames(o));
            if (n == null) return null;
            net.minecraft.world.BossEvent b = new net.minecraft.world.BossEvent(p.id, n, a.color, a.overlay) {};
            b.setProgress(a.progress);
            b.setDarkenScreen(a.darkenScreen);
            b.setPlayBossMusic(a.playMusic);
            b.setCreateWorldFog(a.createWorldFog);
            return List.of(ClientboundBossEventPacket.createAddPacket(b));
        }
        if (op instanceof ClientboundBossEventPacket.UpdateNameOperation u) {
            Component n = Strip.strip(u.name(), Vis.hiddenUuids(o), Vis.hiddenNames(o));
            if (n == null) return null;
            net.minecraft.world.BossEvent b = new net.minecraft.world.BossEvent(p.id, n,
                net.minecraft.world.BossEvent.BossBarColor.WHITE, net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS) {};
            return List.of(ClientboundBossEventPacket.createUpdateNamePacket(b));
        }
        return null;
    }

    private static boolean barSuggestionVisible(String text, UUID o) {
        if (Cfg.state.owners.isEmpty()) return true;
        String id = text.contains(":") ? text : "minecraft:" + text;
        return Owners.barIdVisible(id, o);
    }

    /** H09: dying right now, killed by someone (or their wolf / arrow) the receiver may not know. */
    private static boolean killedByHidden(UUID o) {
        UUID owner = Ctx.KILLER_OWNER.get();
        return Ctx.DYING.get() != null && owner != null && Vis.isKnownPlayer(owner) && !Vis.real(owner, o);
    }

    public static Component generic(ServerPlayer victim) {
        return Component.translatable("death.attack.generic", victim.getDisplayName());
    }

    private static List<Packet<?>> info(ClientboundPlayerInfoUpdatePacket p, ServerPlayer to) {
        UUID o = to.getUUID();
        boolean director = Vis.isDirector(o);
        EnumSet<ClientboundPlayerInfoUpdatePacket.Action> acts = EnumSet.copyOf(p.actions());
        List<ClientboundPlayerInfoUpdatePacket.Entry> out = new ArrayList<>();
        boolean changed = false;
        for (var e : p.entries()) {
            UUID id = e.profileId();
            if (!Vis.isKnownPlayer(id)) { out.add(e); continue; }
            switch (Vis.see(id, o)) {
                case REAL -> {
                    if (director && !id.equals(o)) {
                        changed = true;
                        acts.add(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LIST_ORDER);
                        int order = 100 - Vis.groupIndex(Vis.group(id));
                        out.add(new ClientboundPlayerInfoUpdatePacket.Entry(id, e.profile(), e.listed(), e.latency(), e.gameMode(),
                            e.displayName(), e.showHat(), order, e.chatSession()));
                    } else {
                        out.add(e);
                    }
                }
                case HIDDEN -> changed = true;
                case ALIAS -> {
                    changed = true;
                    UUID fake = Alias.fakeFor(id, o);
                    GameProfile prof = e.profile() == null ? null : new GameProfile(fake, Alias.NAME, e.profile().properties());
                    out.add(new ClientboundPlayerInfoUpdatePacket.Entry(fake, prof, false, e.latency(), e.gameMode(), null, e.showHat(), 0, null));
                }
            }
        }
        if (!changed) return null;
        if (out.isEmpty()) return List.of();
        ClientboundPlayerInfoUpdatePacket np = new ClientboundPlayerInfoUpdatePacket(acts, List.of());
        np.entries = out;
        return List.of(np);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<Packet<?>> entityData(ClientboundSetEntityDataPacket p, UUID o) {
        List<SynchedEntityData.DataValue<?>> out = null;
        List<SynchedEntityData.DataValue<?>> items = p.packedItems();
        for (int i = 0; i < items.size(); i++) {
            SynchedEntityData.DataValue<?> dv = items.get(i);
            Object v = dv.value();
            Object nv = null;
            if (v instanceof Optional<?> opt && opt.isPresent() && opt.get() instanceof EntityReference<?> ref) {
                UUID id = ref.getUUID();
                if (Vis.isKnownPlayer(id) && !Vis.real(id, o)) nv = Optional.of(EntityReference.of(Alias.fakeFor(id, o)));
            } else if (v instanceof EntityReference<?> ref) {
                UUID id = ref.getUUID();
                if (Vis.isKnownPlayer(id) && !Vis.real(id, o)) nv = EntityReference.of(Alias.fakeFor(id, o));
            } else if (v instanceof Optional<?> opt && opt.isPresent() && opt.get() instanceof UUID id) {
                if (Vis.isKnownPlayer(id) && !Vis.real(id, o)) nv = Optional.of(Alias.fakeFor(id, o));
            }
            if (nv != null) {
                if (out == null) out = new ArrayList<>(items);
                out.set(i, new SynchedEntityData.DataValue(dv.id(), dv.serializer(), nv));
            }
        }
        if (out == null) return null;
        return List.of(new ClientboundSetEntityDataPacket(p.id(), out));
    }

    /** Does this text point at a player the receiver must not know (hover uuid, insertion, or the bare name)? */
    public static boolean refs(Component c, Set<UUID> uuids, Set<String> names, int depth) {
        if (c == null || depth > 32) return false;
        if (uuids.isEmpty() && names.isEmpty()) return false;
        Style st = c.getStyle();
        if (st != null) {
            if (st.getHoverEvent() instanceof HoverEvent.ShowEntity se && uuids.contains(se.entity().uuid)) return true;
            if (st.getInsertion() != null && names.contains(st.getInsertion().toLowerCase(Locale.ROOT))) return true;
            if (st.getClickEvent() instanceof ClickEvent.SuggestCommand sc) {
                String cmd = sc.command().toLowerCase(Locale.ROOT).trim();
                for (String n : names) if (cmd.equals("/tell " + n) || cmd.equals("/msg " + n)) return true;
            }
        }
        var contents = c.getContents();
        if (contents instanceof PlainTextContents t && names.contains(t.text().toLowerCase(Locale.ROOT))) return true;
        if (contents instanceof TranslatableContents tc) {
            for (Object a : tc.getArgs()) {
                if (a instanceof Component ac) {
                    if (refs(ac, uuids, names, depth + 1)) return true;
                } else if (a instanceof String s && names.contains(s.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        for (Component sib : c.getSiblings()) if (refs(sib, uuids, names, depth + 1)) return true;
        return false;
    }
}
