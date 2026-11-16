package io.github.kestrelworks.calamities;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.kestrelworks.calamities.net.QuakeFx;
import io.github.kestrelworks.calamities.net.WindFx;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Natural Calamities — earthquakes, lightning storms, windstorms and restless spirits.
 */
public class Calamities implements ModInitializer {

    public static final String MODID = "calamities";
    public static final Logger LOG = LoggerFactory.getLogger("Natural Calamities");

    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MODID, path); }

    public static final EntityType<StormBolt> STORM_BOLT = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, id("storm_bolt"),
            EntityType.Builder.<StormBolt>of(StormBolt::new, MobCategory.MISC)
                    .noSave().sized(0.0f, 0.0f).clientTrackingRange(16).updateInterval(Integer.MAX_VALUE)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("storm_bolt"))));

    public static final EntityType<Wraith> WRAITH = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, id("wraith"),
            EntityType.Builder.<Wraith>of(Wraith::new, MobCategory.MISC)
                    .noSave().fireImmune().sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("wraith"))));

    public static final net.minecraft.core.particles.SimpleParticleType STREAK = Registry.register(
            BuiltInRegistries.PARTICLE_TYPE, id("streak"), net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.simple(true));

    public static final net.minecraft.core.particles.SimpleParticleType FLAKE = Registry.register(
            BuiltInRegistries.PARTICLE_TYPE, id("flake"), net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.simple(true));

    @Override
    public void onInitialize() {
        FabricDefaultAttributeRegistry.register(WRAITH, Wraith.createAttributes());
        PayloadTypeRegistry.playS2C().register(QuakeFx.TYPE, QuakeFx.CODEC);
        PayloadTypeRegistry.playS2C().register(WindFx.TYPE, WindFx.CODEC);
        PayloadTypeRegistry.playS2C().register(io.github.kestrelworks.calamities.net.ThunderVoice.TYPE, io.github.kestrelworks.calamities.net.ThunderVoice.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> Disasters.left(h.player.getUUID()));
        PayloadTypeRegistry.playS2C().register(io.github.kestrelworks.calamities.net.BlizzardFx.TYPE, io.github.kestrelworks.calamities.net.BlizzardFx.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((h, sender, server) -> {
            if (!isReplay(server)) server.execute(() -> Disasters.joined(h.player));
        });
        CommandRegistrationCallback.EVENT.register((d, reg, env) -> register(d));

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            CalamityConfig.load();
            Disasters.reset();
            if (!isReplay(server)) Built.load(server);   // 人が置いたブロック（地割れで削らない）
            if (!isReplay(server)) {   // コマンドの返事を一切出さない（撮影の決まり）
                var rules = server.overworld().getGameRules();
                rules.set(net.minecraft.world.level.gamerules.GameRules.SEND_COMMAND_FEEDBACK, false, server);
                rules.set(net.minecraft.world.level.gamerules.GameRules.COMMAND_BLOCK_OUTPUT, false, server);
                rules.set(net.minecraft.world.level.gamerules.GameRules.LOG_ADMIN_COMMANDS, false, server);
            }
            if (server.isDedicatedServer() || CalamityConfig.get().feedInSingleplayer) EventFeed.start(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> { EventFeed.stop(); Disasters.reset(); if (!isReplay(server)) Built.close(); });
        ServerTickEvents.END_SERVER_TICK.register(server -> { if (!isReplay(server)) { Disasters.tick(server); Built.tick(); } });
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> Built.broken(level, pos));
    }

    /** A world that is only playing back a recording (replay mods run one as a server): nothing may happen there. */
    static boolean isReplay(net.minecraft.server.MinecraftServer server) {
        return server.getClass().getName().toLowerCase(java.util.Locale.ROOT).contains("replay");
    }

    /** Operator commands (level 2). They never print anything; results go to the log only. */
    private static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("calamity").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("quake")
                        .executes(c -> run(c.getSource(), () -> Disasters.quake(c.getSource().getServer(), 0.5f, "command")))
                        .then(Commands.argument("magnitude", FloatArgumentType.floatArg(0, 1))
                                .executes(c -> run(c.getSource(), () -> Disasters.quake(c.getSource().getServer(),
                                        FloatArgumentType.getFloat(c, "magnitude"), "command")))))
                .then(Commands.literal("storm")
                        .executes(c -> run(c.getSource(), () -> Disasters.thunder(c.getSource().getServer(), "command"))))
                .then(Commands.literal("gale")
                        .executes(c -> run(c.getSource(), () -> Disasters.wind(c.getSource().getServer(), "command"))))
                .then(Commands.literal("blizzard")
                        .executes(c -> run(c.getSource(), () -> Disasters.blizzard(c.getSource().getServer(), "command"))))
                .then(Commands.literal("spirit")
                        .executes(c -> run(c.getSource(), () -> Disasters.ghost(c.getSource().getServer(), null, null, null, null)))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(c -> run(c.getSource(), () -> Disasters.ghost(c.getSource().getServer(), null,
                                        StringArgumentType.getString(c, "name"), null, null)))))
                .then(Commands.literal("calm")
                        .executes(c -> run(c.getSource(), () -> Disasters.calm(c.getSource().getServer()))))
                .then(Commands.literal("reveal")
                        .executes(c -> run(c.getSource(), () -> Disasters.reveal(1f, -1f)))
                        .then(Commands.argument("level", FloatArgumentType.floatArg(0, 1))
                                .executes(c -> run(c.getSource(), () -> Disasters.reveal(0f, FloatArgumentType.getFloat(c, "level"))))))
                .then(Commands.literal("voice")
                        .then(Commands.argument("stage", IntegerArgumentType.integer(0, Disasters.VOICE_STAGES - 1))
                                .executes(c -> run(c.getSource(), () -> Disasters.voice(IntegerArgumentType.getInteger(c, "stage"))))))
                .then(Commands.literal("reload")
                        .executes(c -> run(c.getSource(), CalamityConfig::load))));
    }

    private static int run(CommandSourceStack src, Runnable r) {
        try {
            r.run();
            return 1;
        } catch (Exception e) {
            LOG.warn("command failed", e);
            return 0;
        }
    }
}
