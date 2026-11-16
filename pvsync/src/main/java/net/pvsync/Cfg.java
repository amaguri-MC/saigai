package net.pvsync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** settings.json (03 [G]) and state.json (things that must survive a restart). */
public final class Cfg {
    private Cfg() {}

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static final class Spawn {
        public String dim = "minecraft:overworld";
        public double x, y, z;
        public float yaw, pitch;
    }

    public static final class Group {
        public List<String> hosts = new ArrayList<>();
        public List<String> members = new ArrayList<>();
        public Spawn spawn = null;
        public String motd = null;
        public int maxPlayers = 20;
        public String tabHeader = null;
        public String tabFooter = null;
    }

    public static final class Settings {
        public Map<String, Group> groups = new LinkedHashMap<>();
        public List<String> noGroup = new ArrayList<>();
        public List<String> directors = new ArrayList<>();
        public boolean revealed = false;
        public String revealTimeFrom = "P";
        public String directorView = "P";
    }

    public static final class Weather {
        public int clearTime, rainTime, thunderTime;
        public boolean raining, thundering;
        public float rainLevel, thunderLevel;
    }

    public static final class Known {
        public String name;
        public String group; // null = no group
        public Known() {}
        public Known(String name, String group) { this.name = name; this.group = group; }
    }

    public static final class State {
        public Map<String, Long> timeOffset = new java.util.concurrent.ConcurrentHashMap<>();
        public Map<String, Weather> weather = new java.util.concurrent.ConcurrentHashMap<>();
        /** uuid -> last known name / group (to keep offline players out of lists too). */
        public Map<String, Known> known = new java.util.concurrent.ConcurrentHashMap<>();
        /** "team:<name>" / "obj:<name>" / "bar:<id>" -> group that made it. */
        public Map<String, String> owners = new java.util.concurrent.ConcurrentHashMap<>();
        public String realWeatherSaved = null;
    }

    public static Settings settings = defaults();
    public static State state = new State();

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("pvsync");
    }

    static Settings defaults() {
        Settings s = new Settings();
        Group p = new Group();
        p.hosts.add("p.example.net");
        Group q = new Group();
        q.hosts.add("s.example.net");
        s.groups.put("P", p);
        s.groups.put("S", q);
        return s;
    }

    public static synchronized void load() {
        try {
            Files.createDirectories(dir());
            Path f = dir().resolve("settings.json");
            if (Files.exists(f)) {
                Settings s = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Settings.class);
                if (s == null) s = defaults();
                if (s.groups == null) s.groups = new LinkedHashMap<>();
                if (s.noGroup == null) s.noGroup = new ArrayList<>();
                if (s.directors == null) s.directors = new ArrayList<>();
                for (Group g : s.groups.values()) {
                    g.hosts = new java.util.concurrent.CopyOnWriteArrayList<>(g.hosts == null ? List.of() : g.hosts);
                    g.members = new java.util.concurrent.CopyOnWriteArrayList<>(g.members == null ? List.of() : g.members);
                }
                s.noGroup = new java.util.concurrent.CopyOnWriteArrayList<>(s.noGroup);
                s.directors = new java.util.concurrent.CopyOnWriteArrayList<>(s.directors);
                settings = s;
            } else {
                settings = defaults();
                saveSettings();
            }
            Path st = dir().resolve("state.json");
            if (Files.exists(st)) {
                State s = GSON.fromJson(Files.readString(st, StandardCharsets.UTF_8), State.class);
                state = s == null ? new State() : s;
                // read from several threads (server thread, network threads)
                state.timeOffset = new java.util.concurrent.ConcurrentHashMap<>(state.timeOffset == null ? Map.of() : state.timeOffset);
                state.weather = new java.util.concurrent.ConcurrentHashMap<>(state.weather == null ? Map.of() : state.weather);
                state.known = new java.util.concurrent.ConcurrentHashMap<>(state.known == null ? Map.of() : state.known);
                state.owners = new java.util.concurrent.ConcurrentHashMap<>(state.owners == null ? Map.of() : state.owners);
            }
        } catch (Exception e) {
            Log.warn("settings could not be read: " + e);
        }
    }

    public static synchronized void saveSettings() {
        write(dir().resolve("settings.json"), GSON.toJson(settings));
    }

    public static synchronized void saveState() {
        write(dir().resolve("state.json"), GSON.toJson(state));
    }

    private static void write(Path f, String text) {
        try {
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.writeString(tmp, text, StandardCharsets.UTF_8);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Log.warn("could not write " + f.getFileName() + ": " + e);
        }
    }
}
