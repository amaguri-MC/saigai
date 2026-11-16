package com.pinpoint.saigai;

import com.google.gson.JsonObject;
import com.mojang.authlib.properties.Property;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.level.gamerules.GameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 合図を送る側のサーバー MOD。
 * - sources の人が落下ダメージ → 相手のサーバーに地震（ダメージが大きいほど強い）
 * - sources の人が死ぬ → 相手のサーバーに亡霊（死んだ人のスキン）
 * - operators の人のキー → 雷／暴風／吹雪
 * - 入口（Router）：25565 に来た人を名前でどちらのサーバーへ流すか決める
 * 画面には何も出さない（結果はサーバーのログの (ppsaigai) 行だけ）。
 */
public class Saigai implements ModInitializer {

    public static final String MODID = "ppsaigai";
    public static final Logger LOG = LoggerFactory.getLogger("ppsaigai");
    private static Router router;

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(KeyPayload.TYPE, KeyPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(KeyPayload.TYPE, (p, ctx) -> onKey(ctx.player(), p.kind()));
        CommandRegistrationCallback.EVENT.register((d, reg, env) -> register(d));

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            SaigaiConfig.load();
            quiet(server);
            if (!server.isDedicatedServer() && !SaigaiConfig.get().inSingleplayer) return;
            Link.start();
            startRouter();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            Link.stop();
            if (router != null) { router.stop(); router = null; }
        });

        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (!(entity instanceof ServerPlayer p) || taken <= 0 || !isSource(p)) return;
            if (!source.is(DamageTypeTags.IS_FALL)) return;
            SaigaiConfig c = SaigaiConfig.get();
            float mag = Math.min(1f, c.quakeBase + taken * c.quakePerDamage);
            JsonObject j = new JsonObject();
            j.addProperty("t", "quake");
            j.addProperty("mag", mag);
            j.addProperty("src", p.getScoreboardName() + " fell " + String.format("%.1f", taken));
            Link.send(j);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer p && isSource(p)) ghost(p);
        });
    }

    static boolean isSource(ServerPlayer p) {
        return SaigaiConfig.get().sources.stream().anyMatch(n -> n.equalsIgnoreCase(p.getScoreboardName()));
    }

    static void onKey(ServerPlayer p, int kind) {
        if (SaigaiConfig.get().operators.stream().noneMatch(n -> n.equalsIgnoreCase(p.getScoreboardName()))) {
            LOG.info("(ppsaigai) {} のキーは受け付けない（押せる人ではない）", p.getScoreboardName());
            return;
        }
        JsonObject j = new JsonObject();
        j.addProperty("t", kind == KeyPayload.THUNDER ? "storm" : kind == KeyPayload.BLIZZARD ? "blizzard" : "gale");
        j.addProperty("src", p.getScoreboardName() + " key");
        Link.send(j);
    }

    static void ghost(ServerPlayer p) {
        JsonObject j = new JsonObject();
        j.addProperty("t", "spirit");
        j.addProperty("id", p.getUUID().toString());
        j.addProperty("name", p.getScoreboardName());
        for (Property prop : p.getGameProfile().properties().get("textures")) {
            j.addProperty("tex", prop.value());
            if (prop.signature() != null) j.addProperty("sig", prop.signature());
        }
        j.addProperty("src", p.getScoreboardName() + " died");
        Link.send(j);
    }

    private static void startRouter() {
        SaigaiConfig c = SaigaiConfig.get();
        if (!c.routerEnabled || router != null) return;
        router = new Router(new Router.Settings() {
            public String bind() { return c.routerBind; }
            public int port() { return c.routerPort; }
            public List<String> pinpoint() { return c.routerPinpoint; }
            public int pinpointPort() { return c.pinpointPort; }
            public int guestPort() { return c.guestPort; }
            public String motd() { return c.routerMotd; }
        }, s -> LOG.info("(ppsaigai) {}", s));
        try {
            router.start();
        } catch (Exception e) {
            LOG.warn("(ppsaigai) 入口を開けない（:{} を別の物が使っている？）", c.routerPort, e);
            router = null;
        }
    }

    /** コマンドの返事を一切出さない（撮影の決まり） */
    private static void quiet(MinecraftServer server) {
        GameRules r = server.overworld().getGameRules();
        r.set(GameRules.SEND_COMMAND_FEEDBACK, false, server);
        r.set(GameRules.COMMAND_BLOCK_OUTPUT, false, server);
        r.set(GameRules.LOG_ADMIN_COMMANDS, false, server);
    }

    /** /ppsaigai を使えるのはコンソールと testers の人だけ（テスト用。OP でも testers に無ければコマンド自体が見えない） */
    static boolean canUseCommand(CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer p)) return src.getEntity() == null;
        return SaigaiConfig.get().testers.stream().anyMatch(n -> n.equalsIgnoreCase(p.getScoreboardName()));
    }

    /** 予備のコマンド（返事は出さない・ログだけ） */
    private static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("ppsaigai").requires(Saigai::canUseCommand)
                .then(Commands.literal("thunder").executes(c -> { send("storm", "command"); return 1; }))
                .then(Commands.literal("wind").executes(c -> { send("gale", "command"); return 1; }))
                .then(Commands.literal("blizzard").executes(c -> { send("blizzard", "command"); return 1; }))
                .then(Commands.literal("quake")
                        .executes(c -> { quake(0.5f); return 1; })
                        .then(Commands.argument("強さ", FloatArgumentType.floatArg(0, 1))
                                .executes(c -> { quake(FloatArgumentType.getFloat(c, "強さ")); return 1; })))
                .then(Commands.literal("ghost")
                        .then(Commands.argument("人", EntityArgument.player())
                                .executes(c -> { ghost(EntityArgument.getPlayer(c, "人")); return 1; })))
                .then(Commands.literal("calm").executes(c -> { send("calm", "command"); return 1; }))
                // 亡霊を1段だけ本人の姿に近づける／0〜1 でじかに決める（0＝白い誰か分からない姿・1＝本人）
                .then(Commands.literal("reveal")
                        .executes(c -> { reveal("step", 1f); return 1; })
                        .then(Commands.argument("どこまで", FloatArgumentType.floatArg(0, 1))
                                .executes(c -> { reveal("to", FloatArgumentType.getFloat(c, "どこまで")); return 1; })))
                // 雷・暴風の声の段階をじかに決める（0＝最初の加工が強い声・3＝本人と分かる声）。押した回数で自動で進む
                .then(Commands.literal("voice")
                        .then(Commands.argument("段階", IntegerArgumentType.integer(0, 3))
                                .executes(c -> { voice(IntegerArgumentType.getInteger(c, "段階")); return 1; })))
                .then(Commands.literal("status").executes(c -> { LOG.info("(ppsaigai) {}", Link.status()); return 1; }))
                .then(Commands.literal("reload").executes(c -> { SaigaiConfig.load(); return 1; })));
    }

    private static void send(String t, String src) {
        JsonObject j = new JsonObject();
        j.addProperty("t", t);
        j.addProperty("src", src);
        Link.send(j);
    }

    private static void voice(int stage) {
        JsonObject j = new JsonObject();
        j.addProperty("t", "voice");
        j.addProperty("stage", stage);
        j.addProperty("src", "command");
        Link.send(j);
    }

    private static void reveal(String key, float v) {
        JsonObject j = new JsonObject();
        j.addProperty("t", "reveal");
        j.addProperty(key, v);
        j.addProperty("src", "command");
        Link.send(j);
    }

    private static void quake(float mag) {
        JsonObject j = new JsonObject();
        j.addProperty("t", "quake");
        j.addProperty("mag", mag);
        j.addProperty("src", "command");
        Link.send(j);
    }
}
