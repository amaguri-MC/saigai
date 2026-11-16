package net.pvsync.mixin;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.commands.MsgCommand;
import net.minecraft.server.level.ServerPlayer;
import net.pvsync.Mirror;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

/** H08 [M]: directors also read /msg of everybody. */
@Mixin(MsgCommand.class)
public abstract class MsgMirrorMixin {
    @Inject(method = "sendMessage", at = @At("HEAD"))
    private static void pv$mirror(CommandSourceStack src, Collection<ServerPlayer> to, PlayerChatMessage msg, CallbackInfo ci) {
        Mirror.toDirectors(src, to, msg, "msg");
    }
}
