package net.pvsync;

import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Util;
import net.minecraft.world.item.component.ResolvableProfile;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/**
 * Player heads (items and placed heads) carry their owner's profile. When the packet is written for a receiver
 * who may not know that owner, the owner becomes the same stand-in that receiver sees for the body
 * (stand-in uuid, empty name; the skin stays, as for the body).
 */
public final class Heads {
    private Heads() {}

    /** The receiver of the packet being written on this (network) thread. */
    public static final ThreadLocal<UUID> ENCODING_FOR = new ThreadLocal<>();

    public static ResolvableProfile forReceiver(ResolvableProfile p) {
        UUID o = ENCODING_FOR.get();
        if (o == null || p == null) return p;
        try {
            GameProfile g = p.partialProfile();
            UUID real = owner(g.id(), g.name(), o);
            if (real == null) return p;
            return ResolvableProfile.createResolved(new GameProfile(Alias.fakeFor(real, o), Alias.NAME, g.properties()));
        } catch (Throwable t) {
            return p;
        }
    }

    /** Owner as a uuid when it is someone the receiver may not know; else null. */
    static UUID owner(UUID id, String name, UUID o) {
        if (id != null && !id.equals(Util.NIL_UUID) && Vis.isKnownPlayer(id)) return Vis.real(id, o) ? null : id;
        if (name == null || name.isEmpty() || !Vis.nameHidden(name, o)) return null;
        for (var en : Cfg.state.known.entrySet()) {
            if (en.getValue().name != null && en.getValue().name.equalsIgnoreCase(name)) return UUID.fromString(en.getKey());
        }
        // a name from the settings (a director who never joined): a stable stand-in key for that name
        return UUID.nameUUIDFromBytes(("pv-name:" + name.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
    }

    /** A placed head's update tag ("profile") made for this receiver. Same tag object when nothing changes. */
    public static CompoundTag skullTag(CompoundTag tag) {
        if (tag == null || ENCODING_FOR.get() == null || !tag.contains("profile")) return tag;
        try {
            Tag raw = tag.get("profile");
            var parsed = ResolvableProfile.CODEC.parse(NbtOps.INSTANCE, raw).result();
            if (parsed.isEmpty()) return tag;
            ResolvableProfile now = forReceiver(parsed.get());
            if (now == parsed.get()) return tag;
            var enc = ResolvableProfile.CODEC.encodeStart(NbtOps.INSTANCE, now).result();
            CompoundTag copy = tag.copy();
            if (enc.isPresent()) copy.put("profile", enc.get()); else copy.remove("profile");
            return copy;
        } catch (Throwable t) {
            CompoundTag copy = tag.copy();
            copy.remove("profile");
            return copy;
        }
    }
}
