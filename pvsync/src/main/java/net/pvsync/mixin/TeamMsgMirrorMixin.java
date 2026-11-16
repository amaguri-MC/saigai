package net.pvsync.mixin;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.commands.TeamMsgCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;
import net.pvsync.Mirror;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** H08 [M]: directors also read /teammsg of everybody. */
@Mixin(TeamMsgCommand.class)
public abstract class TeamMsgMirrorMixin {
    @Inject(method = "sendMessage", at = @At("HEAD"))
    private static void pv$mirror(CommandSourceStack src, Entity e, PlayerTeam team, List<ServerPlayer> to, PlayerChatMessage msg, CallbackInfo ci) {
        Mirror.toDirectors(src, to, msg, "team " + team.getName());
    }
}
