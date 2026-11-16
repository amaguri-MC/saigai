package io.github.kestrelworks.calamities;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Every block a player placed by hand, so a fissure can leave what people built standing — even when it is made
 * of logs, dirt or stone, which look exactly like natural ground. Kept per dimension and saved with the world.
 */
public final class Built {

    private static final Map<String, LongOpenHashSet> PLACED = new HashMap<>();
    private static Path file;
    private static boolean dirty;
    private static int sinceSave;

    private Built() {}

    private static String dim(Level level) { return level.dimension().identifier().toString(); }

    public static synchronized void placed(Level level, BlockPos pos) {
        if (level.isClientSide() || file == null) return;
        if (PLACED.computeIfAbsent(dim(level), k -> new LongOpenHashSet()).add(pos.asLong())) dirty = true;
    }

    public static synchronized void broken(Level level, BlockPos pos) {
        LongOpenHashSet s = PLACED.get(dim(level));
        if (s != null && s.remove(pos.asLong())) dirty = true;
    }

    public static synchronized boolean is(Level level, BlockPos pos) {
        LongOpenHashSet s = PLACED.get(dim(level));
        return s != null && s.contains(pos.asLong());
    }

    static synchronized void load(MinecraftServer server) {
        PLACED.clear();
        dirty = false;
        file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("calamities_built.bin");
        if (!Files.exists(file)) return;
        try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
            int dims = in.readInt();
            for (int d = 0; d < dims; d++) {
                String key = in.readUTF();
                int n = in.readInt();
                LongOpenHashSet s = new LongOpenHashSet(n);
                for (int i = 0; i < n; i++) s.add(in.readLong());
                PLACED.put(key, s);
            }
        } catch (IOException e) {
            Calamities.LOG.warn("could not read placed blocks", e);
        }
    }

    static synchronized void save() {
        if (file == null || !dirty) return;
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(tmp))) {
                out.writeInt(PLACED.size());
                for (var e : PLACED.entrySet()) {
                    out.writeUTF(e.getKey());
                    out.writeInt(e.getValue().size());
                    for (long l : e.getValue()) out.writeLong(l);
                }
            }
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            dirty = false;
        } catch (IOException e) {
            Calamities.LOG.warn("could not save placed blocks", e);
        }
    }

    /** Saved every minute while running, and when the server stops. */
    static void tick() {
        if (++sinceSave >= 20 * 60) { sinceSave = 0; save(); }
    }

    static synchronized void close() {
        save();
        file = null;
        PLACED.clear();
    }
}
