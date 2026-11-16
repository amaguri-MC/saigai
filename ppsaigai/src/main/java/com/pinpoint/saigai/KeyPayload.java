package com.pinpoint.saigai;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** キーを押す人のクライアント → サーバー：キーが押された（1=雷・2=暴風・3=吹雪） */
public record KeyPayload(int kind) implements CustomPacketPayload {
    public static final int THUNDER = 1, WIND = 2, BLIZZARD = 3;
    public static final Type<KeyPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Saigai.MODID, "key"));
    public static final StreamCodec<FriendlyByteBuf, KeyPayload> CODEC = CustomPacketPayload.codec(
            (p, b) -> b.writeVarInt(p.kind), b -> new KeyPayload(b.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
