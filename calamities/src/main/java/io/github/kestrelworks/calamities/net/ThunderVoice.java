package io.github.kestrelworks.calamities.net;

import io.github.kestrelworks.calamities.Calamities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> target players only: the voice clip ({@code variant}) of the key-press bolt that lands at (x, y, z). */
public record ThunderVoice(double x, double y, double z, int variant) implements CustomPacketPayload {
    public static final Type<ThunderVoice> TYPE = new Type<>(Calamities.id("thunder_voice"));
    public static final StreamCodec<FriendlyByteBuf, ThunderVoice> CODEC = CustomPacketPayload.codec(
            (p, b) -> { b.writeDouble(p.x); b.writeDouble(p.y); b.writeDouble(p.z); b.writeVarInt(p.variant); },
            b -> new ThunderVoice(b.readDouble(), b.readDouble(), b.readDouble(), b.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
