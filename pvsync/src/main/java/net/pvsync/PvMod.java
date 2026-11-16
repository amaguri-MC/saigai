package net.pvsync;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PvMod implements ModInitializer {

    private static final Map<UUID, String> HOSTS = new ConcurrentHashMap<>();

    public static void rememberHost(UUID id, String host) {
        if (host != null) HOSTS.put(id, host);
    }

    public static String hostOf(UUID id) {
        return HOSTS.get(id);
    }

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((d, reg, env) -> Cmd.register(d));
        ServerLifecycleEvents.SERVER_STARTING.register(s -> {
            Vis.server = s;
            Cfg.load();
            Log.info("ready: groups=" + Cfg.settings.groups.keySet() + " revealed=" + Cfg.settings.revealed);
        });
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            // every group starts from the weather the world had; the real world is then held clear (H14)
            for (String g : Cfg.settings.groups.keySet()) Sky.weather(g);
            Cfg.saveState();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            Cfg.saveState();
            Cfg.saveSettings();
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (s.getTickCount() % 20 != 0) return;
            for (var p : s.getPlayerList().getPlayers()) Groups.refreshNamelessTeam(p);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> Vis.leave(h.player.getUUID()));
    }
}
