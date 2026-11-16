package net.pvsync.mixin;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.component.ResolvableProfile;
import net.pvsync.Heads;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player head items: the owner is written as the receiver's stand-in when the receiver may not know them. */
@Mixin(ResolvableProfile.class)
public abstract class ProfileCodecMixin {
    @Shadow @Final @Mutable public static StreamCodec<ByteBuf, ResolvableProfile> STREAM_CODEC;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void pv$wrap(CallbackInfo ci) {
        StreamCodec<ByteBuf, ResolvableProfile> orig = STREAM_CODEC;
        STREAM_CODEC = StreamCodec.of((buf, v) -> orig.encode(buf, Heads.forReceiver(v)), orig::decode);
    }
}
