package io.github.kestrelworks.calamities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * A ground fissure torn open by a strong earthquake: a long, jagged crack, wide and deep in the middle and
 * narrowing to nothing at both ends, with stepped V-shaped walls. It bursts open from the middle outwards
 * over about two seconds, throwing up dirt and stone. Only natural ground is torn (built blocks, water and
 * anything with contents stay), and it never opens within a few blocks of a player.
 */
final class Fissure {

    private record Cell(int x, int z, int depth, int at, boolean surfaceDebris) {}

    private final ServerLevel level;
    private final List<Cell> cells;
    private final Vec3 center, endA, endB;
    private final int span;
    private int t;
    private int thrown;
    private static final RandomSource RNG = RandomSource.create();

    private Fissure(ServerLevel level, List<Cell> cells, Vec3 center, Vec3 endA, Vec3 endB, int span) {
        this.level = level;
        this.cells = cells;
        this.center = center;
        this.endA = endA;
        this.endB = endB;
        this.span = span;
    }

    ServerLevel level() { return level; }
    Vec3 center() { return center; }
    int size() { return cells.size(); }

    /** Plans a fissure near {@code p} sized by the quake magnitude, or null if no safe spot was found. */
    static Fissure plan(ServerLevel level, ServerPlayer p, float mag, List<ServerPlayer> everyone) {
        CalamityConfig c = CalamityConfig.get();
        float m = Mth.clamp(mag, 0, 1);
        // harshness: a size longer and deeper (width as it was); harshness 0 = exactly as before
        double grow = 1.0 + Disasters.harsh() * c.fissureHarshGrow;
        double length = c.fissureLength * (0.55 + 0.45 * m) * grow;
        double halfW = c.fissureWidth * 0.5 * (0.55 + 0.45 * m);
        double depth = c.fissureDepth * (0.5 + 0.5 * m) * grow;
        for (int attempt = 0; attempt < 40; attempt++) {
            double len = attempt < 25 ? length : length * 0.65;
            double toward = RNG.nextDouble() * Math.PI * 2;
            double dist = c.fissureDistance + RNG.nextDouble() * 8;
            double cx = p.getX() + Math.cos(toward) * dist, cz = p.getZ() + Math.sin(toward) * dist;
            // the crack runs roughly across the line to the player (so it opens "in front of" them, not at them)
            double heading = toward + Math.PI / 2 + (RNG.nextDouble() - 0.5) * 0.9;
            // walk from the middle to both ends, the heading wandering a little (a jagged, natural line)
            int half = (int) Math.round(len / 2);
            List<double[]> pts = new ArrayList<>();   // {x, z, s}
            pts.add(new double[]{cx, cz, 0});
            for (int side = -1; side <= 1; side += 2) {
                double x = cx, z = cz, h = heading + (side < 0 ? Math.PI : 0);
                for (int i = 1; i <= half; i++) {
                    h += (RNG.nextDouble() - 0.5) * 0.35;
                    x += Math.cos(h);
                    z += Math.sin(h);
                    pts.add(new double[]{x, z, side * i});
                }
            }
            Map<Long, int[]> grid = new HashMap<>();   // key -> {x, z, depth, at}
            for (double[] pt : pts) {
                double u = Math.abs(pt[2]) / Math.max(1, half);           // 0 middle .. 1 end
                // stays wide and deep for most of its length and pinches off near the ends (a long canyon, not a bowl)
                double cu = Math.max(0, Math.cos(u * Math.PI / 2));
                double w = Math.max(0.55, halfW * Math.pow(cu, 0.3) * (0.88 + RNG.nextDouble() * 0.24));
                double d = Math.max(2, depth * Math.pow(cu, 0.45) * (0.88 + RNG.nextDouble() * 0.24));
                int at = (int) Math.round(u * c.fissureOpenTicks) + RNG.nextInt(4);
                int r = (int) Math.ceil(w) + 1;
                for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                    int gx = Mth.floor(pt[0]) + dx, gz = Mth.floor(pt[1]) + dz;
                    double rr = Math.hypot(gx + 0.5 - pt[0], gz + 0.5 - pt[1]) / w;
                    if (rr > 1) continue;
                    // V-shaped: deepest on the centre line, the walls step up towards the rim
                    int cd = (int) Math.round(d * (1 - Math.pow(rr, 2.6)));
                    if (cd < 1) cd = 1;
                    long key = BlockPos.asLong(gx, 0, gz);
                    int[] old = grid.get(key);
                    if (old == null) grid.put(key, new int[]{gx, gz, cd, at, (int) Math.round(d)});
                    else { old[2] = Math.max(old[2], cd); old[3] = Math.min(old[3], at); old[4] = Math.max(old[4], (int) Math.round(d)); }
                }
            }
            // broad but uneven ledges, like a torn cliff: the depth wobbles from place to place and the ledge
            // height changes along the wall, so the steps never line up into neat rings
            long seed = RNG.nextLong();
            int ledge = Math.max(1, c.fissureLedge);
            for (int[] g : grid.values()) {
                double wob = (noise(seed, g[0], g[1], 6.0) - 0.5) * 7 + (noise(seed + 1, g[0], g[1], 2.5) - 0.5) * 3;
                int step = Math.max(2, ledge - 1 + (int) Math.floor(noise(seed + 2, g[0], g[1], 9.0) * 3));   // 3..5
                int cd = (int) Math.round(g[2] + wob);
                cd = ((Math.max(1, cd) + step - 1) / step) * step;
                g[2] = Mth.clamp(cd, 1, g[4] + 2);
            }
            // never anywhere near a player
            double keep = c.fissureKeepAway;
            boolean ok = true;
            for (int[] g : grid.values()) {
                for (ServerPlayer o : everyone) {
                    if (o.level() != level) continue;
                    double ex = o.getX() - (g[0] + 0.5), ez = o.getZ() - (g[1] + 0.5);
                    if (ex * ex + ez * ez < keep * keep) { ok = false; break; }
                }
                if (!ok) break;
            }
            if (!ok) continue;
            // mostly over natural, dry ground?
            int good = 0;
            for (int[] g : grid.values()) if (groundTop(level, g[0], g[1]) != null) good++;
            if (good < grid.size() * 0.6) continue;
            List<Cell> cells = new ArrayList<>();
            for (int[] g : grid.values()) cells.add(new Cell(g[0], g[1], g[2], g[3], RNG.nextFloat() < c.fissureDebris));
            cells.sort((a, b) -> Integer.compare(a.at, b.at));
            double[] a = pts.get(half), b = pts.get(pts.size() - 1);   // the two ends
            int topY = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(cx, 0, cz)).getY();
            return new Fissure(level, cells, new Vec3(cx, topY, cz), new Vec3(a[0], topY, a[1]), new Vec3(b[0], topY, b[1]),
                    c.fissureOpenTicks + 4);
        }
        return null;
    }

    /** Smooth value noise in 0..1 with features about {@code scale} blocks across. */
    private static double noise(long seed, int x, int z, double scale) {
        double fx = x / scale, fz = z / scale;
        int ix = Mth.floor(fx), iz = Mth.floor(fz);
        double tx = fx - ix, tz = fz - iz;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        double a = lattice(seed, ix, iz), b = lattice(seed, ix + 1, iz);
        double c = lattice(seed, ix, iz + 1), d = lattice(seed, ix + 1, iz + 1);
        return Mth.lerp(tz, Mth.lerp(tx, a, b), Mth.lerp(tx, c, d));
    }

    private static double lattice(long seed, int x, int z) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** Natural ground: stone, dirt, sand, gravel, clay, ores, terracotta, snow. Nothing a player built or stored things in. */
    static boolean natural(BlockState s) {
        if (s.isAir() || s.hasBlockEntity()) return false;
        if (s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.TERRACOTTA)
                || s.is(BlockTags.SNOW) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.CALCITE) || s.is(Blocks.DRIPSTONE_BLOCK)
                || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.SMOOTH_BASALT) || s.is(Blocks.MAGMA_BLOCK)
                || s.is(Blocks.ICE) || s.is(Blocks.PACKED_ICE) || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.SNOW_BLOCK))
            return true;
        return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().endsWith("_ore");
    }

    /** Plants, trees and snow lying on the ground: they go down with the ground beneath them. */
    static boolean cover(BlockState s) {
        return s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || (s.canBeReplaced() && s.getFluidState().isEmpty() && !s.isAir())
                || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS);
    }

    /** The topmost natural ground block of a column (below plants and trees), or null for water, buildings and the like. */
    static BlockPos groundTop(ServerLevel level, int x, int z) {
        BlockPos top = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z)).below();
        for (int i = 0; i < 40 && top.getY() > level.getMinY(); i++) {
            BlockState s = level.getBlockState(top);
            if (!s.isAir() && Built.is(level, top)) return null;   // something a player put there
            if (s.isAir() || cover(s)) { top = top.below(); continue; }
            return natural(s) ? top : null;
        }
        return null;
    }

    /** One tick of tearing open. Returns false once finished. */
    boolean tick() {
        CalamityConfig c = CalamityConfig.get();
        if (t == 0) boom(center, 1.0f);
        if (t == c.fissureOpenTicks / 2) { boom(endA, 0.8f); boom(endB, 0.8f); }
        for (Cell cell : cells) {
            if (cell.at > t) break;
            if (cell.at < t) continue;
            open(cell);
        }
        t++;
        return t <= span;
    }

    private void boom(Vec3 at, float vol) {
        level.playSound(null, at.x, at.y + 1, at.z, Disasters.sound("quake.jolt"), SoundSource.BLOCKS, 2.6f * vol, 0.8f + RNG.nextFloat() * 0.08f);
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.6, at.z, 2, 0.8, 0.3, 0.8, 0);
    }

    private void open(Cell cell) {
        int x = cell.x, z = cell.z;
        // someone walked onto the line while it was opening: the ground under them stays (never drop anyone in)
        double keep = CalamityConfig.get().fissureKeepAway;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            double ex = p.getX() - (x + 0.5), ez = p.getZ() - (z + 0.5);
            if (ex * ex + ez * ez < keep * keep) return;
        }
        BlockPos top = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z)).below();
        // plants and trees standing on it go first
        while (top.getY() > level.getMinY()) {
            BlockState s = level.getBlockState(top);
            if (s.isAir()) { top = top.below(); continue; }
            if (Built.is(level, top)) return;   // a player built this (even of logs or dirt): it and the ground under it stay
            if (!cover(s)) break;
            level.setBlock(top, Blocks.AIR.defaultBlockState(), 2);
            top = top.below();
        }
        BlockState surface = level.getBlockState(top);
        if (!natural(surface)) return;
        int floor = level.getMinY() + 6;
        int removed = 0;
        for (int k = 0; k < cell.depth; k++) {
            BlockPos q = top.below(k);
            if (q.getY() <= floor) break;
            BlockState s = level.getBlockState(q);
            if (!s.getFluidState().isEmpty()) break;                     // water or lava: leave the floor
            if (!level.getBlockState(q.below()).getFluidState().isEmpty()) break;   // never open into lava below
            if (s.isAir()) continue;                                      // an old cave in the way: keep going
            if (!natural(s) || Built.is(level, q)) break;
            if (k == 0 && cell.surfaceDebris && thrown < CalamityConfig.get().fissureDebrisMax) {
                // thrown up out of the ground
                FallingBlockEntity f = FallingBlockEntity.fall(level, q, s);
                f.dropItem = false;
                f.setDeltaMovement((RNG.nextDouble() - 0.5) * 0.6, 0.65 + RNG.nextDouble() * 0.6, (RNG.nextDouble() - 0.5) * 0.6);
                f.hurtMarked = true;
                thrown++;
            } else {
                level.setBlock(q, Blocks.AIR.defaultBlockState(), 3);
            }
            if (k < 2) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), x + 0.5, q.getY() + 0.7, z + 0.5,
                    6, 0.35, 0.35, 0.35, 0.15);
            removed++;
        }
        if (removed > 0 && RNG.nextFloat() < 0.18f)
            level.sendParticles(ParticleTypes.POOF, x + 0.5, top.getY() + 1.0, z + 0.5, 3, 0.4, 0.3, 0.4, 0.04);
    }
}
