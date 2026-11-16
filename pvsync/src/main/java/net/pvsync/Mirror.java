package net.pvsync;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.stream.Collectors;

/** [M]: copies of private chat (/msg, /teammsg) for directors only. */
public final class Mirror {
    private Mirror() {}

    public static void toDirectors(CommandSourceStack src, Collection<ServerPlayer> to, PlayerChatMessage msg, String kind) {
        String targets = to.stream().map(p -> p.getGameProfile().name()).collect(Collectors.joining(","));
        Component line = Component.literal("[" + kind + " " + src.getTextName() + " > " + targets + "] ").withStyle(ChatFormatting.GRAY)
            .append(msg.decoratedContent());
        for (ServerPlayer d : Vis.online()) {
            if (!Vis.isDirector(d) || to.contains(d) || d == src.getPlayer()) continue;
            Ctx.RAW.set(true);
            try {
                d.sendSystemMessage(line);
            } finally {
                Ctx.RAW.set(false);
            }
        }
    }
}
