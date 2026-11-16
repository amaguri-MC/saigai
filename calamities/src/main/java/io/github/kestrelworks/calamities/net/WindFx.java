package io.github.kestrelworks.calamities.net;

import io.github.kestrelworks.calamities.Calamities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → player: a gale blows toward (dirX, dirZ) for {@code ticks} from world time {@code start}. 0 ticks = stop. */
public record WindFx(float dirX, float dirZ, float push, float endFactor, int ticks, int variant, long start) implements CustomPacketPayload {
    public static final Type<WindFx> TYPE = new Type<>(Calamities.id("gale"));
    public static final StreamCodec<FriendlyByteBuf, WindFx> CODEC = CustomPacketPayload.codec(
            (p, b) -> { b.writeFloat(p.dirX); b.writeFloat(p.dirZ); b.writeFloat(p.push); b.writeFloat(p.endFactor);
                        b.writeVarInt(p.ticks); b.writeVarInt(p.variant); b.writeLong(p.start); },
            b -> new WindFx(b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readVarInt(), b.readVarInt(), b.readLong()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
