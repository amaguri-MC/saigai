package net.pvsync.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.pvsync.Ctx;
import net.pvsync.HostHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.net.SocketAddress;

@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class LoginMixin {
    @WrapOperation(method = "verifyLoginAndFinishConnectionSetup", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/players/PlayerList;canPlayerLogin(Ljava/net/SocketAddress;Lnet/minecraft/server/players/NameAndId;)Lnet/minecraft/network/chat/Component;"))
    private Component pv$login(PlayerList list, SocketAddress addr, NameAndId who, Operation<Component> op) {
        Ctx.LOGIN_HOST.set(((HostHolder) ((ServerLoginPacketListenerImpl) (Object) this).connection).pv$host());
        try {
            return op.call(list, addr, who);
        } finally {
            Ctx.LOGIN_HOST.remove();
        }
    }
}
