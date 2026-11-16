package io.github.kestrelworks.calamities;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** config/naturalcalamities.json */
public class CalamityConfig {

    /** Overall strength of every disaster (1.0 = default). */
    public float intensity = 1.0f;
    /** Earthquakes may knock loose natural blocks (gravel, stone ceilings, leaves). */
    public boolean quakeLooseBlocks = true;
    /** Lightning may start a small fire where it lands. */
    public boolean stormFire = true;
    /** A lightning storm brings ~50 s of rain (sky turns grey) if it was clear. */
    public boolean stormRain = true;
    /** Push strength of a gale (blocks per tick, before intensity). */
    public float galePush = 0.075f;
    /** Gale push in the End is multiplied by this (keeps the main island survivable). */
    public float galeEndFactor = 0.35f;
    /** Seconds a restless spirit lingers. */
    public int spiritSeconds = 60;
    /** "player": the spirit wears the skin it was given, "pale": a faceless pale figure (until revealed step by step). */
    public String spiritLook = "pale";
    /** How many reveal steps it takes from pale to the full face. */
    public int revealSteps = 4;
    /** A strong earthquake tears a fissure open near a player (natural ground only). */
    public boolean quakeFissure = true;
    /** Weakest quake (0..1) that opens a fissure. */
    public float fissureMinMagnitude = 0.5f;
    /** Size of the fissure at full strength (blocks): length, width at the top, depth in the middle. */
    public float fissureLength = 80f, fissureWidth = 15f, fissureDepth = 26f;
    /** Height of each ledge in the walls (blocks): the walls go down in broad steps like a canyon. */
    public int fissureLedge = 4;
    /** How far from the player its middle lies (+0..8), and how close it may come to anyone. */
    public float fissureDistance = 17f, fissureKeepAway = 3.5f;
    /** Ticks it takes to tear open from the middle to the ends. */
    public int fissureOpenTicks = 40;
    /** Share of surface blocks thrown up into the air, and the most thrown per fissure. */
    public float fissureDebris = 0.16f;
    public int fissureDebrisMax = 70;
    /** Seconds one storm key press keeps the thunderstorm going (presses add up to 3x). */
    public int stormSeconds = 60;
    /** Average ticks between bolts around each player outdoors during a thunderstorm. */
    public int stormBoltEvery = 45;
    /** Chance that a storm bolt strikes a player who is out in the open (real lightning damage). */
    public float stormHitChance = 0.07f;
    /** Chance that a storm bolt starts a small fire. */
    public float stormFireChance = 0.1f;
    /** Storms and gales it takes for their voice to come one stage closer to the real one. */
    public int voicePressesPerStep = 2;
    /** Seconds one blizzard key press keeps the blizzard going (presses add up to 3x). */
    public int blizzardSeconds = 45;
    /** How far players out in the blizzard freeze (0..1 of the vanilla "fully frozen" point; below 1 = frost and slowness, no damage). */
    public float blizzardFreeze = 0.8f;
    /** true: players may freeze all the way (vanilla freezing damage). */
    public boolean blizzardHarm = false;
    /** Water near players freezes over, snow settles on the ground. */
    public boolean blizzardIce = true, blizzardSnow = true;

    /** World event feed (lets an external weather/event controller drive disasters). */
    public boolean feedEnabled = true;
    public String feedHost = "127.0.0.1";
    public int feedPort = 25590;
    public String feedToken = "";
    public boolean feedInSingleplayer = false;

    private static CalamityConfig current = new CalamityConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static CalamityConfig get() { return current; }

    public static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("naturalcalamities.json"); }

    public static void load() {
        Path f = file();
        try {
            if (Files.exists(f)) {
                CalamityConfig c = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), CalamityConfig.class);
                if (c != null) current = c;
            }
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(current), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Calamities.LOG.warn("could not read {}", f, e);
        }
    }
}
