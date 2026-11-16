package net.pvsync;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 03 [C]. Exists only for directors and the server console (H24). Never answers on screen: success and failure
 * both go to the server log only (H25). One greedy argument so Brigadier itself never prints a red error either.
 */
public final class Cmd {
    private Cmd() {}

    public static final String NAME = "pv";

    public static boolean allowed(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return src.getEntity() == null;
        return Vis.isDirector(p);
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal(NAME)
            .requires(Cmd::allowed)
            .executes(c -> 1)
            .then(Commands.argument("a", StringArgumentType.greedyString())
                .suggests(Cmd::suggest)
                .executes(c -> {
                    try {
                        run(c.getSource(), StringArgumentType.getString(c, "a"));
                    } catch (Throwable t) {
                        Log.warn("command failed: " + t);
                    }
                    return 1;
                })));
    }

    private static final List<String> SUBS = List.of("group", "director", "reveal", "back", "spawn", "view", "status", "reload");

    private static CompletableFuture<Suggestions> suggest(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, SuggestionsBuilder b) {
        String in = b.getRemaining();
        String[] parts = in.split(" ", -1);
        List<String> opts = new ArrayList<>();
        MinecraftServer s = c.getSource().getServer();
        if (parts.length <= 1) {
            opts.addAll(SUBS);
        } else {
            String sub = parts[0].toLowerCase(Locale.ROOT);
            if (parts.length == 2 && (sub.equals("group") || sub.equals("director"))) {
                for (ServerPlayer p : s.getPlayerList().getPlayers()) opts.add(p.getGameProfile().name());
            } else if (parts.length == 2 && (sub.equals("spawn") || sub.equals("view"))) {
                opts.addAll(Cfg.settings.groups.keySet());
            } else if (parts.length == 3 && sub.equals("group")) {
                opts.addAll(Cfg.settings.groups.keySet());
                opts.add("none");
            } else if (parts.length == 3 && sub.equals("director")) {
                opts.add("on");
                opts.add("off");
            }
        }
        String last = parts[parts.length - 1].toLowerCase(Locale.ROOT);
        SuggestionsBuilder sb = b.createOffset(b.getStart() + in.length() - parts[parts.length - 1].length());
        for (String o : opts) if (o.toLowerCase(Locale.ROOT).startsWith(last)) sb.suggest(o);
        return sb.buildFuture();
    }

    static void run(CommandSourceStack src, String args) {
        String[] a = args.trim().split("\\s+");
        String who = src.getTextName();
        if (a.length == 0 || a[0].isEmpty()) return;
        MinecraftServer s = src.getServer();
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "group" -> {
                if (a.length != 3) { fail(who, args, "usage: group <player> <group|none>"); return; }
                Target t = target(s, a[1]);
                if (t == null) { fail(who, args, "unknown player " + a[1]); return; }
                String g = a[2];
                if (!g.equalsIgnoreCase("none")) {
                    g = groupName(g);
                    if (g == null) { fail(who, args, "unknown group " + a[2]); return; }
                } else {
                    g = null;
                }
                setGroup(t, g);
                ok(who, args);
            }
            case "director" -> {
                if (a.length != 3 || !(a[2].equalsIgnoreCase("on") || a[2].equalsIgnoreCase("off"))) { fail(who, args, "usage: director <player> on|off"); return; }
                Target t = target(s, a[1]);
                if (t == null) { fail(who, args, "unknown player " + a[1]); return; }
                boolean on = a[2].equalsIgnoreCase("on");
                Resync.Snapshot snap = Resync.snapshot();
                Cfg.settings.directors.removeIf(x -> Vis.matches(x, t.id, t.name));
                if (on) Cfg.settings.directors.add(t.id != null ? t.id.toString() : t.name);
                Cfg.saveSettings();
                if (t.id != null) Vis.setDirector(t.id, on);
                Resync.apply(snap);
                ok(who, args);
            }
            case "reveal" -> {
                if (Vis.revealed()) { fail(who, args, "already revealed"); return; }
                Resync.Snapshot snap = Resync.snapshot();
                Cfg.settings.revealed = true;
                Cfg.saveSettings();
                Resync.apply(snap);
                ok(who, args);
            }
            case "back" -> {
                if (!Vis.revealed()) { fail(who, args, "not revealed"); return; }
                Resync.Snapshot snap = Resync.snapshot();
                Cfg.settings.revealed = false;
                Cfg.saveSettings();
                Resync.apply(snap);
                ok(who, args);
            }
            case "spawn" -> {
                if (a.length != 2) { fail(who, args, "usage: spawn <group>"); return; }
                String g = groupName(a[1]);
                if (g == null) { fail(who, args, "unknown group " + a[1]); return; }
                ServerLevel level = src.getLevel();
                var pos = src.getPosition();
                if (!Groups.solidUnder(level, pos.x, pos.y, pos.z)) { fail(who, args, "no footing under " + pos + " (air or liquid)"); return; }
                Cfg.Spawn sp = new Cfg.Spawn();
                sp.dim = level.dimension().identifier().toString();
                sp.x = pos.x; sp.y = pos.y; sp.z = pos.z;
                sp.yaw = src.getRotation().y; sp.pitch = src.getRotation().x;
                Cfg.settings.groups.get(g).spawn = sp;
                Cfg.saveSettings();
                ok(who, args + " -> " + sp.dim + " " + sp.x + " " + sp.y + " " + sp.z);
            }
            case "view" -> {
                if (a.length != 2) { fail(who, args, "usage: view <group>"); return; }
                String g = groupName(a[1]);
                if (g == null) { fail(who, args, "unknown group " + a[1]); return; }
                Resync.Snapshot snap = Resync.snapshot();
                Cfg.settings.directorView = g;
                Cfg.saveSettings();
                Resync.apply(snap);
                ok(who, args);
            }
            case "status" -> {
                Log.info("status: revealed=" + Vis.revealed() + " directorView=" + Cfg.settings.directorView + " revealTimeFrom=" + Cfg.settings.revealTimeFrom);
                for (ServerPlayer p : s.getPlayerList().getPlayers()) {
                    String g = Vis.group(p.getUUID());
                    Log.info("status: " + p.getGameProfile().name() + " group=" + (g == null ? "none" : g) + (Vis.isDirector(p) ? " director" : ""));
                }
                for (var en : Cfg.settings.groups.entrySet()) {
                    Log.info("status: group " + en.getKey() + " hosts=" + en.getValue().hosts + " members=" + en.getValue().members
                        + " spawn=" + (en.getValue().spawn == null ? "world" : en.getValue().spawn.dim + " " + en.getValue().spawn.x + " " + en.getValue().spawn.y + " " + en.getValue().spawn.z)
                        + " time=" + Math.floorMod(Sky.dayTime(en.getKey()), 24000L));
                }
            }
            case "reload" -> {
                Resync.Snapshot snap = Resync.snapshot();
                Cfg.load();
                for (ServerPlayer p : s.getPlayerList().getPlayers()) {
                    // groups come from the table again; the host part is kept from the current state
                    String old = Vis.group(p.getUUID());
                    String g = resolveKeepHost(p, old);
                    Vis.setGroup(p.getUUID(), p.getGameProfile().name(), g);
                    Vis.setDirector(p.getUUID(), Vis.configDirector(p.getUUID(), p.getGameProfile().name()));
                }
                Resync.apply(snap);
                ok(who, args);
            }
            default -> fail(who, args, "unknown sub command");
        }
    }

    private static String resolveKeepHost(ServerPlayer p, String current) {
        UUID id = p.getUUID();
        String name = p.getGameProfile().name();
        for (var en : Cfg.settings.groups.entrySet()) for (String m : en.getValue().members) if (Vis.matches(m, id, name)) return en.getKey();
        for (String m : Cfg.settings.noGroup) if (Vis.matches(m, id, name)) return null;
        String host = PvMod.hostOf(id);
        String byHost = Vis.groupForHost(host);
        return byHost != null ? byHost : (host == null ? current : null);
    }

    record Target(UUID id, String name) {}

    private static Target target(MinecraftServer s, String in) {
        ServerPlayer p = s.getPlayerList().getPlayerByName(in);
        if (p != null) return new Target(p.getUUID(), p.getGameProfile().name());
        try {
            UUID id = UUID.fromString(in);
            ServerPlayer q = s.getPlayerList().getPlayer(id);
            if (q != null) return new Target(id, q.getGameProfile().name());
            Cfg.Known k = Cfg.state.known.get(id.toString());
            return new Target(id, k == null ? null : k.name);
        } catch (IllegalArgumentException ignored) {
        }
        for (var en : Cfg.state.known.entrySet()) {
            if (en.getValue().name != null && en.getValue().name.equalsIgnoreCase(in)) {
                return new Target(UUID.fromString(en.getKey()), en.getValue().name);
            }
        }
        // someone who has not joined yet: written in the table by name, used when they join
        if (in.matches("[A-Za-z0-9_]{3,16}")) return new Target(null, in);
        return null;
    }

    private static String groupName(String in) {
        for (String g : Cfg.settings.groups.keySet()) if (g.equalsIgnoreCase(in)) return g;
        return null;
    }

    static void setGroup(Target t, String g) {
        Resync.Snapshot snap = Resync.snapshot();
        for (Cfg.Group cg : Cfg.settings.groups.values()) cg.members.removeIf(x -> Vis.matches(x, t.id, t.name));
        Cfg.settings.noGroup.removeIf(x -> Vis.matches(x, t.id, t.name));
        String key = t.id != null ? t.id.toString() : t.name;
        if (g == null) Cfg.settings.noGroup.add(key);
        else Cfg.settings.groups.get(g).members.add(key);
        Cfg.saveSettings();
        if (t.id != null) Vis.setGroup(t.id, t.name, g);
        Resync.apply(snap);
        ServerPlayer p = Vis.server == null || t.id == null ? null : Vis.server.getPlayerList().getPlayer(t.id);
        if (p != null) Groups.sendTabTexts(p);
    }

    private static void ok(String who, String args) {
        Log.info(who + ": /" + NAME + " " + args + " -> ok");
    }

    private static void fail(String who, String args, String why) {
        Log.warn(who + ": /" + NAME + " " + args + " -> not done: " + why);
    }
}
