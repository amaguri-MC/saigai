package net.pvsync.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.pvsync.Heads;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Placed player heads in a chunk. */
@Mixin(targets = "net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData$BlockEntityInfo")
public abstract class ChunkSkullMixin {
    @WrapOperation(method = "write", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/network/RegistryFriendlyByteBuf;writeNbt(Lnet/minecraft/nbt/Tag;)Lnet/minecraft/network/FriendlyByteBuf;"))
    private FriendlyByteBuf pv$tag(RegistryFriendlyByteBuf buf, Tag tag, Operation<FriendlyByteBuf> op) {
        return op.call(buf, tag instanceof CompoundTag c ? Heads.skullTag(c) : tag);
    }
}
