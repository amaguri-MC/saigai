package net.pvsync;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.CreateGroupEvent;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.JoinGroupEvent;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.PacketEvent;
import de.maxhenkel.voicechat.api.events.RemoveGroupEvent;
import de.maxhenkel.voicechat.api.events.StaticSoundPacketEvent;
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

/**
 * H11 / H12 for Simple Voice Chat. Voices: the SVC server API (cancel per receiver). Player list in the voice
 * screen: SVC asks CommonCompatibilityManager.canSee (mixin) and the state packets are also checked here.
 * Voice groups: shown only to the group of the person who made them. Nothing here runs when SVC is absent.
 */
public final class VoiceCompat implements VoicechatPlugin {

    @Override
    public String getPluginId() {
        return "pvsync";
    }

    @Override
    public void registerEvents(EventRegistration reg) {
        reg.registerEvent(EntitySoundPacketEvent.class, VoiceCompat::sound, 100);
        reg.registerEvent(LocationalSoundPacketEvent.class, VoiceCompat::sound, 100);
        reg.registerEvent(StaticSoundPacketEvent.class, VoiceCompat::sound, 100);
        reg.registerEvent(CreateGroupEvent.class, e -> {
            VoicechatConnection c = e.getConnection();
            if (c == null || e.getGroup() == null) return;
            UUID maker = c.getPlayer().getUuid();
            if (Vis.isDirector(maker)) return;
            String g = Vis.group(maker);
            VoiceWire.GROUP_OWNER.put(e.getGroup().getId(), g == null ? "u:" + maker : g);
        }, 100);
        reg.registerEvent(RemoveGroupEvent.class, e -> {
            if (e.getGroup() != null) VoiceWire.GROUP_OWNER.remove(e.getGroup().getId());
        }, 100);
        reg.registerEvent(JoinGroupEvent.class, e -> {
            VoicechatConnection c = e.getConnection();
            if (c == null || e.getGroup() == null) return;
            if (!VoiceWire.groupVisible(e.getGroup().getId(), c.getPlayer().getUuid())) e.cancel();
        }, 100);
    }

    private static void sound(PacketEvent<?> e) {
        VoicechatConnection s = e.getSenderConnection();
        VoicechatConnection r = e.getReceiverConnection();
        if (s == null || r == null) return;
        UUID su = s.getPlayer().getUuid();
        UUID ru = r.getPlayer().getUuid();
        if (!Vis.real(su, ru)) e.cancel();
    }
}
