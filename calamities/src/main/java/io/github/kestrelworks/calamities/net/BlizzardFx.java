package io.github.kestrelworks.calamities.net;

import io.github.kestrelworks.calamities.Calamities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → player: a blizzard blows toward (dirX, dirZ) from world time {@code start} for {@code ticks}.
 * A longer blizzard (another press) is sent again with the same start and more ticks. 0 ticks = stop.
 */
public record BlizzardFx(float dirX, float dirZ, int ticks, long start) implements CustomPacketPayload {
    public static final Type<BlizzardFx> TYPE = new Type<>(Calamities.id("blizzard"));
    public static final StreamCodec<FriendlyByteBuf, BlizzardFx> CODEC = CustomPacketPayload.codec(
            (p, b) -> { b.writeFloat(p.dirX); b.writeFloat(p.dirZ); b.writeVarInt(p.ticks); b.writeLong(p.start); },
            b -> new BlizzardFx(b.readFloat(), b.readFloat(), b.readVarInt(), b.readLong()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
