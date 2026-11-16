package net.pvsync.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.pvsync.Heads;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Placed player heads updated after the chunk was sent (only while a packet is being written for a receiver). */
@Mixin(ClientboundBlockEntityDataPacket.class)
public abstract class SkullTagMixin {
    @ModifyReturnValue(method = "getTag", at = @At("RETURN"))
    private CompoundTag pv$tag(CompoundTag tag) {
        return Heads.skullTag(tag);
    }
}
