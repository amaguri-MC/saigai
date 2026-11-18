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

    // ---- harshness: disasters hurt the targets (group S and directors; never other groups, spectators or creative)

    /** Strength of everything below at once: 0 = no damage and everything as it was before, 1 = default, 2 = much harsher. */
    public float harshness = 1.0f;
    /** false: damage never kills (it stops at half a heart; burning is put out at the last hearts). */
    public boolean harshLethal = true;

    /** Quake: damage per jolt at magnitude 1 (a jolt is 50..100 % of it, times magnitude), the weakest magnitude that hurts, average ticks between jolts. */
    public float quakeDamage = 4.0f;
    public float quakeDamageMinMag = 0.15f;
    public int quakeDamageInterval = 30;
    /** Quake: rocks that come down from ceilings and cliffs near a target (chance per tick at magnitude 1, radius in blocks, damage, most in the air at once). */
    public float quakeRockRate = 0.04f;
    public int quakeRockRadius = 5;
    public float quakeRockDamage = 5.0f;
    public int quakeRockMax = 24;
    /** Fissure grows by this much per 1.0 of harshness (length and depth). */
    public float fissureHarshGrow = 0.3f;

    /** Storm: average ticks between extra bolts landing a few blocks from each target out in the open (at harshness 1), and how near. */
    public int stormNearEvery = 60;
    public float stormNearMin = 2.5f, stormNearMax = 6.5f;
    public float stormNearFireChance = 0.3f;
    /** Storm: the direct-hit chance is multiplied by 1 + harshness * this. */
    public float stormHitBoost = 2.0f;
    /** Storm: damage of a direct hit (times 0.5 + 0.5 * harshness) and how long it sets the target on fire. */
    public float stormHitDamage = 5.0f;
    public int stormHitFireSeconds = 6;

    /** Gale: the push is multiplied by 1 + harshness * this. */
    public float windPushBoost = 0.6f;
    /** Gale: pushed along the wind faster than this (blocks per tick) into a wall: damage = min(max, 1 + (speed - this) * scale). */
    public float windSlamSpeed = 0.2f;
    public float windSlamScale = 30f;
    public float windSlamMax = 8f;
    /** Gale: out in the open, about every this many ticks (at harshness 1) something flying hits the target for this much. */
    public int windDebrisEvery = 120;
    public float windDebrisDamage = 2.0f;

    /** Blizzard: seconds out in the cold before it starts to hurt, damage and ticks between hurts (frost damage). */
    public int blizzardColdSeconds = 12;
    public float blizzardDamage = 2.0f;
    public int blizzardDamageEvery = 40;
    /** Blizzard: snow piles up to 2 + round(harshness * this) layers. */
    public float blizzardThick = 2f;

    /** Spirit: within this many blocks the target gets darkness and slowness; touching it hurts once (then it fades). */
    public float spiritAura = 9f;
    public float spiritTouchDamage = 5.0f;
    public float spiritTouchRange = 1.4f;
    /** Spirit: ticks after appearing before it starts to close in, and blocks per tick it closes in. */
    public int spiritCloseDelay = 60;
    public float spiritCloseRate = 0.012f;

    /** With a view-sync mod in the world: the group whose members disasters are about (empty = "S"). */
    public String targetGroup = "S";
    /** With a view-sync mod: others within this many blocks (same dimension) of a target see the storm sky and blizzard while it lasts. */
    public float shareRadius = 64f;
    /** With a view-sync mod: its directors get the disasters too, whatever their group (for trying things out). */
    public boolean directorsAreTargets = true;

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
