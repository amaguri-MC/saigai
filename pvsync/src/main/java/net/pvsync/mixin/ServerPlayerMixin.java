package net.pvsync.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.pvsync.Cfg;
import net.pvsync.Ctx;
import net.pvsync.Groups;
import net.pvsync.Vis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.UUID;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    /** H08: chat of a person this receiver may not know never reaches them (before the chat index is used). */
    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
    private void pv$chat(OutgoingChatMessage msg, boolean filtered, ChatType.Bound bound, CallbackInfo ci) {
        if (msg instanceof OutgoingChatMessage.Player p) {
            PlayerChatMessage m = p.message();
            UUID sender = m.link().sender();
            ServerPlayer self = (ServerPlayer) (Object) this;
            if (sender != null && !sender.equals(net.minecraft.util.Util.NIL_UUID) && !Vis.real(sender, self.getUUID())) ci.cancel();
        }
    }

    /** H09: while dying, death texts naming someone the receiver may not know become the nameless one. */
    @WrapMethod(method = "die")
    private void pv$die(DamageSource src, Operation<Void> op) {
        ServerPlayer prev = Ctx.DYING.get();
        java.util.UUID prevOwner = Ctx.KILLER_OWNER.get();
        Ctx.DYING.set((ServerPlayer) (Object) this);
        Ctx.KILLER_OWNER.set(ownerOf(src.getEntity()) != null ? ownerOf(src.getEntity()) : ownerOf(src.getDirectEntity()));
        try {
            op.call(src);
        } finally {
            if (prev == null) Ctx.DYING.remove(); else Ctx.DYING.set(prev);
            if (prevOwner == null) Ctx.KILLER_OWNER.remove(); else Ctx.KILLER_OWNER.set(prevOwner);
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private static java.util.UUID ownerOf(net.minecraft.world.entity.Entity e) {
        if (e instanceof ServerPlayer p) return p.getUUID();
        if (e instanceof net.minecraft.world.entity.OwnableEntity o && o.getOwnerReference() != null) return o.getOwnerReference().getUUID();
        if (e instanceof net.minecraft.world.entity.TraceableEntity t && t.getOwner() != null) return t.getOwner().getUUID();
        return null;
    }

    /** H19: no bed / anchor -> the spawn of the player's group. */
    @ModifyReturnValue(method = "findRespawnPositionAndUseSpawnBlock", at = @At("RETURN"))
    private TeleportTransition pv$respawn(TeleportTransition t) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (self.getRespawnConfig() != null && !t.missingRespawnBlock()) return t;
        Cfg.Spawn sp = Groups.spawnOf(self.getUUID());
        if (sp == null) return t;
        ServerLevel level = Groups.spawnLevel(self.level().getServer(), sp);
        if (level == null) return t;
        return new TeleportTransition(level, new Vec3(sp.x, sp.y, sp.z), Vec3.ZERO, sp.yaw, sp.pitch,
            t.missingRespawnBlock(), false, Set.of(), t.postTeleportTransition());
    }

    /** H13 / H14: whether it is night is the group's clock. */
    @WrapMethod(method = "startSleepInBed")
    private Either<Player.BedSleepingProblem, Unit> pv$sleep(BlockPos pos, Operation<Either<Player.BedSleepingProblem, Unit>> op) {
        ServerPlayer prev = Ctx.SLEEPER.get();
        Ctx.SLEEPER.set((ServerPlayer) (Object) this);
        try {
            return op.call(pos);
        } finally {
            if (prev == null) Ctx.SLEEPER.remove(); else Ctx.SLEEPER.set(prev);
        }
    }
}
