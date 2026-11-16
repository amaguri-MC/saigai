package io.github.kestrelworks.calamities.net;

import io.github.kestrelworks.calamities.Calamities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → player: the ground shakes (magnitude 0..1) for {@code ticks} from world time {@code start}. 0 ticks = stop. */
public record QuakeFx(float magnitude, int ticks, long start) implements CustomPacketPayload {
    public static final Type<QuakeFx> TYPE = new Type<>(Calamities.id("quake"));
    public static final StreamCodec<FriendlyByteBuf, QuakeFx> CODEC = CustomPacketPayload.codec(
            (p, b) -> { b.writeFloat(p.magnitude); b.writeVarInt(p.ticks); b.writeLong(p.start); },
            b -> new QuakeFx(b.readFloat(), b.readVarInt(), b.readLong()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
