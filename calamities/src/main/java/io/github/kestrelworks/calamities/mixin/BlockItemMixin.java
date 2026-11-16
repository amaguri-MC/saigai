package io.github.kestrelworks.calamities.mixin;

import io.github.kestrelworks.calamities.Built;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Remembers every block a player places by hand (see {@link Built}). */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {

    @Inject(method = "placeBlock", at = @At("RETURN"))
    private void calamities$remember(BlockPlaceContext ctx, BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && ctx.getPlayer() != null && !ctx.getLevel().isClientSide())
            Built.placed(ctx.getLevel(), ctx.getClickedPos());
    }
}
